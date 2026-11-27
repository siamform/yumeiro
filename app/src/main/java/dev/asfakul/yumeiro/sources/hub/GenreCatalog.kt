/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import java.text.Normalizer
import java.util.Locale

/**
 * Group W (B2): one list of genres for the whole app.
 *
 * Studied (behaviour only, no code; Hikari is GPL-3.0): Hikari joins TMDB's film and series genre names into one
 * list, adds anime themes looked up through TMDB keywords and maps names between the two kinds. Yumeiro's own
 * version: every genre knows its film id, its series id (or the TMDB keyword that stands in when series have no
 * such genre, e.g. Horror) and the names extensions use for it, so "Sci-Fi", "Science Fiction" and "SF" are one
 * genre wherever they appear. Keyword ids were read from the live TMDB API (2026-11-27) and each one checked to
 * return real titles.
 *
 * Genres nobody listed here are not dropped: [learn] counts the names that extensions, manga and novel sources
 * send, and [learned] turns the ones seen often enough into genres of their own (automatic genre creation).
 */
object GenreCatalog {
    enum class Group(val label: String) { MAIN("Genres"), ANIME(tr(R.string.dw_anime_themes, "Anime themes")), THEMES(tr(R.string.dw_themes, "Themes and moods")) }

    /** What the genre is asked for: films, series or anime (Japanese animated series and films). */
    enum class Kind(val label: String) { ALL("All"), MOVIE("Movies"), TV("Series"), ANIME("Anime") }

    data class Genre(
        val key: String,
        val name: String,
        val group: Group,
        /** TMDB film genre id. */
        val movie: Int? = null,
        /** TMDB series genre id. */
        val tv: Int? = null,
        /** TMDB keyword id: used when the kind has no genre id (and for every theme). */
        val keyword: Int? = null,
        val aliases: List<String> = emptyList(),
    ) {
        /** True when [kind] can be asked for at all. */
        fun supports(kind: Kind): Boolean = when (kind) {
            Kind.ALL -> movie != null || tv != null || keyword != null
            Kind.MOVIE -> movie != null || keyword != null
            Kind.TV, Kind.ANIME -> tv != null || keyword != null
        }
    }

    private fun g(key: String, name: String, movie: Int?, tv: Int?, kw: Int? = null, vararg aliases: String) =
        Genre(key, name, Group.MAIN, movie, tv, kw, aliases.toList())
    private fun a(key: String, name: String, kw: Int, vararg aliases: String) = Genre(key, name, Group.ANIME, keyword = kw, aliases = aliases.toList())
    private fun t(key: String, name: String, kw: Int, vararg aliases: String) = Genre(key, name, Group.THEMES, keyword = kw, aliases = aliases.toList())

