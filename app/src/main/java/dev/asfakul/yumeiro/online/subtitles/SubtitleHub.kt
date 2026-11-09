/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream

internal object SubtitleNet {
    fun readCapped(input: InputStream, max: Long): ByteArray = input.use { s ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = s.read(buf); if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IllegalStateException("Subtitle archive too large")
        }
        out.toByteArray()
    }
}

/**
 * Searches every subtitle site the user has a free key for (OpenSubtitles.com, SubDL, SubSource)
 * at once and merges the results. One site failing (bad key, limit reached) never hides the others.
 */
object SubtitleHub {
    data class Outcome(val hits: List<SubtitleHit>, val errors: Map<String, String>, val providers: List<String>)

    fun configured(): List<String> = buildList {
        if (OpenSubtitles.available()) add("OpenSubtitles")
        if (SubDL.available()) add(SubDL.NAME)
        if (SubSource.available()) add(SubSource.NAME)
    }

    suspend fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): Outcome = coroutineScope {
        val providers = configured()
        val jobs = providers.map { p ->
            async(Dispatchers.IO) {
                p to runCatching {
                    when (p) {
                        SubDL.NAME -> SubDL.search(client, title, imdbId, season, episode, language)
                        SubSource.NAME -> SubSource.search(client, title, imdbId, season, episode, language)
                        else -> OpenSubtitles.search(client, title, imdbId, season, episode, language).map {
                            SubtitleHit("OpenSubtitles", it.fileId.toString(), SubtitleLanguages.normalize(it.language), it.release,
                                it.downloads, it.hearingImpaired)
                        }
                    }
                }
            }
        }.awaitAll()
        val errors = jobs.mapNotNull { (p, r) -> r.exceptionOrNull()?.let { p to (it.message ?: "search failed") } }.toMap()
        Outcome(merge(jobs.map { it.second.getOrDefault(emptyList()) }), errors, providers)
    }

    /** Interleaves the sites' lists (best of each first) so no single site floods the top. */
    internal fun merge(lists: List<List<SubtitleHit>>): List<SubtitleHit> {
        val out = mutableListOf<SubtitleHit>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out.distinctBy { it.key }.take(90)
    }

    /**
     * A URL the player can load for [hit]: OpenSubtitles hands out a temporary https link; SubDL and
     * SubSource send a ZIP, which is unpacked into the app cache (episode picked from season packs).
     */
    suspend fun resolve(context: Context, client: OkHttpClient, hit: SubtitleHit, season: Int?, episode: Int?): String = withContext(Dispatchers.IO) {
        if (hit.provider == "OpenSubtitles") return@withContext OpenSubtitles.link(client, hit.id.toLong())
        val file = fetchToFile(context, client, hit, season, episode)
        Uri.fromFile(file).toString()
    }

    /** Downloads and unpacks [hit] into cacheDir/subtitles; returns the subtitle file. */
    suspend fun fetchToFile(context: Context, client: OkHttpClient, hit: SubtitleHit, season: Int?, episode: Int?): File = withContext(Dispatchers.IO) {
        val bytes = when (hit.provider) {
            SubDL.NAME -> SubDL.download(client, hit)
            SubSource.NAME -> SubSource.download(client, hit)
            else -> throw IllegalArgumentException("Unknown subtitle site")
        }
        val entry = SubtitleArchive.pick(bytes, season, episode)
            ?: throw IllegalStateException("No text subtitle in this download (try another one)")
        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val safeId = hit.id.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(60)
        val file = File(dir, "${hit.provider.lowercase()}_${safeId}_${episode ?: 0}.${entry.extension}")
        file.writeText(entry.text, Charsets.UTF_8)
        file
    }
}
