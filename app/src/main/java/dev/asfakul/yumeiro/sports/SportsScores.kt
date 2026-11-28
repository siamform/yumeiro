/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sports

import android.content.Context
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * B15: live scores and fixtures for Live TV → Tools → Sports, from free sources that need no key.
 *
 *  - Primary: ESPN's public scoreboard feed (no key, live clock and scores, many leagues). It is not an official,
 *    documented API, so every field is treated as untrusted and the page says where the numbers come from.
 *  - Fallback: TheSportsDB's free public test key ("123", published in their docs; not a secret). Fewer matches,
 *    used only when ESPN cannot be reached, and the page says so.
 *
 * Rule from the owner: never invent scores. A score is shown only when the source sent one; a missing score stays
 * empty, an unknown status stays the source's own words, and on failure the page shows an error, never old numbers
 * dressed up as new.
 */
object SportsScores {
    /**
     * [espn]: ESPN scoreboard queries (several are merged; empty = ESPN has no free feed for it).
     * [sportsDb]: TheSportsDB sport name. [alsoSportsDb]: ESPN covers only part of this sport (volleyball: US
     * college only), so TheSportsDB's pro leagues are added next to it, not only used as a fallback.
     */
    enum class Sport(val id: String, val espn: List<String>, val sportsDb: String, val alsoSportsDb: Boolean = false) {
        FOOTBALL("football", listOf("sport=soccer"), "Soccer"),
        CRICKET("cricket", listOf("sport=cricket"), "Cricket"),
        BASKETBALL("basketball", listOf("sport=basketball&league=nba"), "Basketball"),
        VOLLEYBALL("volleyball", listOf("sport=volleyball&league=womens-college-volleyball", "sport=volleyball&league=mens-college-volleyball"), "Volleyball", alsoSportsDb = true),
        AMERICAN_FOOTBALL("american_football", listOf("sport=football&league=nfl"), "American Football"),
        HOCKEY("hockey", listOf("sport=hockey&league=nhl"), "Ice Hockey"),
        BASEBALL("baseball", listOf("sport=baseball&league=mlb"), "Baseball"),
        TENNIS("tennis", listOf("sport=tennis"), "Tennis"),
        RUGBY("rugby", listOf("sport=rugby"), "Rugby"),
        HANDBALL("handball", emptyList(), "Handball"),
    }

    enum class State { UPCOMING, LIVE, FINISHED, OFF }
    enum class Source(val label: String, val site: String) {
        ESPN("ESPN", "https://www.espn.com"),
        SPORTSDB("TheSportsDB", "https://www.thesportsdb.com"),
    }

    data class Side(val name: String, val short: String, val logo: String?, val score: String?, val winner: Boolean)
    data class Match(
        val id: String,
        val start: Instant?,
        val state: State,
        /** The source's own short status: "67'", "HT", "FT", "Stumps", "Q3 4:12". */
        val detail: String,
        /** One line from the source, e.g. "India need 120 runs" or "Postponed". */
        val note: String,
        val sides: List<Side>,
        val link: String?,
        /** ESPN "sport/league" path for the match page ([loadDetail]); null when the source has no detail feed. */
        val espnPath: String? = null,
    )
    data class League(val name: String, val matches: List<Match>)
    data class Board(
        val sport: Sport, val day: LocalDate, val leagues: List<League>, val sources: List<Source>, val fetchedAt: Long,
        /** TheSportsDB stood in because ESPN did not answer (fewer matches). */
        val fallback: Boolean = false,
        /** The source could not be reached just now; this is the last answer it gave, from [fetchedAt]. */
        val stale: Boolean = false,
    ) {
        constructor(sport: Sport, day: LocalDate, leagues: List<League>, source: Source, fetchedAt: Long) :
            this(sport, day, leagues, listOf(source), fetchedAt)
        val source: Source get() = sources.first()
        val liveCount: Int get() = leagues.sumOf { l -> l.matches.count { it.state == State.LIVE } }
        val empty: Boolean get() = leagues.isEmpty()
    }