    val ALL: List<Genre> = listOf(
        g("action", "Action", 28, 10759, null, "action and adventure", "action adventure"),
        g("adventure", "Adventure", 12, 10759, null),
        g("animation", "Animation", 16, 16, null, "animated", "cartoon", "donghua"),
        g("comedy", "Comedy", 35, 35, null, "comedies", "funny", "humor", "humour"),
        g("crime", "Crime", 80, 80, null, "gangster", "police"),
        g("documentary", "Documentary", 99, 99, null, "documentaries", "docuseries", "docu"),
        g("drama", "Drama", 18, 18, null, "dramas"),
        g("family", "Family", 10751, 10751, null),
        g("fantasy", "Fantasy", 14, 10765, null, "sci fi and fantasy"),
        g("history", "History", 36, null, 15126, "historical", "period"),
        g("horror", "Horror", 27, null, 315058, "scary"),
        g("music", "Music", 10402, null, 283297, "musical", "musicals"),
        g("mystery", "Mystery", 9648, 9648, null, "detective", "whodunit"),
        g("romance", "Romance", 10749, null, 9840, "romantic", "love"),
        g("scifi", tr(R.string.science_fiction, "Science Fiction"), 878, 10765, null, "sci fi", "scifi", "sf", "science fiction and fantasy"),
        g("thriller", "Thriller", 53, null, 316362, "suspense", "thrillers"),
        g("war", "War", 10752, 10768, null, "war and politics", "military"),
        g("western", "Western", 37, 37, null, "westerns"),
        g("kids", "Kids", 10751, 10762, null, "children", "kid"),
        g("reality", "Reality", null, 10764, null, "reality tv", "reality show"),
        g("sports", "Sports", null, null, 6075, "sport"),
        g("tvmovie", tr(R.string.tv_movie, "TV Movie"), 10770, null, null),
        g("soap", "Soap", null, 10766, null, "soap opera", "telenovela"),
        g("talk", "Talk", null, 10767, null, "talk show"),
        g("news", "News", null, 10763, null),
        // Anime themes (TMDB keywords; asked for with Animation + Japanese when the kind is Anime).
        a("isekai", "Isekai", 237451, "another world"),
        a("shounen", "Shounen", 207826, "shonen"),
        a("seinen", "Seinen", 195668),
        a("shoujo", "Shoujo", 206437, "shojo"),
        a("josei", "Josei", 229074),
        a("mecha", "Mecha", 10046, "robots", "giant robot"),
        a("magical_girl", tr(R.string.magical_girl, "Magical Girl"), 292887, "mahou shoujo"),
        a("slice_of_life", tr(R.string.slice_of_life, "Slice of Life"), 9914, "slice-of-life"),
        a("iyashikei", "Iyashikei", 291483, "healing"),
        a("school", "School", 10873, "school life"),
        a("high_school", tr(R.string.high_school, "High School"), 6270),
        a("martial_arts", tr(R.string.martial_arts, "Martial Arts"), 779, "kung fu"),
        a("reincarnation", "Reincarnation", 5484, "reincarnated"),
        a("light_novel", tr(R.string.based_on_a_light_novel, "Based on a Light Novel"), 319900, "light novel"),
        a("manga", tr(R.string.based_on_a_manga, "Based on a Manga"), 13141),
        a("idol", "Idols", 287664, "idol"),
        a("harem", "Harem", 9194),
        a("ninja", "Ninja", 10278),
        a("samurai", "Samurai", 1462),
        a("demons", "Demons", 15001, "demon"),
        a("kaiju", "Kaiju", 161791),
        a("cooking", "Cooking", 1918, "gourmet", "food"),
        a("wuxia", "Wuxia", 184656, "xianxia", "cultivation"),
        a("video_game", tr(R.string.video_games, "Video Games"), 282, "game", "games", "gaming"),
        a("vr", tr(R.string.virtual_reality, "Virtual Reality"), 4563, "vrmmo"),
        // Themes and moods (any kind).
        t("superhero", "Superheroes", 9715, "super power", "superpower", "super powers"),
        t("supernatural", "Supernatural", 6152),
        t("dark_fantasy", tr(R.string.dark_fantasy, "Dark Fantasy"), 177895),
        t("magic", "Magic", 2343),
        t("sword_sorcery", tr(R.string.sword_and_sorcery, "Sword and Sorcery"), 234213),
        t("mythology", "Mythology", 2035, "myth", "gods"),
        t("dragons", "Dragons", 12554, "dragon"),
        t("vampires", "Vampires", 3133, "vampire"),
        t("zombies", "Zombies", 12377, "zombie"),
        t("ghosts", "Ghosts", 162846, "ghost"),
        t("witches", "Witches", 616, "witch"),
        t("time_travel", tr(R.string.time_travel, "Time Travel"), 4379),
        t("space_opera", tr(R.string.space_opera, "Space Opera"), 161176, "space"),
        t("cyberpunk", "Cyberpunk", 12190),
        t("post_apocalyptic", "Post-apocalyptic", 4458, "post apocalyptic", "apocalypse"),
        t("dystopia", "Dystopia", 4565, "dystopian"),
        t("survival", "Survival", 10349),
        t("psychological", "Psychological", 12565, "psychological thriller", "psychological horror"),
        t("revenge", "Revenge", 9748),
        t("heist", "Heist", 10051),
        t("serial_killer", tr(R.string.serial_killers, "Serial Killers"), 10714),
        t("conspiracy", "Conspiracy", 10410),
        t("pirates", "Pirates", 12988, "pirate"),
        t("workplace", "Workplace", 6282, "office"),
        t("coming_of_age", tr(R.string.coming_of_age, "Coming of Age"), 10683),
        t("found_family", tr(R.string.found_family, "Found Family"), 248927),
        t("romcom", tr(R.string.romantic_comedy, "Romantic Comedy"), 389245, "rom com", "romcom"),
        t("parody", "Parody", 9755, "spoof"),
        t("gore", "Gore", 10292, "splatter"),
    )

    private val byKey = ALL.associateBy { it.key }
    private val byName: Map<String, Genre> = buildMap {
        // Aliases first, names last: a real name always wins over another genre's alias.
        for (gen in ALL) for (al in gen.aliases) putIfAbsent(normalize(al), gen)
        for (gen in ALL) put(normalize(gen.name), gen)
        for (gen in ALL) put(normalize(gen.key), gen)
    }

