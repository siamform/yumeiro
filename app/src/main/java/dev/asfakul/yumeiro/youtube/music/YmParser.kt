/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads YouTube Music web responses (browse, search, continuations) into [YmPage]s.
 *
 * One reader for every page: Home, Explore, Charts, Moods, an album, a playlist, an artist and
 * search all use the same handful of shelf and item shapes, so each shape is read in exactly one
 * place. Unknown shapes are skipped, never fatal: YouTube changes its layout often and a page
 * with one shelf missing is better than an error. Pure Kotlin, unit-tested on recorded shapes.
 */
object YmParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): YmPage = page(json.parseToJsonElement(body).obj ?: return YmPage())

    fun page(root: JsonObject): YmPage {
        val shelves = mutableListOf<YmShelf>()
        var chips = emptyList<YmChip>()
        var continuation: String? = null
        var header: YmHeader? = root["header"].obj?.let(::header)

        // Continuations (Home's next shelves, a playlist's next songs, search's next page).
        root["continuationContents"].obj?.let { cc ->
            cc["sectionListContinuation"].obj?.let { s ->
                s["contents"].arr?.forEach { shelf(it.obj)?.let(shelves::add) }
                continuation = nextContinuation(s)
            }
            (cc["musicShelfContinuation"].obj ?: cc["musicPlaylistShelfContinuation"].obj)?.let { s ->
                shelves += YmShelf("", items = items(s["contents"].arr), style = YmShelf.Style.LIST)
                continuation = nextContinuation(s) ?: continuationItem(s["contents"].arr)
            }
        }
        root["onResponseReceivedActions"].arr?.forEach { a ->
            a.obj?.get("appendContinuationItemsAction").obj?.get("continuationItems").arr?.let { list ->
                shelves += YmShelf("", items = items(list), style = YmShelf.Style.LIST)
                continuation = continuationItem(list)
            }
        }

        val contents = root["contents"].obj
        // singleColumn (Home, Explore, artist), twoColumn (album, playlist), tabbedSearch (search).
        val tabs = contents?.get("singleColumnBrowseResultsRenderer").obj?.get("tabs").arr
            ?: contents?.get("tabbedSearchResultsRenderer").obj?.get("tabs").arr
        val two = contents?.get("twoColumnBrowseResultsRenderer").obj
        val sectionLists = buildList {
            tabs?.firstOrNull()?.obj?.get("tabRenderer").obj?.get("content").obj?.get("sectionListRenderer").obj?.let(::add)
            two?.get("tabs").arr?.firstOrNull()?.obj?.get("tabRenderer").obj?.get("content").obj?.get("sectionListRenderer").obj?.let(::add)
            two?.get("secondaryContents").obj?.get("sectionListRenderer").obj?.let(::add)
        }
        for (sl in sectionLists) {
            sl["header"].obj?.get("chipCloudRenderer").obj?.let { chips = chips(it) }
            sl["contents"].arr?.forEach { el ->
                val o = el.obj ?: return@forEach
                // Album/playlist headers sit inside the first section list.
                o["musicResponsiveHeaderRenderer"].obj?.let { header = responsiveHeader(it); return@forEach }
                o["musicEditablePlaylistDetailHeaderRenderer"].obj?.get("header").obj?.get("musicResponsiveHeaderRenderer").obj
                    ?.let { header = responsiveHeader(it); return@forEach }
                // Newer "All" search: each result row in its own itemSectionRenderer. Group them.
                o["itemSectionRenderer"].obj?.let { sec ->
                    val rows = items(sec["contents"].arr)
                    val last = shelves.lastOrNull()
                    if (last != null && last.title == "Results" && last.style == YmShelf.Style.LIST) {
                        shelves[shelves.lastIndex] = last.copy(items = last.items + rows)
                    } else if (rows.isNotEmpty()) shelves += YmShelf("Results", items = rows, style = YmShelf.Style.LIST)
                    return@forEach
                }
                shelf(o)?.let(shelves::add)
                if (continuation == null) o["musicPlaylistShelfRenderer"].obj?.let { continuation = continuationItem(it["contents"].arr) ?: nextContinuation(it) }
                if (continuation == null) o["musicShelfRenderer"].obj?.let { continuation = nextContinuation(it) }
            }
            if (continuation == null) continuation = nextContinuation(sl)
        }
        return YmPage(header, shelves.filter { it.items.isNotEmpty() }, chips, continuation)
    }

    // ---- shelves ---------------------------------------------------------------------------

    private fun shelf(o: JsonObject?): YmShelf? {
        o ?: return null
        o["musicCarouselShelfRenderer"].obj?.let { c ->
            val h = c["header"].obj?.get("musicCarouselShelfBasicHeaderRenderer").obj
            val list = items(c["contents"].arr)
            // Quick picks / Top songs come as song rows inside a carousel: keep them as a list.
            val songRows = c["contents"].arr?.firstOrNull()?.obj?.containsKey("musicResponsiveListItemRenderer") == true
            return YmShelf(
                title = text(h?.get("title")).orEmpty(),
                strapline = text(h?.get("strapline")),
                items = list,
                style = if (songRows) YmShelf.Style.LIST else YmShelf.Style.CAROUSEL,
                more = browse(h?.get("moreContentButton").obj?.get("buttonRenderer").obj?.get("navigationEndpoint"))
                    ?: browse(h?.get("title").obj?.get("runs").arr?.firstOrNull()?.obj?.get("navigationEndpoint")),
            )
        }
        o["musicShelfRenderer"].obj?.let { s ->
            return YmShelf(
                title = text(s["title"]).orEmpty(),
                items = items(s["contents"].arr),
                style = YmShelf.Style.LIST,
                more = browse(s["bottomEndpoint"]) ?: browse(s["title"].obj?.get("runs").arr?.firstOrNull()?.obj?.get("navigationEndpoint")),
            )
        }
        o["musicPlaylistShelfRenderer"].obj?.let { s ->
            return YmShelf("", items = items(s["contents"].arr), style = YmShelf.Style.LIST)
        }
        o["gridRenderer"].obj?.let { g ->
            val title = text(g["header"].obj?.get("gridHeaderRenderer").obj?.get("title")).orEmpty()
            val list = items(g["items"].arr)
            val moods = list.isNotEmpty() && list.all { it is YmItem.Mood }
            return YmShelf(title, items = list, style = if (moods) YmShelf.Style.GRID else YmShelf.Style.CAROUSEL)
        }
        o["musicCardShelfRenderer"].obj?.let { c ->
            // Search's "Top result" card.
            val item = cardItem(c) ?: return null
            return YmShelf(text(c["header"].obj?.get("musicCardShelfHeaderBasicRenderer").obj?.get("title")) ?: "Top result", items = listOf(item), style = YmShelf.Style.LIST)
        }
        return null
    }

    private fun items(arr: JsonArray?): List<YmItem> = arr.orEmpty().mapNotNull { item(it.obj) }.distinctBy { it.key }

    private fun item(o: JsonObject?): YmItem? {
        o ?: return null
        o["musicResponsiveListItemRenderer"].obj?.let { return responsive(it) }
        o["musicTwoRowItemRenderer"].obj?.let { return twoRow(it) }
        o["musicNavigationButtonRenderer"].obj?.let { b ->
            val target = browse(b["clickCommand"]) ?: return null
            val title = text(b["buttonText"]) ?: return null
            return YmItem.Mood(title, target, b["solid"].obj?.get("leftStripeColor").long)
        }
        o["musicMultiRowListItemRenderer"].obj?.let { r ->
            val id = watchId(r["onTap"]) ?: return null
            return YmItem.Song(id, text(r["title"]) ?: return null, listOfNotNull(text(r["subtitle"])?.let { YmRef(it) }), thumbnail = thumb(r["thumbnail"]))
        }
        return null
    }

    /** A song row (Quick picks, Top songs, a playlist or album track, a search result). */
    private fun responsive(r: JsonObject): YmItem? {
        val cols = r["flexColumns"].arr.orEmpty().map { it.obj?.get("musicResponsiveListItemFlexColumnRenderer").obj?.get("text") }
        val titleRuns = runs(cols.getOrNull(0))
        val title = titleRuns.joinToString("") { it.first }.ifBlank { return null }
        val thumbnail = thumb(r["thumbnail"])
        val sub = cols.drop(1).flatMap { runs(it) }
        val videoId = r["playlistItemData"].obj?.get("videoId").str
            ?: watchId(titleRuns.firstNotNullOfOrNull { it.second })
            ?: watchId(r["overlay"].obj?.get("musicItemThumbnailOverlayRenderer").obj?.get("content").obj
                ?.get("musicPlayButtonRenderer").obj?.get("playNavigationEndpoint"))
        if (videoId == null) {
            // Not playable: an album, artist or playlist in a list (search results).
            val target = browse(r["navigationEndpoint"]) ?: return null
            return YmItem.Collection(target, title, sub.joinToString("") { it.first }.trim(), thumbnail, kindOf(r["navigationEndpoint"]))
        }
        val artists = sub.filter { pageType(it.second) == "MUSIC_PAGE_TYPE_ARTIST" || pageType(it.second) == "MUSIC_PAGE_TYPE_USER_CHANNEL" }
            .map { YmRef(it.first, browse(it.second)?.browseId) }
            .ifEmpty { plainArtists(sub) }
        val album = sub.firstOrNull { pageType(it.second) == "MUSIC_PAGE_TYPE_ALBUM" }?.let { YmRef(it.first, browse(it.second)?.browseId) }
        val fixed = r["fixedColumns"].arr?.firstOrNull()?.obj?.get("musicResponsiveListItemFixedColumnRenderer").obj?.get("text")
        val duration = (text(fixed) ?: sub.map { it.first }.lastOrNull { DURATION.matches(it.trim()) })?.let(::seconds) ?: 0
        val videoType = musicVideoType(titleRuns.firstNotNullOfOrNull { it.second })
            ?: musicVideoType(r["overlay"].obj?.get("musicItemThumbnailOverlayRenderer").obj?.get("content").obj
                ?.get("musicPlayButtonRenderer").obj?.get("playNavigationEndpoint"))
        val note = sub.map { it.first.trim() }.lastOrNull { it.endsWith("plays") || it.endsWith("views") }
        return YmItem.Song(videoId, title, artists, album, thumbnail, duration, video = videoType != null && videoType != "MUSIC_VIDEO_TYPE_ATV", note = note)
    }

    /** A cover in a carousel: album, playlist, artist, or a music video. */
    private fun twoRow(r: JsonObject): YmItem? {
        val title = text(r["title"]) ?: return null
        val subtitle = text(r["subtitle"]).orEmpty()
        val thumbnail = thumb(r["thumbnailRenderer"])
        val nav = r["navigationEndpoint"]
        watchId(nav)?.let { id ->
            val subRuns = runs(r["subtitle"])
            val artists = subRuns.filter { pageType(it.second) == "MUSIC_PAGE_TYPE_ARTIST" }.map { YmRef(it.first, browse(it.second)?.browseId) }
                .ifEmpty { plainArtists(subRuns) }
            val type = musicVideoType(nav)
            return YmItem.Song(id, title, artists, thumbnail = thumbnail, video = type != null && type != "MUSIC_VIDEO_TYPE_ATV", note = subtitle.substringAfterLast(" • ", "").takeIf { it.endsWith("views") || it.endsWith("plays") })
        }
        val target = browse(nav) ?: return null
        val play = r["thumbnailOverlay"].obj?.get("musicItemThumbnailOverlayRenderer").obj?.get("content").obj
            ?.get("musicPlayButtonRenderer").obj?.get("playNavigationEndpoint").obj
        val playlistId = play?.get("watchPlaylistEndpoint").obj?.get("playlistId").str ?: play?.get("watchEndpoint").obj?.get("playlistId").str
        return YmItem.Collection(target, title, subtitle, thumbnail, kindOf(nav), playlistId)
    }

    private fun cardItem(c: JsonObject): YmItem? {
        val title = text(c["title"]) ?: return null
        val nav = c["title"].obj?.get("runs").arr?.firstOrNull()?.obj?.get("navigationEndpoint") ?: c["onTap"]
        val thumbnail = thumb(c["thumbnail"])
        val subRuns = runs(c["subtitle"])
        watchId(nav)?.let { id ->
            return YmItem.Song(id, title, subRuns.filter { pageType(it.second) == "MUSIC_PAGE_TYPE_ARTIST" }.map { YmRef(it.first, browse(it.second)?.browseId) }, thumbnail = thumbnail, video = musicVideoType(nav).let { it != null && it != "MUSIC_VIDEO_TYPE_ATV" })
        }
        val target = browse(nav) ?: return null
        return YmItem.Collection(target, title, subRuns.joinToString("") { it.first }, thumbnail, kindOf(nav))
    }

    // ---- headers ---------------------------------------------------------------------------

    private fun header(h: JsonObject): YmHeader? {
        h["musicImmersiveHeaderRenderer"].obj?.let { r ->
            return YmHeader(
                title = text(r["title"]).orEmpty(),
                description = text(r["description"]).orEmpty(),
                thumbnail = thumb(r["thumbnail"]),
                playlistId = r["startRadioButton"].obj?.get("buttonRenderer").obj?.get("navigationEndpoint").obj
                    ?.get("watchPlaylistEndpoint").obj?.get("playlistId").str
                    ?: r["playButton"].obj?.get("buttonRenderer").obj?.get("navigationEndpoint").obj?.get("watchEndpoint").obj?.get("playlistId").str,
                subscribers = text(r["subscriptionButton"].obj?.get("subscribeButtonRenderer").obj?.get("subscriberCountText")),
                artist = true,
            )
        }
        h["musicVisualHeaderRenderer"].obj?.let { r -> return YmHeader(text(r["title"]).orEmpty(), thumbnail = thumb(r["foregroundThumbnail"]) ?: thumb(r["thumbnail"]), artist = true) }
        h["musicHeaderRenderer"].obj?.let { r -> return YmHeader(text(r["title"]).orEmpty()) }
        h["musicDetailHeaderRenderer"].obj?.let { r ->
            return YmHeader(text(r["title"]).orEmpty(), text(r["subtitle"]).orEmpty(), text(r["secondSubtitle"]).orEmpty(), text(r["description"]).orEmpty(), thumb(r["thumbnail"]))
        }
        return null
    }

    private fun responsiveHeader(r: JsonObject): YmHeader {
        val play = r["buttons"].arr?.firstNotNullOfOrNull { b ->
            b.obj?.get("musicPlayButtonRenderer").obj?.get("playNavigationEndpoint").obj?.let {
                it["watchEndpoint"].obj?.get("playlistId").str ?: it["watchPlaylistEndpoint"].obj?.get("playlistId").str
            }
        }
        return YmHeader(
            title = text(r["title"]).orEmpty(),
            subtitle = listOfNotNull(text(r["straplineTextOne"]), text(r["subtitle"])).joinToString(" • "),
            secondSubtitle = text(r["secondSubtitle"]).orEmpty(),
            description = text(r["description"].obj?.get("musicDescriptionShelfRenderer").obj?.get("description")).orEmpty(),
            thumbnail = thumb(r["thumbnail"]),
            playlistId = play,
        )
    }

    private fun chips(c: JsonObject): List<YmChip> = c["chips"].arr.orEmpty().mapNotNull { el ->
        val r = el.obj?.get("chipCloudChipRenderer").obj ?: return@mapNotNull null
        val t = text(r["text"]) ?: return@mapNotNull null
        val b = browse(r["navigationEndpoint"]) ?: return@mapNotNull null
        YmChip(t, b, r["isSelected"].bool == true)
    }

    // ---- small readers ---------------------------------------------------------------------

    private val DURATION = Regex("""^\d{1,2}(:\d{2}){1,2}$""")

    internal fun seconds(text: String): Long {
        val t = text.trim()
        if (!DURATION.matches(t)) return 0
        return t.split(':').fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) }
    }

    /** Artist names from plain subtitle text ("Song • Artist • Album • 3:20") when nothing is linked. */
    private fun plainArtists(sub: List<Pair<String, JsonElement?>>): List<YmRef> {
        val parts = sub.joinToString("") { it.first }.split(" • ").map { it.trim() }.filter { it.isNotBlank() }
        val skip = setOf("Song", "Video", "Episode", "Single", "EP", "Album", "Playlist")
        return parts.firstOrNull { it !in skip && !DURATION.matches(it) && !it.endsWith("views") && !it.endsWith("plays") }
            ?.let { listOf(YmRef(it)) }.orEmpty()
    }

    private fun kindOf(nav: JsonElement?): YmItem.Collection.Kind = when (pageType(nav)) {
        "MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK" -> YmItem.Collection.Kind.ALBUM
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL", "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" -> YmItem.Collection.Kind.ARTIST
        "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE", "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE" -> YmItem.Collection.Kind.PODCAST
        "MUSIC_PAGE_TYPE_PLAYLIST" -> YmItem.Collection.Kind.PLAYLIST
        else -> YmItem.Collection.Kind.OTHER
    }

    private fun browse(nav: JsonElement?): YmBrowse? {
        val b = nav.obj?.get("browseEndpoint").obj ?: return null
        val id = b["browseId"].str ?: return null
        return YmBrowse(id, b["params"].str)
    }

    private fun pageType(nav: JsonElement?): String? = nav.obj?.get("browseEndpoint").obj?.get("browseEndpointContextSupportedConfigs").obj
        ?.get("browseEndpointContextMusicConfig").obj?.get("pageType").str

    private fun watchId(nav: JsonElement?): String? = nav.obj?.get("watchEndpoint").obj?.get("videoId").str

    private fun musicVideoType(nav: JsonElement?): String? = nav.obj?.get("watchEndpoint").obj?.get("watchEndpointMusicSupportedConfigs").obj
        ?.get("watchEndpointMusicConfig").obj?.get("musicVideoType").str

    private fun nextContinuation(o: JsonObject): String? = o["continuations"].arr?.firstOrNull()?.obj?.let {
        it["nextContinuationData"].obj?.get("continuation").str ?: it["reloadContinuationData"].obj?.get("continuation").str
    }

    private fun continuationItem(arr: JsonArray?): String? = arr?.lastOrNull()?.obj?.get("continuationItemRenderer").obj
        ?.get("continuationEndpoint").obj?.get("continuationCommand").obj?.get("token").str

    /** Largest thumbnail, upscaled when the CDN can resize (googleusercontent / ggpht). */
    internal fun thumb(el: JsonElement?): String? {
        val o = el.obj ?: return null
        val list = (o["musicThumbnailRenderer"].obj ?: o["croppedSquareThumbnailRenderer"].obj ?: o)["thumbnail"].obj?.get("thumbnails").arr
            ?: o["thumbnails"].arr ?: return null
        val url = list.lastOrNull()?.obj?.get("url").str ?: return null
        val abs = if (url.startsWith("//")) "https:$url" else url
        return if ("googleusercontent.com" in abs || "ggpht.com" in abs) abs.replace(Regex("=w\\d+-h\\d+[^&?]*$"), "=w544-h544-l90-rj") else abs
    }

    private fun runs(el: JsonElement?): List<Pair<String, JsonElement?>> =
        el.obj?.get("runs").arr.orEmpty().mapNotNull { r -> r.obj?.let { (it["text"].str ?: return@mapNotNull null) to it["navigationEndpoint"] } }

    internal fun text(el: JsonElement?): String? {
        val o = el.obj ?: return null
        o["simpleText"].str?.let { return it }
        return o["runs"].arr?.joinToString("") { it.obj?.get("text").str.orEmpty() }?.takeIf { it.isNotBlank() }
    }

    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private val JsonElement?.arr: JsonArray? get() = this as? JsonArray
    private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private val JsonElement?.long: Long? get() = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    private val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