    private val DAY = DateTimeFormatter.BASIC_ISO_DATE
    private const val MAX_TEXT = 80
    private const val MAX_MATCHES = 400

    fun espnUrl(sport: Sport, day: LocalDate, query: String = sport.espn.first()): String =
        "https://site.web.api.espn.com/apis/v2/scoreboard/header?$query&dates=${day.format(DAY)}&lang=en&region=us"

    fun sportsDbUrl(sport: Sport, day: LocalDate): String =
        "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$day&s=" + java.net.URLEncoder.encode(sport.sportsDb, "UTF-8").replace("+", "%20")

    // ---------- parsing (pure, unit-tested against saved real answers) ----------

    private fun clean(s: String?): String {
        if (s == null || s == "null") return ""
        val t = s.filter { !it.isISOControl() }.trim()
        return if (t.length > MAX_TEXT) t.take(MAX_TEXT - 1) + "…" else t
    }

    /** An ESPN slug goes into a URL path, so only plain [a-z0-9.-] slugs are kept. */
    private fun slug(s: String?): String? = clean(s).takeIf { it.isNotEmpty() && it.length <= 60 && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }

    /** Only https links to the source's own sites; anything else is dropped. */
    private fun safeLink(s: String?, hosts: List<String>): String? {
        val u = clean(s).takeIf { it.startsWith("https://") } ?: return null
        val host = u.removePrefix("https://").substringBefore('/').substringBefore(':').lowercase()
        return u.takeIf { hosts.any { h -> host == h || host.endsWith(".$h") } }
    }

    /** A score is a short run of digits with cricket marks ("246/5 (85 ov)", "1", "0 (4)"); anything else is not shown. */
    private fun score(s: String?): String? {
        val t = clean(s)
        if (t.isEmpty() || !t.any { it.isDigit() }) return null
        // Cricket chases read "93/3 (8.5/16 ov, target 93)": the word "target" and its comma are allowed too.
        val bare = t.replace("target", "").replace(",", "")
        return t.takeIf { it.length <= 40 && bare.all { c -> c.isDigit() || c in " /()&.-*+ovdfOVDF" } }
    }

    private fun instant(s: String?): Instant? = runCatching {
        val t = clean(s)
        when {
            t.isEmpty() -> null
            t.endsWith("Z") && t.length == 17 -> Instant.parse(t.dropLast(1) + ":00Z") // "2026-11-27T19:30Z"
            t.endsWith("Z") || t.contains('+') -> Instant.parse(t)
            else -> LocalDateTime.parse(t).toInstant(ZoneOffset.UTC) // TheSportsDB strTimestamp is UTC
        }
    }.getOrNull()

