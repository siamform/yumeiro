/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

data class SubtitleLanguage(val code3: String, val name: String)

val SUBTITLE_LANGUAGES = listOf(
    SubtitleLanguage("eng", "English"), SubtitleLanguage("ben", "Bengali"), SubtitleLanguage("hin", "Hindi"),
    SubtitleLanguage("ara", "Arabic"), SubtitleLanguage("urd", "Urdu"), SubtitleLanguage("spa", "Spanish"),
    SubtitleLanguage("fre", "French"), SubtitleLanguage("ger", "German"), SubtitleLanguage("ita", "Italian"),
    SubtitleLanguage("por", "Portuguese"), SubtitleLanguage("pob", "Portuguese (BR)"), SubtitleLanguage("rus", "Russian"),
    SubtitleLanguage("tur", "Turkish"), SubtitleLanguage("ind", "Indonesian"), SubtitleLanguage("may", "Malay"),
    SubtitleLanguage("jpn", "Japanese"), SubtitleLanguage("kor", "Korean"), SubtitleLanguage("chi", "Chinese"),
    SubtitleLanguage("per", "Persian"), SubtitleLanguage("tha", "Thai"), SubtitleLanguage("vie", "Vietnamese"),
)

data class OnlineSubtitle(
    val id: String,
    val fileName: String,
    val language: String,
    val languageCode: String,
    val format: String,
    val downloads: Int,
    val release: String,
    val movieName: String,
    val hashMatch: Boolean,
    val downloadUrl: String,
    val encoding: String?,
    val provider: String,
)

/** A downloaded subtitle file ready for ExoPlayer. */
data class LocalSubtitle(val uri: Uri, val mimeType: String, val language: String, val label: String)

interface SubtitleProvider {
    val name: String
    suspend fun search(query: String, languageCode3: String, hash: MovieHash?): List<OnlineSubtitle>
    suspend fun download(context: Context, subtitle: OnlineSubtitle): LocalSubtitle
}

data class MovieHash(val hash: String, val size: Long)

/**
 * OpenSubtitles.org REST search (no account or API key needed).
 * Matching by file hash is tried first for local files, then by title.
 */
class OpenSubtitlesProvider : SubtitleProvider {
    override val name = "OpenSubtitles"
    private val base = "https://rest.opensubtitles.org/search"
    private val ua = "TemporaryUserAgent"

    override suspend fun search(query: String, languageCode3: String, hash: MovieHash?): List<OnlineSubtitle> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<OnlineSubtitle>()
            if (hash != null) {
                results += runCatching {
                    request("$base/moviebytesize-${hash.size}/moviehash-${hash.hash}/sublanguageid-$languageCode3", true)
                }.getOrDefault(emptyList())
            }
            val q = cleanQuery(query)
            if (q.isNotBlank()) {
                results += request("$base/query-${Uri.encode(q.lowercase())}/sublanguageid-$languageCode3", false)
            }
            results.distinctBy { it.id }
                .sortedWith(compareByDescending<OnlineSubtitle> { it.hashMatch }.thenByDescending { it.downloads })
                .take(60)
        }

    private fun request(url: String, hashMatch: Boolean): List<OnlineSubtitle> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("User-Agent", ua)
            setRequestProperty("X-User-Agent", ua)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("Subtitle search failed (HTTP ${conn.responseCode})")
            val arr = JSONArray(conn.inputStream.use { it.readBytes().decodeToString() })
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val format = o.optString("SubFormat").lowercase()
                if (format !in setOf("srt", "vtt", "ass", "ssa")) return@mapNotNull null
                OnlineSubtitle(
                    id = o.optString("IDSubtitleFile"),
                    fileName = o.optString("SubFileName"),
                    language = o.optString("LanguageName"),
                    languageCode = o.optString("ISO639").ifBlank { o.optString("SubLanguageID") },
                    format = format,
                    downloads = o.optString("SubDownloadsCnt").toIntOrNull() ?: 0,
                    release = o.optString("MovieReleaseName"),
                    movieName = o.optString("MovieName"),
                    hashMatch = hashMatch || o.optString("MatchedBy") == "moviehash",
                    downloadUrl = o.optString("SubDownloadLink"),
                    encoding = o.optString("SubEncoding").ifBlank { null },
                    provider = name,
                )
            }.filter { it.downloadUrl.startsWith("https://") }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun download(context: Context, subtitle: OnlineSubtitle): LocalSubtitle = withContext(Dispatchers.IO) {
        val conn = (URL(subtitle.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
        }
        val bytes = try {
            if (conn.responseCode !in 200..299) error("Download failed (HTTP ${conn.responseCode})")
            val raw = conn.inputStream.use { it.readLimited(8 * 1024 * 1024) }
            if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                GZIPInputStream(raw.inputStream()).use { it.readLimited(16 * 1024 * 1024) }
            } else raw
        } finally {
            conn.disconnect()
        }
        val charset = subtitle.encoding?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        val text = String(bytes, charset).removePrefix("\uFEFF")
        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val file = File(dir, "${subtitle.provider}_${subtitle.id}.${subtitle.format}")
        file.writeText(text, Charsets.UTF_8)
        LocalSubtitle(
            uri = Uri.fromFile(file),
            mimeType = mimeFor(subtitle.format),
            language = subtitle.languageCode,
            label = "${subtitle.language} · ${subtitle.provider}",
        )
    }
}

fun mimeFor(extension: String): String = when (extension.lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}

/** Removes release noise so "Movie.Name.2010.1080p.BluRay.x264" searches as "movie name 2010". */
fun cleanQuery(raw: String): String {
    var s = raw.substringBeforeLast('.', raw).takeIf { raw.contains('.') && raw.substringAfterLast('.').length <= 4 } ?: raw
    s = s.replace(Regex("[._]+"), " ")
    s = s.replace(Regex("""(?i)\b(480p|576p|720p|1080p|2160p|4k|uhd|hdr|x264|x265|h264|h265|hevc|web[- ]?dl|webrip|bluray|brrip|dvdrip|hdtv|aac|ac3|dts|yify|yts|rarbg|proper|repack|extended)\b.*"""), "")
    s = s.replace(Regex("""[\[\(].*?[\]\)]"""), " ")
    return s.replace(Regex("\\s+"), " ").trim()
}

private fun InputStream.readLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (true) {
        val n = read(buf); if (n < 0) break
        out.write(buf, 0, n); require(out.size() <= max) { "Subtitle file too large" }
    }
    return out.toByteArray()
}

/** OpenSubtitles hash: file size + 64-bit sums of the first and last 64 KiB. */
object MovieHasher {
    private const val CHUNK = 64 * 1024

    suspend fun compute(context: Context, uri: Uri): MovieHash? = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.scheme != "content" && uri.scheme != "file") return@runCatching null
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size < CHUNK * 2) return@use null
                java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    val head = ByteBuffer.allocate(CHUNK).order(ByteOrder.LITTLE_ENDIAN)
                    ch.read(head, 0); head.flip()
                    val tail = ByteBuffer.allocate(CHUNK).order(ByteOrder.LITTLE_ENDIAN)
                    ch.read(tail, size - CHUNK); tail.flip()
                    var h = size
                    while (head.remaining() >= 8) h += head.long
                    while (tail.remaining() >= 8) h += tail.long
                    MovieHash(String.format("%016x", h), size)
                }
            }
        }.getOrNull()
    }
}