    fun byKey(key: String): Genre? = byKey[key]

    /**
     * Pure: a genre name in one comparable form: no accents or case, "&" as "and", punctuation and extra spaces
     * gone, "Sci-Fi" / "Sci Fi" / "scifi" all "sci fi". Unit-tested.
     */
    fun normalize(raw: String): String {
        val s = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val b = StringBuilder(s.length)
        var space = true
        for (c in s) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c == '&' || c == '+' -> { if (!space) b.append(' '); b.append("and "); space = true }
                c.isLetterOrDigit() -> { b.append(c.lowercaseChar()); space = false }
                else -> if (!space) { b.append(' '); space = true }
            }
        }
        return b.toString().trim()
    }

    /** The canonical genre for a name from any source, or null when it is not one of [ALL]. */
    fun match(raw: String): Genre? = byName[normalize(raw)]

    /**
     * Pure: a genre list from a source made tidy: duplicates by meaning removed ("Sci-Fi" and "Science Fiction"
     * once), known genres under their one name, others trimmed and capitalised, junk (numbers, links, very long,
     * adult words) dropped. Every genre list in the app passes through this (online, manga, novel, watch page).
     */
    fun clean(raw: List<String>, max: Int = 12): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (r in raw.asSequence().filterNot { "://" in it || it.startsWith("www.") }.flatMap { it.split(',', ';', '|', '/').asSequence() }) {
            val t = r.trim().trim('#', '·', '-', '.', ' ')
            if (t.length < 2 || t.length > 32 || t.all { it.isDigit() || !it.isLetter() } || "://" in t || adult(t)) continue
            val canon = match(t)
            val name = canon?.name ?: titleCase(t)
            if (seen.add(canon?.key ?: normalize(t))) out += name
            if (out.size >= max) break
        }
        return out
    }

    private val ADULT = setOf("hentai", "porn", "porno", "xxx", "ecchi", "erotic", "erotica", "smut", "yaoi", "yuri", "18", "r18", "nsfw")

    /** "Adult" alone or an adult word; "Young Adult" (a novel genre) is fine. */
    private fun adult(t: String): Boolean { val n = normalize(t); return n == "adult" || n == "mature" || n.split(' ').any { it in ADULT } }

    private fun titleCase(s: String): String =
        if (s.any { it.isUpperCase() } && s.any { it.isLowerCase() }) s
        else s.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    /** Pure: the genres to offer for [kind] (anime themes only where anime is asked for, or for everything). */
    fun forKind(kind: Kind): List<Genre> = ALL.filter { it.supports(kind) && (it.group != Group.ANIME || kind == Kind.ANIME || kind == Kind.ALL) }

    /** Pure: the canonical genre of a TMDB genre id of [kind] ("movie" | "tv"). */
    fun ofTmdb(id: Int, kind: String): Genre? = ALL.firstOrNull { (if (kind == "movie") it.movie else it.tv) == id }

    // region automatic genres

    /** Names seen from sources that are not in [ALL], with how often (kept for the session; saved by [GenreMemory]). */
    private val seenCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val seenNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    const val LEARN_AT = 3
    private const val LEARN_MAX = 400

    /** Counts the genre names a source sent (only unknown, clean ones). */
    fun learn(raw: List<String>) {
        for (name in clean(raw, max = 24)) {
            if (match(name) != null) continue
            val k = normalize(name)
            if (seenCounts.size >= LEARN_MAX && !seenCounts.containsKey(k)) continue
            seenNames.putIfAbsent(k, name)
            seenCounts.merge(k, 1, Int::plus)
        }
    }

    /** Genres made from what sources sent: names seen [LEARN_AT] times or more, most seen first. */
    fun learned(): List<String> = seenCounts.entries.filter { it.value >= LEARN_AT }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .mapNotNull { seenNames[it.key] }

    /** Saved form ("name\tcount" lines) and back, so learned genres survive a restart. */
    fun exportLearned(): String = seenCounts.entries.joinToString("\n") { "${seenNames[it.key] ?: it.key}\t${it.value}" }
    fun importLearned(saved: String?) {
        saved?.lineSequence()?.forEach { line ->
            val name = line.substringBefore('\t').trim(); val n = line.substringAfter('\t', "").toIntOrNull() ?: return@forEach
            if (name.isEmpty() || match(name) != null) return@forEach
            val k = normalize(name); seenNames.putIfAbsent(k, name); seenCounts.merge(k, n, ::maxOf)
        }
    }
    internal fun clearLearned() { seenCounts.clear(); seenNames.clear() }

    // endregion
}