    fun parseEspn(json: String, sport: Sport, day: LocalDate, now: Long): Board {
        val root = JSONObject(json)
        val hosts = listOf("espn.com", "espn.in", "espn.co.uk", "espncricinfo.com", "espn.com.au")
        val leagues = ArrayList<League>()
        var total = 0
        val sports = root.optJSONArray("sports") ?: JSONArray()
        for (i in 0 until sports.length()) {
            val sportSlug = slug(sports.optJSONObject(i)?.optString("slug"))
            val ls = sports.optJSONObject(i)?.optJSONArray("leagues") ?: continue
            for (j in 0 until ls.length()) {
                val l = ls.optJSONObject(j) ?: continue
                val leagueSlug = slug(l.optString("slug"))
                val path = if (sportSlug != null && leagueSlug != null) "$sportSlug/$leagueSlug" else null
                val name = clean(l.optString("name")).ifEmpty { clean(l.optString("shortName")) }
                if (name.isEmpty()) continue
                val evs = l.optJSONArray("events") ?: continue
                val seen = HashSet<String>()
                val matches = ArrayList<Match>()
                for (k in 0 until evs.length()) {
                    if (total >= MAX_MATCHES) break
                    val e = evs.optJSONObject(k) ?: continue
                    val id = clean(e.optString("id"))
                    if (id.isEmpty()) continue
                    if (!seen.add(id)) continue
                    val fs = e.optJSONObject("fullStatus")
                    val type = fs?.optJSONObject("type")
                    val typeName = clean(type?.optString("name")).uppercase()
                    val raw = clean(e.optString("status")).ifEmpty { clean(type?.optString("state")) }
                    val state = when {
                        typeName.contains("POSTPONED") || typeName.contains("CANCEL") || typeName.contains("ABANDON") ||
                            typeName.contains("SUSPENDED") || typeName.contains("DELAYED") -> State.OFF
                        raw == "in" -> State.LIVE
                        raw == "post" -> State.FINISHED
                        raw == "pre" -> State.UPCOMING
                        else -> continue // unknown: better not shown than guessed
                    }
                    val detail = clean(type?.optString("shortDetail")).ifEmpty { clean(e.optString("summary")) }
                    val note = clean(fs?.optString("longSummary")).ifEmpty { clean(fs?.optString("summary")) }
                        .ifEmpty { if (state == State.OFF) clean(type?.optString("description")) else "" }
                    val cs = e.optJSONArray("competitors") ?: continue
                    val sides = ArrayList<Pair<String, Side>>()
                    for (c in 0 until cs.length()) {
                        val o = cs.optJSONObject(c) ?: continue
                        val n = clean(o.optString("displayName")).ifEmpty { clean(o.optString("name")) }
                        if (n.isEmpty()) continue
                        sides += clean(o.optString("homeAway")) to Side(
                            name = n,
                            short = clean(o.optString("abbreviation")).ifEmpty { n },
                            logo = safeLink(o.optString("logo"), listOf("espncdn.com")),
                            // Before kick-off ESPN may send "0" or ""; a score that has not happened is not shown.
                            score = if (state == State.UPCOMING) null else score(o.optString("score")),
                            winner = state == State.FINISHED && o.optBoolean("winner", false),
                        )
                    }
                    if (sides.size < 2) continue
                    // Home first, the way scores are read out everywhere ESPN is used; keep the source order otherwise.
                    val ordered = if (sides.any { it.first == "home" } && sides.any { it.first == "away" })
                        sides.sortedBy { if (it.first == "home") 0 else 1 } else sides
                    var link: String? = null
                    val links = e.optJSONArray("links")
                    if (links != null) for (q in 0 until links.length()) {
                        link = safeLink(links.optJSONObject(q)?.optString("href"), hosts); if (link != null) break
                    }
                    if (link == null) link = safeLink(e.optString("link"), hosts)
                    matches += Match(id, instant(e.optString("date")), state, detail, note, ordered.map { it.second }.take(2), link, path)
                    total++
                }
                if (matches.isNotEmpty()) leagues += League(name, sortMatches(matches))
            }
        }
        return Board(sport, day, sortLeagues(leagues), Source.ESPN, now)
    }

    private val SDB_LIVE = setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE", "IN PLAY", "Q1", "Q2", "Q3", "Q4", "OT", "INT")
    private val SDB_DONE = setOf("FT", "AET", "PEN", "AP", "MATCH FINISHED", "FINISHED", "AOT", "FT_PEN")
    private val SDB_OFF = setOf("PST", "POSTPONED", "CANC", "CANCELLED", "ABD", "ABANDONED", "SUSP", "AWD", "WO")

