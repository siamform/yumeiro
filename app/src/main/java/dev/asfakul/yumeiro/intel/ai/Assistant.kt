/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.intel.Aspect
import dev.asfakul.yumeiro.intel.Candidates
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.MetaCache
import dev.asfakul.yumeiro.intel.Tag
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * B21/B22: the assistant. Before the model is asked, Yumeiro does its own research for the request (AniList for
 * anime, manga and novels; TMDB through Yumeiro Cloud for films and series; the For You picks) and sends it as
 * EVIDENCE with the active profile's taste slice. Every title in the answer is checked against that evidence (or
 * looked up); what can't be found is shown as unverified. The rules are the owner's B22 text (assets).
 */
object Assistant {
    private const val TAG = "Assistant"
    private const val RULES = "intel/assistant_rules.md"
    private const val EVIDENCE_MAX = 28
    private const val VERIFY_LOOKUPS = 3
    private const val TAGS_TTL = 30L * 86_400_000L

    data class Research(val anilist: Boolean, val tmdb: Boolean, val web: Boolean, val evidence: Int, val seeds: List<String> = emptyList(), val tags: List<String> = emptyList())

    data class Pick(
        val title: String, val year: Int?, val category: MediaCategory?, val tags: List<String>,
        val why: String, val differences: String, val reception: String, val discovery: String, val confidence: String,
        /** Found in Yumeiro's evidence or by a lookup (AniList / TMDB): the title and year are real. */
        val verified: Boolean,
        /** Where it can be opened from (cover, ids); null when unverified. */
        val rec: Discovery.Rec? = null,
        /** Something the person should know (\"You finished this\"). */
        val note: String? = null,
    )

    data class Section(val name: String, val picks: List<Pick>)

    data class Answer(
        val reply: String, val clarify: String = "", val sections: List<Section> = emptyList(),
        val sources: List<AiSource> = emptyList(), val research: Research? = null, val model: String = "",
    ) {
        val picks: List<Pick> get() = sections.flatMap { it.picks }
    }

    /** One title Yumeiro found for the request. */
    internal data class Ev(val rec: Discovery.Rec, val alt: List<String>, val why: String, val source: String)

    // region request reading (pure)

    private val CATEGORY_WORDS: List<Pair<MediaCategory, Regex>> = listOf(
        MediaCategory.ANIME to Regex("(?i)\\banime\\b"),
        MediaCategory.MANGA to Regex("(?i)\\b(manga|manhwa|manhua|webtoons?|comics?)\\b"),
        MediaCategory.NOVEL to Regex("(?i)\\b(light novels?|web novels?|novels?|books?)\\b"),
        MediaCategory.MOVIE to Regex("(?i)\\b(movies?|films?)\\b"),
        MediaCategory.SERIES to Regex("(?i)\\b(tv shows?|tv series|series|shows?|k-?dramas?|dramas?)\\b"),
        MediaCategory.MUSIC to Regex("(?i)\\b(music|songs?|artists?|albums?|bands?|singers?)\\b"),
        MediaCategory.YOUTUBE to Regex("(?i)\\b(youtube|youtubers?|channels?|creators?)\\b"),
    )
    private val SEED = Regex("(?i)\\b(?:similar to|(?<!\\b(?:i|we|you|they|really|would|do|dont|don't|also|just|totally) )like|such as|reminds? me of|same vibe as|in the vein of|based on|fans? of|if i loved?|because i (?:loved?|liked?|enjoyed))\\s+[\"“'‘]?([^\"”’,.?!()]{2,60})")
    private val SEED_STOP = Regex("(?i)\\s+(?:but|with|and|that|which|where|for|from|in|on)\\s.*$")
    private val NOT_A_SEED = Regex("(?i)^(my|the themes?|themes?|what|something|anything|this|that|it|me|them|a |an |the ones?)\\b")
    private val REWATCH = Regex("(?i)\\b(rewatch|re-watch|reread|re-read|again|already (?:watched|read|seen))\\b")
    private val UNDERRATED = Regex("(?i)\\b(underrated|hidden gems?|lesser[- ]known|obscure|underdogs?|niche|not (?:so )?popular|unknown)\\b")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /** Categories named in [q], first mention first (\"manga that matches my favourite anime\" → manga, anime). */
    fun categoriesIn(q: String): List<MediaCategory> =
        CATEGORY_WORDS.mapNotNull { (cat, re) -> re.find(q)?.let { cat to it.range.first } }.sortedBy { it.second }.map { it.first }.distinct()

