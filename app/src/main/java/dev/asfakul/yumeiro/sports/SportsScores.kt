/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sports

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
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
    enum class Sport(val id: String, val espn: String, val sportsDb: String) {
        FOOTBALL("football", "sport=soccer", "Soccer"),
        CRICKET("cricket", "sport=cricket", "Cricket"),
        BASKETBALL("basketball", "sport=basketball&league=nba", "Basketball"),
        AMERICAN_FOOTBALL("american_football", "sport=football&league=nfl", tr(R.string.american_football, "American Football")),
        HOCKEY("hockey", "sport=hockey&league=nhl", tr(R.string.ice_hockey, "Ice Hockey")),
        BASEBALL("baseball", "sport=baseball&league=mlb", "Baseball"),
        TENNIS("tennis", "sport=tennis", "Tennis"),
        RUGBY("rugby", "sport=rugby", "Rugby"),
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
    )
    data class League(val name: String, val matches: List<Match>)
    data class Board(val sport: Sport, val day: LocalDate, val leagues: List<League>, val source: Source, val fetchedAt: Long) {
        val liveCount: Int get() = leagues.sumOf { l -> l.matches.count { it.state == State.LIVE } }
        val empty: Boolean get() = leagues.isEmpty()
    }

    private val DAY = DateTimeFormatter.BASIC_ISO_DATE
    private const val MAX_TEXT = 80
    private const val MAX_MATCHES = 400

    fun espnUrl(sport: Sport, day: LocalDate): String =
        "https://site.web.api.espn.com/apis/v2/scoreboard/header?${sport.espn}&dates=${day.format(DAY)}&lang=en&region=us"

    fun sportsDbUrl(sport: Sport, day: LocalDate): String =
        "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$day&s=" + java.net.URLEncoder.encode(sport.sportsDb, "UTF-8").replace("+", "%20")

    // ---------- parsing (pure, unit-tested against saved real answers) ----------

    private fun clean(s: String?): String {
        if (s == null || s == "null") return ""
        val t = s.filter { !it.isISOControl() }.trim()
        return if (t.length > MAX_TEXT) t.take(MAX_TEXT - 1) + "…" else t
    }

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
        return t.takeIf { it.length <= 40 && it.all { c -> c.isDigit() || c in " /()&.-*+ovdfOVDF" } }
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
            val ls = sports.optJSONObject(i)?.optJSONArray("leagues") ?: continue
            for (j in 0 until ls.length()) {
                val l = ls.optJSONObject(j) ?: continue
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
                    matches += Match(id, instant(e.optString("date")), state, detail, note, ordered.map { it.second }.take(2), link)
                    total++
                }
                if (matches.isNotEmpty()) leagues += League(name, sortMatches(matches))
            }
        }
        return Board(sport, day, sortLeagues(leagues), Source.ESPN, now)
    }

    private val SDB_LIVE = setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE", tr(R.string.in_play, "IN PLAY"), "Q1", "Q2", "Q3", "Q4", "OT", "INT")
    private val SDB_DONE = setOf("FT", "AET", "PEN", "AP", tr(R.string.match_finished, "MATCH FINISHED"), "FINISHED", "AOT", "FT_PEN")
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

    // ---------- network ----------

    class Failed(val reason: Reason) : Exception(reason.name)
    enum class Reason { OFFLINE, SOURCE_DOWN, BAD_ANSWER }

    @Volatile internal var espnForTest: String? = null
    @Volatile internal var sportsDbForTest: String? = null

    private fun fetch(ctx: Context, url: String): String {
        val client = NetworkClient.get(ctx).newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Failed(Reason.SOURCE_DOWN)
            val body = r.body?.string() ?: throw Failed(Reason.BAD_ANSWER)
            if (body.length > 3_000_000) throw Failed(Reason.BAD_ANSWER)
            return body
        }
    }

    /** ESPN first; TheSportsDB only if ESPN could not be read. The answer always names its source. */
    suspend fun load(ctx: Context, sport: Sport, day: LocalDate): Result<Board> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val first = runCatching { parseEspn(fetch(ctx, espnForTest ?: espnUrl(sport, day)), sport, day, now) }
        if (first.isSuccess) return@withContext first
        val second = runCatching { parseSportsDb(fetch(ctx, sportsDbForTest ?: sportsDbUrl(sport, day)), sport, day, now) }
        if (second.isSuccess) return@withContext second
        val e = first.exceptionOrNull()
        val reason = when {
            e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException -> Reason.OFFLINE
            e is Failed -> e.reason
            e is org.json.JSONException -> Reason.BAD_ANSWER
            else -> Reason.SOURCE_DOWN
        }
        Result.failure(Failed(reason))
    }
}