    fun parseSportsDb(json: String, sport: Sport, day: LocalDate, now: Long): Board {
        val events = JSONObject(json).optJSONArray("events") ?: JSONArray()
        val byLeague = LinkedHashMap<String, ArrayList<Match>>()
        val hosts = listOf("thesportsdb.com")
        for (i in 0 until minOf(events.length(), MAX_MATCHES)) {
            val e = events.optJSONObject(i) ?: continue
            val id = clean(e.optString("idEvent"))
            if (id.isEmpty()) continue
            val home = clean(e.optString("strHomeTeam")); val away = clean(e.optString("strAwayTeam"))
            if (home.isEmpty() || away.isEmpty()) continue
            val st = clean(e.optString("strStatus")).uppercase()
            val hs = score(e.optString("intHomeScore")); val aws = score(e.optString("intAwayScore"))
            val state = when {
                st in SDB_OFF -> State.OFF
                st in SDB_DONE -> State.FINISHED
                st in SDB_LIVE -> State.LIVE
                st == "NS" || st.isEmpty() || st == "NOT STARTED" || st == "TIME TO BE DEFINED" -> State.UPCOMING
                else -> continue
            }
            val start = instant(e.optString("strTimestamp")) ?: runCatching {
                LocalDateTime.parse(clean(e.optString("dateEvent")) + "T" + clean(e.optString("strTime")).take(8)).toInstant(ZoneOffset.UTC)
            }.getOrNull()
            fun side(n: String, s: String?, logo: String?, win: Boolean) = Side(n, n, safeLink(logo, hosts), if (state == State.UPCOMING) null else s, win)
            val hn = hs?.toIntOrNull(); val an = aws?.toIntOrNull()
            val done = state == State.FINISHED && hn != null && an != null
            val league = clean(e.optString("strLeague")).ifEmpty { sport.sportsDb }
            byLeague.getOrPut(league) { ArrayList() } += Match(
                id, start, state,
                detail = if (state == State.UPCOMING) "" else clean(e.optString("strStatus")),
                note = if (state == State.OFF) clean(e.optString("strStatus")) else "",
                sides = listOf(side(home, hs, e.optString("strHomeTeamBadge"), done && hn!! > an!!), side(away, aws, e.optString("strAwayTeamBadge"), done && an!! > hn!!)),
                link = null,
            )
        }
        return Board(sport, day, sortLeagues(byLeague.map { League(it.key, sortMatches(it.value)) }), Source.SPORTSDB, now)
    }

    private fun rank(s: State) = when (s) { State.LIVE -> 0; State.UPCOMING -> 1; State.FINISHED -> 2; State.OFF -> 3 }
    private fun sortMatches(m: List<Match>) = m.sortedWith(compareBy<Match>({ rank(it.state) }, { it.start ?: Instant.MAX }))
    /** Leagues with live games first; otherwise the source's own order (ESPN lists the big leagues first). */
    private fun sortLeagues(l: List<League>) = l.withIndex()
        .sortedWith(compareBy({ if (it.value.matches.any { m -> m.state == State.LIVE }) 0 else 1 }, { it.index })).map { it.value }

    /** Today's board is refreshed while something is live; other days and quiet days are not. */
    fun refreshEveryMs(board: Board?, today: LocalDate): Long? =
        if (board != null && board.day == today && board.liveCount > 0) 30_000L else null

    /** Two boards from different feeds as one: leagues keep each feed's order, live leagues first. */
    fun merge(boards: List<Board>): Board {
        val first = boards.first()
        val leagues = LinkedHashMap<String, List<Match>>()
        boards.forEach { b -> b.leagues.forEach { l -> leagues[l.name] = (leagues[l.name] ?: emptyList()) + l.matches } }
        return Board(
            first.sport, first.day, sortLeagues(leagues.map { League(it.key, sortMatches(it.value.distinctBy { m -> m.id })) }),
            boards.flatMap { it.sources }.distinct(), boards.minOf { it.fetchedAt },
        )
    }

    // ---------- match page (ESPN summary) ----------

