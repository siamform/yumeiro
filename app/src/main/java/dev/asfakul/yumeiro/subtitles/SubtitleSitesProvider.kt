/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles

import android.content.Context
import android.net.Uri
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.subtitles.SubDL
import dev.asfakul.yumeiro.online.subtitles.SubSource
import dev.asfakul.yumeiro.online.subtitles.SubtitleHit
import dev.asfakul.yumeiro.online.subtitles.SubtitleHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SubDL / SubSource for the local video player (searches by the file's cleaned title; "S01E02"
 * in the name narrows it to the episode). Only active when the user saved a free key for the site.
 */
class SubtitleSitesProvider(private val context: Context, private val site: String) : SubtitleProvider {
    override val name = site

    fun available(): Boolean = when (site) { SubDL.NAME -> SubDL.available(); SubSource.NAME -> SubSource.available(); else -> false }

    override suspend fun search(query: String, languageCode3: String, hash: MovieHash?): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        if (!available()) return@withContext emptyList()
        val (title, season, episode) = splitEpisode(query)
        val client = NetworkClient.get(context)
        val hits = when (site) {
            SubDL.NAME -> SubDL.search(client, title, null, season, episode, languageCode3)
            else -> SubSource.search(client, title, null, season, episode, languageCode3)
        }
        hits.map { h ->
            OnlineSubtitle(
                id = h.id, fileName = h.release, language = h.language.uppercase(), languageCode = h.language, format = "srt",
                downloads = h.downloads, release = h.release, movieName = title, hashMatch = false,
                downloadUrl = "", encoding = null, provider = site,
            )
        }
    }

    override suspend fun download(context: Context, subtitle: OnlineSubtitle): LocalSubtitle = withContext(Dispatchers.IO) {
        val (_, season, episode) = splitEpisode(subtitle.movieName + " " + subtitle.release)
        val hit = SubtitleHit(site, subtitle.id, subtitle.languageCode, subtitle.release)
        val file = SubtitleHub.fetchToFile(context, NetworkClient.get(context), hit, season, episode)
        LocalSubtitle(Uri.fromFile(file), mimeFor(file.extension), subtitle.languageCode, "${subtitle.language} · $site")
    }

    companion object {
        /** "Show Name S02E05" → ("Show Name", 2, 5); no marker → (query, null, null). */
        fun splitEpisode(query: String): Triple<String, Int?, Int?> {
            val m = Regex("""(?i)\bs(\d{1,2})[ ._-]*e(\d{1,4})\b""").find(query) ?: Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b""").find(query)
                ?: return Triple(query.trim(), null, null)
            return Triple(query.substring(0, m.range.first).trim().ifBlank { query.trim() }, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}
