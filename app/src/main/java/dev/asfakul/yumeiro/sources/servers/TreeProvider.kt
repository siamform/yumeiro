/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

/** A browsable tree of folders and media files (WebDAV, SMB, DLNA). */
interface MediaTree {
    val server: HomeServers.Server

    data class Entry(val path: String, val name: String, val isDir: Boolean, val mime: String? = null, val thumb: String? = null, val size: Long = -1) {
        val isVideo get() = !isDir && (mime?.startsWith("video") == true || VIDEO_EXT.any { name.endsWith(it, true) })
        val isSubtitle get() = !isDir && SUB_EXT.any { name.endsWith(it, true) }
    }

    /** Children of [path]; "" is the root. */
    suspend fun list(path: String): List<Entry>

    /** Playable URL and headers for a file. */
    suspend fun stream(path: String): Pair<String, Map<String, String>>

    fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/', "")

    companion object {
        val VIDEO_EXT = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".wmv", ".flv", ".3gp", ".mpg", ".mpeg", ".m3u8")
        val SUB_EXT = listOf(".srt", ".vtt", ".ass", ".ssa")

        /** Natural sort so "Episode 2" comes before "Episode 10". */
        val natural: Comparator<String> = Comparator { a, b ->
            val re = Regex("""(\d+)|(\D+)""")
            val pa = re.findAll(a.lowercase()).map { it.value }.toList()
            val pb = re.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(pa.size, pb.size)) {
                val x = pa[i]; val y = pb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger()) else x.compareTo(y)
                if (c != 0) return@Comparator c
            }
            pa.size - pb.size
        }
    }
}

/**
 * Turns a [MediaTree] into a source: Home shows the top folders, a folder opens as a series
 * whose episodes are its videos (sub-folders appear as "More like this"), a file opens as a movie.
 */
class TreeProvider(val tree: MediaTree, displayName: String) : MainAPI() {
    override var name = displayName
    override var mainUrl = tree.server.url
    override var lang = "all"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)

    init { sourcePlugin = "yumeiro-bridge:server:" + tree.server.id }

    override val mainPage = listOf(MainPageData(tree.server.name.ifBlank { "Library" }, ""))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
        val root = tree.list(request.data).filter { it.isDir || it.isVideo }
        return newHomePageResponse(request.name, root.sortedWith(compareBy<MediaTree.Entry> { !it.isDir }.thenBy(MediaTree.natural) { it.name }).map { it.toResult() }, false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), false)
        val out = mutableListOf<MediaTree.Entry>()
        val queue = ArrayDeque(listOf("" to 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < 150 && out.size < 80) {
            val (path, depth) = queue.removeFirst()
            visited++
            val kids = runCatching { tree.list(path) }.getOrDefault(emptyList())
            kids.filter { (it.isDir || it.isVideo) && it.name.contains(query, true) }.let(out::addAll)
            if (depth < 4) kids.filter { it.isDir }.forEach { queue.addLast(it.path to depth + 1) }
        }
        return newSearchResponseList(out.map { it.toResult() }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val isDir = url.startsWith("d:")
        val path = url.substring(2)
        val title = path.trimEnd('/').substringAfterLast('/').ifBlank { tree.server.name }.let { java.net.URLDecoder.decode(it, "UTF-8") }
        if (!isDir) return newMovieLoadResponse(title.substringBeforeLast('.'), url, TvType.Movie, path) {}
        val kids = tree.list(path)
        val videos = kids.filter { it.isVideo }.sortedWith(compareBy(MediaTree.natural) { it.name })
        val folders = kids.filter { it.isDir }.sortedWith(compareBy(MediaTree.natural) { it.name })
        val eps = videos.mapIndexed { i, v -> newEpisode(v.path) { name = v.name.substringBeforeLast('.'); episode = i + 1; posterUrl = v.thumb } }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
            plot = listOfNotNull(
                "${videos.size} videos".takeIf { videos.isNotEmpty() },
                "${folders.size} folders".takeIf { folders.isNotEmpty() },
            ).joinToString(" · ").ifBlank { "Empty folder" }
            posterUrl = videos.firstOrNull { it.thumb != null }?.thumb
            recommendations = folders.map { it.toResult() }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val (url, headers) = tree.stream(data)
        val type = if (url.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        callback(newExtractorLink(name, "Original", url, type) { this.headers = headers; quality = 0 })
        // Side-car subtitles: same folder, same base name.
        val base = data.trimEnd('/').substringAfterLast('/').substringBeforeLast('.')
        runCatching { tree.list(tree.parentOf(data)) }.getOrDefault(emptyList())
            .filter { it.isSubtitle && it.name.startsWith(java.net.URLDecoder.decode(base, "UTF-8"), true) }
            .forEach { s -> runCatching { tree.stream(s.path) }.getOrNull()?.let { (u, _) -> subtitleCallback(SubtitleFile(s.name.substringAfter(base).trim('.').ifBlank { "Subtitle" }, u)) } }
        return true
    }

    private fun MediaTree.Entry.toResult(): SearchResponse =
        newMovieSearchResponse(if (isDir) name else name.substringBeforeLast('.'), (if (isDir) "d:" else "f:") + path, if (isDir) TvType.TvSeries else TvType.Movie) {
            posterUrl = thumb
        }
}