    data class DetailSide(val name: String, val score: String?, val periods: List<String>, val record: String?, val winner: Boolean)
    data class StatLine(val label: String, val home: String, val away: String)
    data class Moment(val clock: String, val text: String, val team: String?)
    /** One past game from a team's own point of view: [own]–[other], W/L/D as the source wrote it. */
    data class FormGame(val result: String, val own: String?, val other: String?, val opponent: String, val atHome: Boolean, val date: Instant?)
    data class Form(val team: String, val games: List<FormGame>)
    data class Detail(
        val id: String,
        val state: State?,
        val status: String,
        val summary: String,
        val sides: List<DetailSide>,
        val stats: List<StatLine>,
        val moments: List<Moment>,
        val form: List<Form>,
        val venue: String?,
        val attendance: Int?,
        val notes: List<String>,
        val fetchedAt: Long,
    ) {
        /** Nothing beyond the names and score: the page says so instead of showing empty sections. */
        val thin: Boolean get() = stats.isEmpty() && moments.isEmpty() && form.isEmpty() && sides.all { it.periods.isEmpty() }
    }

    fun detailUrl(path: String, id: String): String =
        "https://site.api.espn.com/apis/site/v2/sports/$path/summary?event=" + java.net.URLEncoder.encode(id, "UTF-8")

    private const val MAX_STATS = 16
    private const val MAX_MOMENTS = 24
    private val MOMENT_KINDS = Regex("goal|penalty - scored|red card|touchdown|field goal|safety|\\btry\\b|conversion|home run", RegexOption.IGNORE_CASE)

    /** Shares in 0..1 under a "%" label read as percentages ("0.7" → "70%"); possession gets its sign. Values are never made up. */
    private fun statValue(label: String, v: String): String {
        val d = v.toDoubleOrNull()
        return when {
            d != null && label.endsWith("%") && d in 0.0..1.0 && v.contains('.') -> "${Math.round(d * 100)}%"
            d != null && label.contains("possession", true) && !v.endsWith("%") -> "$v%"
            else -> v
        }
    }