    /** Titles named as a reference (\"similar to Future Diary\"), at most 2. */
    fun seedsIn(q: String): List<String> = SEED.findAll(q).map { it.groupValues[1].replace(SEED_STOP, "").trim().trimEnd('\'', '’') }
        .filter { it.length >= 2 && !NOT_A_SEED.containsMatchIn(it) && categoriesIn(it).isEmpty() }
        .distinctBy { Tag.norm(it) }.take(2).toList()

    fun wantsRewatch(q: String) = REWATCH.containsMatchIn(q)
    fun wantsUnderrated(q: String) = UNDERRATED.containsMatchIn(q)

    /** Common request words for real AniList tags (\"survival game\" is AniList's Death Game). */
    private val ALIASES = mapOf(
        "survival game" to "Death Game", "survival games" to "Death Game", "death games" to "Death Game", "yanderes" to "Yandere",
        "time loop" to "Time Loop", "isekai" to "Isekai", "villainess" to "Villainess", "slow burn" to "Slow Burn",
        "anti hero" to "Anti-Hero", "antihero" to "Anti-Hero", "found family" to "Found Family", "cozy" to "Iyashikei", "healing" to "Iyashikei",
    )

    /** AniList tags and genres named in [q] (whole words; plurals too), longest first, at most 4. */
    fun tagsIn(q: String, vocabulary: List<String>): List<String> {
        val words = SPLIT.split(q.lowercase()).filter { it.isNotBlank() }
        val text = " " + words.joinToString(" ") + " "
        val found = LinkedHashSet<String>()
        ALIASES.forEach { (k, v) -> if (text.contains(" $k ") && vocabulary.any { it.equals(v, true) }) found += v }
        vocabulary.sortedByDescending { it.length }.forEach { t ->
            val n = " " + SPLIT.split(t.lowercase()).filter { it.isNotBlank() }.joinToString(" ")
            if (n.isBlank() || n.length < 4) return@forEach
            if (text.contains("$n ") || text.contains("${n}s ") || text.contains("${n}es ")) {
                if (found.none { f -> f.lowercase().contains(t.lowercase()) }) found += t
            }
        }
        return found.take(4).toList()
    }

    // endregion

    // region AniList tag list (public, cached for everyone)

    @Volatile private var vocabulary: Pair<List<String>, List<String>>? = null