    fun parseDetail(json: String, id: String, now: Long): Detail {
        val root = JSONObject(json)
        val comp = root.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)
        val type = comp?.optJSONObject("status")?.optJSONObject("type")
        val state = when (clean(type?.optString("state"))) { "in" -> State.LIVE; "post" -> State.FINISHED; "pre" -> State.UPCOMING; else -> null }
        val cs = comp?.optJSONArray("competitors") ?: JSONArray()
        data class Raw(val id: String, val home: Boolean, val side: DetailSide)
        val raws = ArrayList<Raw>()
        for (i in 0 until cs.length()) {
            val o = cs.optJSONObject(i) ?: continue
            val t = o.optJSONObject("team")
            val name = clean(t?.optString("displayName")).ifEmpty { clean(t?.optString("name")) }
            if (name.isEmpty()) continue
            val ls = o.optJSONArray("linescores")
            val periods = ArrayList<String>()
            if (ls != null) for (k in 0 until ls.length()) {
                val v = score(ls.optJSONObject(k)?.optString("displayValue")) ?: run { periods.clear(); break }
                periods += v
            }
            val rec = o.optJSONArray("record")?.let { r -> (0 until r.length()).mapNotNull { r.optJSONObject(it) }.firstOrNull { clean(it.optString("type")) == "total" } }
            raws += Raw(
                clean(o.optString("id")), clean(o.optString("homeAway")) == "home",
                DetailSide(
                    name, if (state == State.UPCOMING) null else score(o.optString("score")),
                    if (state == State.UPCOMING) emptyList() else periods.take(12),
                    rec?.let { clean(it.optString("displayValue")).takeIf { v -> v.isNotEmpty() && v.length <= 16 } },
                    state == State.FINISHED && o.optBoolean("winner", false),
                ),
            )
        }
        val ordered = if (raws.any { it.home }) raws.sortedBy { if (it.home) 0 else 1 } else raws
        val homeId = ordered.getOrNull(0)?.id; val awayId = ordered.getOrNull(1)?.id
        // Team statistics: only labels both teams have, in the source's order.
        val stats = ArrayList<StatLine>()
        val teams = root.optJSONObject("boxscore")?.optJSONArray("teams")
        if (teams != null && homeId != null && awayId != null && state != State.UPCOMING) {
            fun statsOf(teamId: String): LinkedHashMap<String, String> {
                val m = LinkedHashMap<String, String>()
                for (i in 0 until teams.length()) {
                    val t = teams.optJSONObject(i) ?: continue
                    if (clean(t.optJSONObject("team")?.optString("id")) != teamId) continue
                    val st = t.optJSONArray("statistics") ?: continue
                    for (k in 0 until st.length()) {
                        val x = st.optJSONObject(k) ?: continue
                        val label = clean(x.optString("label")); val v = clean(x.optString("displayValue"))
                        if (label.isEmpty() || v.isEmpty() || v.length > 16 || !v.any { it.isDigit() }) continue
                        m.putIfAbsent(label, v)
                    }
                }
                return m
            }
            val h = statsOf(homeId); val a = statsOf(awayId)
            for ((label, hv) in h) {
                val av = a[label] ?: continue
                val shown = label.split(' ').joinToString(" ") { w -> if (w.length > 1 && w.all { it.isUpperCase() }) w.lowercase().replaceFirstChar { it.uppercase() } else w }
                stats += StatLine(shown, statValue(label, hv), statValue(label, av))
                if (stats.size >= MAX_STATS) break
            }
        }
        val names = raws.associate { it.id to it.side.name }
        // Moments: goals, cards and scores, as the source wrote them.
        val moments = ArrayList<Moment>()
        root.optJSONArray("keyEvents")?.let { ev ->
            for (i in 0 until ev.length()) {
                val e = ev.optJSONObject(i) ?: continue
                val kind = clean(e.optJSONObject("type")?.optString("text"))
                if (!MOMENT_KINDS.containsMatchIn(kind)) continue
                val who = e.optJSONArray("participants")?.optJSONObject(0)?.optJSONObject("athlete")?.optString("displayName")?.let(::clean).orEmpty()
                val text = if (who.isNotEmpty()) "$kind · $who" else clean(e.optString("shortText")).ifEmpty { kind }
                moments += Moment(clean(e.optJSONObject("clock")?.optString("displayValue")), text, names[clean(e.optJSONObject("team")?.optString("id"))])
                if (moments.size >= MAX_MOMENTS) break
            }
        }
        if (moments.isEmpty()) root.optJSONArray("scoringPlays")?.let { ev ->
            for (i in 0 until ev.length()) {
                val e = ev.optJSONObject(i) ?: continue
                val text = clean(e.optString("text")).ifEmpty { clean(e.optJSONObject("type")?.optString("text")) }
                if (text.isEmpty()) continue
                val period = e.optJSONObject("period")?.optInt("number", 0) ?: 0
                val clock = listOfNotNull(if (period > 0) "P$period" else null, clean(e.optJSONObject("clock")?.optString("displayValue")).ifEmpty { null }).joinToString(" ")
                moments += Moment(clock, text, names[clean(e.optJSONObject("team")?.optString("id"))])
                if (moments.size >= MAX_MOMENTS) break
            }
        }
        // Each team's last games (the "last match" and the form before it).
        val form = ArrayList<Form>()
        root.optJSONArray("lastFiveGames")?.let { lf ->
            for (i in 0 until lf.length()) {
                val t = lf.optJSONObject(i) ?: continue
                val team = clean(t.optJSONObject("team")?.optString("displayName"))
                val teamId = clean(t.optJSONObject("team")?.optString("id"))
                if (team.isEmpty()) continue
                val evs = t.optJSONArray("events") ?: continue
                val games = ArrayList<FormGame>()
                for (k in 0 until minOf(evs.length(), 5)) {
                    val g = evs.optJSONObject(k) ?: continue
                    val opp = clean(g.optJSONObject("opponent")?.optString("displayName"))
                    if (opp.isEmpty()) continue
                    val atHome = clean(g.optString("homeTeamId")) == teamId
                    val hs = score(g.optString("homeTeamScore")); val aws = score(g.optString("awayTeamScore"))
                    val res = clean(g.optString("gameResult")).uppercase().takeIf { it in setOf("W", "L", "D", "T") }.orEmpty()
                    games += FormGame(res, if (atHome) hs else aws, if (atHome) aws else hs, opp, atHome, instant(g.optString("gameDate")))
                }
                if (games.isNotEmpty()) form += Form(team, games)
            }
        }
        val form2 = form.sortedBy { f -> ordered.indexOfFirst { it.side.name == f.team }.let { if (it < 0) 9 else it } }
        val info = root.optJSONObject("gameInfo")
        val venue = clean(info?.optJSONObject("venue")?.optString("fullName")).ifEmpty { null }
        val att = info?.optInt("attendance", 0)?.takeIf { it in 1..500_000 }
        val notes = root.optJSONArray("notes")?.let { n -> (0 until minOf(n.length(), 3)).mapNotNull { clean(n.optJSONObject(it)?.optString("text")).ifEmpty { null } } }.orEmpty()
        return Detail(
            id, state, clean(type?.optString("shortDetail")).ifEmpty { clean(type?.optString("description")) },
            clean(comp?.optJSONObject("status")?.optString("summary")),
            ordered.map { it.side }.take(2), stats, moments, form2, venue, att, notes, now,
        )
    }

    // ---------- network ----------

    class Failed(val reason: Reason) : Exception(reason.name)
    enum class Reason { OFFLINE, SOURCE_DOWN, BAD_ANSWER, BUSY }

    @Volatile internal var espnForTest: String? = null
    @Volatile internal var sportsDbForTest: String? = null
    @Volatile internal var clockForTest: (() -> Long)? = null
    private fun now() = clockForTest?.invoke() ?: System.currentTimeMillis()

    /**
     * Polite use of free feeds (documented in AUDIT_B15): one request at a time, answers kept in memory for a
     * while ([ttlMs]), a manual refresh within [MIN_GAP_MS] of the last answer reuses it, and a feed that says
     * "too many requests" (429/503) is left alone for its Retry-After (60 s when it gives none).
     */
    const val MIN_GAP_MS = 10_000L
    const val STALE_MAX_MS = 2 * 60 * 60_000L
    private const val MAX_CACHE = 40
    private val gate = kotlinx.coroutines.sync.Mutex()
    private val cache = object : LinkedHashMap<String, Board>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Board>?) = size > MAX_CACHE
    }
    private val details = object : LinkedHashMap<String, Detail>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Detail>?) = size > MAX_CACHE
    }
    private val blockedUntil = HashMap<String, Long>()
    @Volatile internal var requestsForTest = 0

    /** How long an answer is reused: live day 25 s, today 3 min, future 15 min, past days 30 min. */
    fun ttlMs(board: Board, today: LocalDate): Long = when {
        board.day == today && board.liveCount > 0 -> 25_000L
        board.day == today -> 3 * 60_000L
        board.day.isAfter(today) -> 15 * 60_000L
        else -> 30 * 60_000L
    }

    internal fun resetForTest() { synchronized(cache) { cache.clear(); details.clear(); blockedUntil.clear() }; requestsForTest = 0 }

    private fun hostOf(url: String) = runCatching { java.net.URI(url).host }.getOrNull() ?: url

    private fun fetch(ctx: Context, url: String): String {
        val host = hostOf(url)
        val until = synchronized(cache) { blockedUntil[host] ?: 0L }
        if (now() < until) throw Failed(Reason.BUSY)
        requestsForTest++
        val client = NetworkClient.get(ctx).newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (r.code == 429 || r.code == 503) {
                val wait = r.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(5, 15 * 60)?.times(1000) ?: 60_000L
                synchronized(cache) { blockedUntil[host] = now() + wait }
                throw Failed(Reason.BUSY)
            }
            if (!r.isSuccessful) throw Failed(Reason.SOURCE_DOWN)
            val body = r.body?.string() ?: throw Failed(Reason.BAD_ANSWER)
            if (body.length > 3_000_000) throw Failed(Reason.BAD_ANSWER)
            return body
        }
    }

    private fun reasonOf(e: Throwable?): Reason = when {
        e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException -> Reason.OFFLINE
        e is Failed -> e.reason
        e is org.json.JSONException -> Reason.BAD_ANSWER
        else -> Reason.SOURCE_DOWN
    }

    private fun fetchBoard(ctx: Context, sport: Sport, day: LocalDate): Board {
        val t = now()
        val espn = sport.espn.map { q -> runCatching { parseEspn(fetch(ctx, espnForTest ?: espnUrl(sport, day, q)), sport, day, t) } }
        val espnOk = espn.mapNotNull { it.getOrNull() }
        val wantDb = sport.alsoSportsDb || sport.espn.isEmpty() || espnOk.isEmpty()
        val db = if (wantDb) runCatching { parseSportsDb(fetch(ctx, sportsDbForTest ?: sportsDbUrl(sport, day)), sport, day, t) } else null
        val all = espnOk + listOfNotNull(db?.getOrNull())
        if (all.isEmpty()) throw Failed(reasonOf(espn.firstNotNullOfOrNull { it.exceptionOrNull() } ?: db?.exceptionOrNull()))
        val merged = if (all.size == 1) all.single() else merge(all)
        // "Fallback" only when ESPN was meant to answer and did not.
        return merged.copy(fallback = sport.espn.isNotEmpty() && espnOk.isEmpty())
    }

    /**
     * The day's board. Reuses a fresh answer; [force] (pull to refresh, the refresh button, live updates)
     * asks again unless the last answer is under [MIN_GAP_MS] old. When the feeds cannot be reached, the last
     * answer (under [STALE_MAX_MS]) comes back marked [Board.stale] so the page can say how old it is;
     * nothing is ever presented as newer than it is.
     */
    suspend fun load(ctx: Context, sport: Sport, day: LocalDate, force: Boolean = false, today: LocalDate = LocalDate.now()): Result<Board> = withContext(Dispatchers.IO) {
        gate.withLock {
            val key = "${sport.id}|$day"
            val have = synchronized(cache) { cache[key] }
            val age = have?.let { now() - it.fetchedAt } ?: Long.MAX_VALUE
            if (have != null && (age < MIN_GAP_MS || (!force && age < ttlMs(have, today)))) return@withLock Result.success(have)
            val fresh = runCatching { fetchBoard(ctx, sport, day) }
            fresh.onSuccess { b -> synchronized(cache) { cache[key] = b } }
            if (fresh.isSuccess) return@withLock fresh
            if (have != null && age < STALE_MAX_MS) return@withLock Result.success(have.copy(stale = true))
            Result.failure(Failed(reasonOf(fresh.exceptionOrNull())))
        }
    }

    /** The match page, from ESPN only (TheSportsDB's free tier has no match statistics). Cached like boards. */
    suspend fun loadDetail(ctx: Context, m: Match): Result<Detail> = withContext(Dispatchers.IO) {
        val path = m.espnPath ?: return@withContext Result.failure(Failed(Reason.SOURCE_DOWN))
        val key = "$path|${m.id}"
        val have = synchronized(cache) { details[key] }
        val ttl = if (m.state == State.LIVE) 25_000L else if (m.state == State.FINISHED) 30 * 60_000L else 5 * 60_000L
        if (have != null && now() - have.fetchedAt < ttl) return@withContext Result.success(have)
        gate.withLock {
            runCatching { parseDetail(fetch(ctx, detailUrl(path, m.id)), m.id, now()) }
                .onSuccess { d -> synchronized(cache) { details[key] = d } }
                // The last page read (its time is on screen) beats an error; with none, say why.
                .recoverCatching { e -> have ?: throw Failed(reasonOf(e)) }
        }
    }
}