    /** AniList's tag names (no adult tags) and genres; cached 30 days. Empty when offline and never fetched. */
    internal suspend fun aniVocabulary(c: Context): Pair<List<String>, List<String>> {
        vocabulary?.let { return it }
        val f = File(c.applicationContext.cacheDir, "intel/anilist_tags.json")
        fun parse(o: JSONObject): Pair<List<String>, List<String>> {
            val tags = o.optJSONArray("MediaTagCollection")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { !it.optBoolean("isAdult") }.map { it.optString("name") } }.orEmpty()
            val genres = o.optJSONArray("GenreCollection")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it.isNotBlank() && it != "Hentai" }
            return tags.filter { it.isNotBlank() } to genres
        }
        val cached = runCatching { if (f.isFile && System.currentTimeMillis() - f.lastModified() < TAGS_TTL) parse(JSONObject(f.readText())) else null }.getOrNull()
        if (cached != null && cached.first.isNotEmpty()) return cached.also { vocabulary = it }
        val data = runCatching { MetaCache.ani("query{MediaTagCollection{name isAdult} GenreCollection}", JSONObject()) }.getOrNull()
            ?: return (cached ?: (emptyList<String>() to emptyList()))
        runCatching { f.parentFile?.mkdirs(); f.writeText(data.toString()) }
        return parse(data).also { vocabulary = it }
    }

    // endregion

    // region research

    private fun aniType(cat: MediaCategory) = if (cat == MediaCategory.ANIME) "ANIME" else "MANGA"
    private fun aniFormats(cat: MediaCategory): JSONArray? = when (cat) {
        MediaCategory.MANGA -> JSONArray(listOf("MANGA", "ONE_SHOT")); MediaCategory.NOVEL -> JSONArray(listOf("NOVEL")); else -> null
    }
    private val ANI = setOf(MediaCategory.ANIME, MediaCategory.MANGA, MediaCategory.NOVEL)
    private val FILM = setOf(MediaCategory.MOVIE, MediaCategory.SERIES)

    internal fun recOf(m: MetaCache.Meta, cat: MediaCategory): Discovery.Rec =
        Discovery.Rec(Discovery.keyOf(m), cat, m.title, m.cover, m.year, m.ids, popularity = m.popularity, score = m.score, format = m.format,
            tags = m.tags.filter { !it.spoiler }.take(24))

    private fun recOf(i: HubDiscover.Item, cat: MediaCategory): Discovery.Rec =
        Discovery.Rec("tm:${i.kind}:${i.id}", cat, i.title, i.poster, i.year, TasteSignals.Ids(tmdb = i.id.toInt(), tmdbKind = i.kind),
            popularity = i.votes ?: 0, score = ((i.rating ?: 0.0) * 10).toInt(), tags = i.genres.map { Tag(it, Aspect.GENRE, 1f) })

    private suspend fun seedMeta(c: Context, title: String, cats: List<MediaCategory>): MetaCache.Meta? {
        for (cat in cats) {
            val e = TasteSignals.Entry("seed|${Tag.norm(title)}", cat, title)
            val m = runCatching { MetaCache.lookup(c, e) }.getOrNull()
            if (m != null && !m.adult) return m
        }
        return null
    }

    /** Yumeiro's own research for [q] in [cat] (see the class note). Never throws; what was unreachable is in [Research]. */
    internal suspend fun research(c: Context, q: String, cat: MediaCategory?, entries: List<TasteSignals.Entry>, onStep: (String) -> Unit): Pair<List<Ev>, Research> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Ev>()
        val known = Candidates.Known.of(entries)
        val rewatch = wantsRewatch(q)
        fun add(r: Discovery.Rec, alt: List<String>, why: String, source: String) {
            if (out.size >= EVIDENCE_MAX * 2) return
            val m = MetaCache.Meta(r.title, r.category, r.ids, r.tags, r.year, alt = alt)
            if (!rewatch && known.has(m)) return
            out.putIfAbsent(r.key, Ev(r, alt, why, source))
        }
        var aniOk = false; var tmdbOk = false
        val seeds = seedsIn(q)
        val seedNames = ArrayList<String>()
        var tags: List<String> = emptyList()
        if (cat in ANI) {
            onStep("Looking it up on AniList…")
            val (vocab, genres) = aniVocabulary(c)
            if (vocab.isNotEmpty()) aniOk = true
            for (s in seeds) {
                val m = seedMeta(c, s, listOf(cat!!) + ANI.filter { it != cat }) ?: continue
                aniOk = true
                seedNames += m.title
                if (m.category == cat) {
                    runCatching { Candidates.likeTitle(m) }.getOrNull()?.forEach { f -> add(recOf(f.meta, cat), f.meta.alt, "AniList users recommend it for ${m.title}", "AniList") }
                } else {
                    // \"Manga like <an anime>\": the anime's own strongest story tags, searched among manga.
                    val t = m.tags.filter { !it.spoiler && (it.aspect == Aspect.THEMES || it.aspect == Aspect.CHARACTERS) }.sortedByDescending { it.weight }.take(3).map { it.name }
                    if (t.isNotEmpty()) aniSearch(cat, t, emptyList(), false, known)?.forEach { add(recOf(it, cat), it.alt, "Shares ${m.title}'s ${t.joinToString(", ")}", "AniList") }
                }
            }
            tags = tagsIn(q, vocab)
            val g = tagsIn(q, genres)
            val under = wantsUnderrated(q)
            if (tags.isNotEmpty() || g.isNotEmpty() || under && seedNames.isEmpty()) {
                onStep("Searching AniList for ${(tags + g).ifEmpty { listOf("lesser-known titles") }.joinToString(", ")}…")
                val tasteTags = if (tags.isEmpty() && g.isEmpty()) Discovery.tasteFor(entries, cat!!, System.currentTimeMillis())?.let { t -> t.top(3, Aspect.THEMES).map { t.names[it.first] ?: it.first } }.orEmpty() else emptyList()
                aniSearch(cat!!, (tags + tasteTags).distinct(), g, under, known)?.also { aniOk = true }?.forEach { m ->
                    add(recOf(m, cat), m.alt, (if (under) "Well rated but little known, " else "") + "tagged ${(tags + g).ifEmpty { tasteTags }.joinToString(", ")} on AniList", "AniList")
                }
                tags = tags + g
            }
        }
        if (cat in FILM && HubDiscover.available()) {
            onStep("Looking it up on TMDB…")
            val kind = if (cat == MediaCategory.MOVIE) "movie" else "tv"
            val fc: MediaCategory = cat!!
            for (s in seeds) {
                val m = seedMeta(c, s, listOf(cat!!)) ?: continue
                seedNames += m.title
                val id = m.ids.tmdb ?: continue
                runCatching { HubDiscover.recommend(kind, id.toLong()) }.getOrNull()?.also { tmdbOk = true }?.items?.filter { !it.anime }?.forEach { add(recOf(it, fc), emptyList(), "TMDB recommends it for ${m.title}", "TMDB") }
            }
            val table = if (kind == "movie") HubDiscover.MOVIE_GENRES else HubDiscover.TV_GENRES
            val genreIds = table.filter { (_, name) -> name.split(" & ").any { part -> tagsIn(q, listOf(part)).isNotEmpty() } }.map { it.first }.take(2)
            if (genreIds.isNotEmpty()) {
                tags = table.filter { it.first in genreIds }.map { it.second }
                runCatching { HubDiscover.page(HubDiscover.Query(kind, HubDiscover.Sort.TOP, genres = genreIds, minRating = if (wantsUnderrated(q)) 6 else 7)) }.getOrNull()
                    ?.also { tmdbOk = true }?.items?.filter { !it.anime && (!wantsUnderrated(q) || (it.votes ?: 0) < 3000) }?.forEach { add(recOf(it, fc), emptyList(), "Well rated in ${tags.joinToString(", ")} on TMDB", "TMDB") }
            }
        }
        // The profile's For You picks (already ranked against its taste; no network).
        val shelves = if (cat == null) Discovery.SHELVES else listOf(cat).filter { it in Discovery.SHELVES }
        for (s in shelves) Discovery.shelf(c, s)?.let { shelf ->
            if (s in ANI) aniOk = true else tmdbOk = true
            shelf.recs.take(if (cat == null) 4 else 10).forEach { r ->
                add(r, emptyList(), "A For You pick, shares " + r.reasons.take(3).joinToString(", ") { it.name }.ifBlank { "this profile's taste" } + (if (r.gem) " (hidden gem)" else ""), "Yumeiro For You")
            }
        }
        val list = out.values.take(EVIDENCE_MAX)
        list to Research(aniOk, tmdbOk, false, list.size, seedNames, tags)
    }

    private suspend fun aniSearch(cat: MediaCategory, tags: List<String>, genres: List<String>, underrated: Boolean, known: Candidates.Known): List<MetaCache.Meta>? {
        if (tags.isEmpty() && genres.isEmpty()) return null
        val args = buildString {
            append("\$t:MediaType,\$f:[MediaFormat],\$no:[Int]")
            if (tags.isNotEmpty()) append(",\$tags:[String]")
            if (genres.isNotEmpty()) append(",\$g:[String]")
        }
        val filter = buildString {
            append("type:\$t,format_in:\$f,id_not_in:\$no,isAdult:false")
            if (tags.isNotEmpty()) append(",tag_in:\$tags")
            if (genres.isNotEmpty()) append(",genre_in:\$g")
            if (underrated) append(",popularity_lesser:40000,averageScore_greater:66")
            append(",sort:[SCORE_DESC]")
        }
        val q = "query($args){Page(perPage:30){media($filter){${Candidates.FIELDS}}}}"
        val v = JSONObject().put("t", aniType(cat)).apply { aniFormats(cat)?.let { put("f", it) } }.put("no", JSONArray(known.anilist.take(200).toList()))
            .apply { if (tags.isNotEmpty()) put("tags", JSONArray(tags)); if (genres.isNotEmpty()) put("g", JSONArray(genres)) }
        val data = runCatching { MetaCache.ani(q, v) }.onFailure { Log.i(TAG, "search: ${it.javaClass.simpleName}") }.getOrNull() ?: return null
        val a = data.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(MetaCache::parseAniList) }.filter { !it.adult && !known.has(it) }
    }

    /** Pure: the EVIDENCE block (facts only). */
    internal fun evidenceJson(list: List<Ev>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            val r = e.rec
            put(JSONObject().put("title", r.title).put("category", r.category.code).apply {
                r.year?.let { put("year", it) }
                r.format?.let { put("format", it) }
                if (r.score > 0) put("score", r.score)
                if (r.popularity > 0) put(if (r.category in FILM) "votes" else "members", r.popularity)
                val t = r.tags.filter { !it.spoiler }.sortedByDescending { it.weight * it.aspect.weight }.take(8).map { it.name }
                if (t.isNotEmpty()) put("tags", JSONArray(t))
                e.alt.take(2).takeIf { it.isNotEmpty() }?.let { put("also_known_as", JSONArray(it)) }
                put("found", e.why).put("source", e.source)
            })
        }
    }

    // endregion

    // region the answer

    /** Pure: the model's JSON → [Answer] (unverified; [verify] checks the titles). Prose without JSON becomes the reply. */
    internal fun parse(text: String, fallback: MediaCategory?): Answer {
        val o = AiHttp.firstJson(text) ?: return Answer(text.trim().take(4_000))
        val sections = o.optJSONArray("sections")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { s ->
                val picks = s.optJSONArray("items")?.let { items ->
                    (0 until items.length()).mapNotNull { items.optJSONObject(it) }.mapNotNull { p ->
                        val title = p.optString("title").trim().takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
                        fun str(k: String) = p.optString(k).trim().takeIf { it != "null" }.orEmpty().take(600)
                        Pick(
                            title.take(160), p.optInt("year").takeIf { it in 1900..2100 }, MediaCategory.of(p.optString("category").lowercase()) ?: fallback,
                            p.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it.isNotBlank() }.take(6) }.orEmpty(),
                            str("why"), str("differences"), str("reception"), str("discovery"), str("confidence"), verified = false,
                        )
                    }
                }.orEmpty()
                if (picks.isEmpty()) null else Section(s.optString("name").ifBlank { "Picks" }.take(40), picks.take(8))
            }
        }.orEmpty()
        return Answer(o.optString("reply").trim().takeIf { it != "null" }.orEmpty().take(2_000), o.optString("clarify").trim().takeIf { it != "null" }.orEmpty().take(400), sections)
    }

    /** Pure: [pick] in [evidence] (same name, or one of its other names; the closest year). */
    internal fun match(pick: Pick, evidence: List<Ev>): Ev? {
        val want = Tag.norm(pick.title)
        val named = evidence.filter { e -> (listOf(e.rec.title) + e.alt).any { Tag.norm(it) == want } }
        return if (pick.year == null) named.firstOrNull() else named.minByOrNull { e -> e.rec.year?.let { kotlin.math.abs(it - pick.year) } ?: 3 }
    }

    /**
     * Checks every pick: in the evidence → verified; else up to [VERIFY_LOOKUPS] lookups on AniList / TMDB → verified
     * with the real year; else unverified. Titles the profile turned away are removed; finished ones are removed unless a
     * rewatch was asked for.
     */
    internal suspend fun verify(c: Context, a: Answer, evidence: List<Ev>, entries: List<TasteSignals.Entry>, rewatch: Boolean,
                                lookup: suspend (Pick) -> MetaCache.Meta? = { p -> defaultLookup(c, p) }): Answer {
        var lookups = 0
        val known = entries.associateBy { Tag.norm(it.title) }
        val sections = a.sections.mapNotNull { s ->
            val picks = s.picks.mapNotNull { p ->
                val ev = match(p, evidence)
                val meta = if (ev == null && lookups < VERIFY_LOOKUPS && p.category != null && p.category !in setOf(MediaCategory.MUSIC, MediaCategory.YOUTUBE)) { lookups++; runCatching { lookup(p) }.getOrNull()?.takeIf { !it.adult } } else null
                val rec = ev?.rec ?: meta?.let { recOf(it, p.category!!) }
                val names = (listOf(p.title) + (ev?.alt.orEmpty()) + (meta?.alt.orEmpty()) + listOfNotNull(rec?.title)).map { Tag.norm(it) }
                val mine = names.firstNotNullOfOrNull { known[it] } ?: entries.firstOrNull { e -> rec != null && ((rec.ids.anilist != null && e.ids.anilist == rec.ids.anilist) || (rec.ids.tmdb != null && e.ids.tmdb == rec.ids.tmdb)) }
                if (mine != null && (mine.dismissedAt > 0 || mine.feedback == TasteSignals.Feedback.NOT_FOR_ME)) return@mapNotNull null
                if (mine != null && mine.completed && !rewatch) return@mapNotNull null
                val note = when {
                    mine?.completed == true -> "You finished this"
                    mine?.started == true -> "You've started this"
                    rec == null -> "Yumeiro couldn't confirm this title, so its details come from the AI alone"
                    else -> null
                }
                p.copy(title = rec?.title ?: p.title, year = rec?.year ?: p.year, category = rec?.category ?: p.category, verified = rec != null, rec = rec, note = note)
            }.distinctBy { Tag.norm(it.title) }
            if (picks.isEmpty()) null else s.copy(picks = picks)
        }
        // A title in two sections is shown once (the first).
        val seen = HashSet<String>()
        val deduped = sections.mapNotNull { s -> s.picks.filter { seen.add(Tag.norm(it.title)) }.takeIf { it.isNotEmpty() }?.let { s.copy(picks = it) } }
        return a.copy(sections = deduped)
    }

    private suspend fun defaultLookup(c: Context, p: Pick): MetaCache.Meta? {
        val cat = p.category ?: return null
        return MetaCache.lookup(c, TasteSignals.Entry("check|${Tag.norm(p.title)}", cat, p.title, year = p.year))
    }

    private fun rules(c: Context): String = runCatching { c.assets.open(RULES).bufferedReader().use { it.readText() } }.getOrDefault("")

    /** Pure: RESEARCH STATUS for the prompt and the screen. */
    internal fun status(r: Research, webOn: Boolean): String = buildString {
        append("AniList: ").append(if (r.anilist) "reachable" else "not reached").append(". ")
        append("TMDB (Yumeiro Cloud): ").append(if (r.tmdb) "reachable" else "not reached").append(". ")
        append("Web search: ").append(if (webOn) "on (Google, through the AI provider)" else "off").append(". ")
        append("Reddit and X: not accessible to Yumeiro. ")
        append("Evidence titles: ${r.evidence}.")
        if (r.seeds.isNotEmpty()) append(" Reference titles found: ${r.seeds.joinToString(", ")}.")
        if (r.tags.isNotEmpty()) append(" Tags searched: ${r.tags.joinToString(", ")}.")
    }

    /**
     * One question: research, ask the profile's provider, verify. [history] = the earlier turns of this chat
     * (text only). Throws [AiError] (no key, key rejected, offline…).
     */
    suspend fun ask(c: Context, q: String, screenCategory: MediaCategory?, history: List<AiMessage>, recentlySuggested: List<String>, onStep: (String) -> Unit = {}): Answer {
        val cfg = AiSettings.get(c)
        if (!cfg.ready || !cfg.forAssistant) throw AiError.NotSetUp()
        val mentioned = categoriesIn(q)
        val cat = mentioned.firstOrNull() ?: screenCategory
        onStep("Reading this profile's taste…")
        val entries = withContext(Dispatchers.IO) { TasteSignals.entries(c) }
        val (evidence, research0) = research(c, q, cat, entries, onStep)
        val web = cfg.webSearch && cfg.preset?.webSearch == true
        val research = research0.copy(web = web)
        val profile = withContext(Dispatchers.IO) {
            val main = AiContext.slice(c, cat)
            val json = JSONObject(main.json.toString())
            // Cross-category requests (\"manga that matches my favourite anime\") carry the other category's slice too.
            mentioned.drop(1).forEach { other -> AiContext.slice(c, other).json.optJSONObject(other.code)?.let { json.put(other.code, it) } }
            if (main.empty && mentioned.size <= 1) AiContext.slice(c, null).json.let { o -> o.keys().forEach { k -> if (!json.has(k)) json.put(k, o.get(k)) } }
            if (recentlySuggested.isNotEmpty()) json.put("recently_suggested_in_chat", JSONArray(recentlySuggested.take(30)))
            json
        }
        onStep(if (web) "Asking ${cfg.preset?.label} (with web search)…" else "Asking ${cfg.preset?.label}…")
        val message = buildString {
            append("ACTIVE CATEGORY: ").append(cat?.label ?: "any").append('\n')
            append("PROFILE:\n").append(profile.toString()).append("\n\n")
            append("EVIDENCE:\n").append(evidenceJson(evidence).toString()).append("\n\n")
            append("REQUEST: ").append(q.take(1_000))
        }
        val system = rules(c) + "\n\nRESEARCH STATUS: " + status(research, web)
        val reply = AiSettings.chat(c, AiRequest("", system, history.takeLast(6) + AiMessage(true, message), json = true, webSearch = web, maxTokens = 4096, temperature = 0.6))
        onStep("Checking every title…")
        val parsed = parse(reply.text, cat)
        val checked = verify(c, parsed, evidence, entries, wantsRewatch(q))
        return checked.copy(sources = reply.sources, research = research.copy(web = web && reply.searched), model = reply.model)
    }

    // endregion
}
