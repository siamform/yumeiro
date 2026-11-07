/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/** Technical + tag metadata for the Now Playing "Info" panel. All fields optional. */
data class TrackDetails(
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val format: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val path: String? = null,
    val fileName: String? = null,
)

/**
 * Loads lyrics and metadata for a track. Everything runs on [Dispatchers.IO] and every failure is
 * swallowed (returns null) so a broken/odd file can never crash the player. Needs no permissions
 * beyond the existing READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE; imported lyrics come from the
 * system file picker (SAF) and are copied into app-private storage.
 */
object TrackDetailsRepository {
    private const val TAG = "TrackDetails"
    private const val MAX_LRC_BYTES = 1L * 1024 * 1024

    private fun importedFile(context: Context, trackId: String): File =
        File(File(context.filesDir, "lyrics"), trackId.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".lrc")

    suspend fun loadLyrics(context: Context, trackId: String, uri: String): Lyrics? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        // 1. User-imported lyrics override everything.
        runCatching {
            val f = importedFile(app, trackId)
            if (f.isFile && f.length() in 1..MAX_LRC_BYTES) {
                LrcParser.parse(f.readText()).takeIf { it.isNotEmpty() }?.let { return@withContext Lyrics(it, LyricsSource.IMPORTED) }
            }
        }
        // 2. Sidecar .lrc next to the audio file (readable where storage permission allows it).
        runCatching {
            val path = resolvePath(app, uri)
            if (path != null) {
                val base = path.substringBeforeLast('.', path)
                for (ext in listOf(".lrc", ".LRC", ".txt")) {
                    val f = File(base + ext)
                    if (f.isFile && f.canRead() && f.length() in 1..MAX_LRC_BYTES) {
                        val lines = LrcParser.parse(f.readText())
                        if (lines.isNotEmpty()) return@withContext Lyrics(lines, LyricsSource.SIDECAR)
                    }
                }
            }
        }
        // 3. Lyrics embedded in the tags.
        runCatching {
            app.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val raw = EmbeddedLyrics.read(fis.channel)
                    val lines = LrcParser.parse(raw)
                    if (lines.isNotEmpty()) return@withContext Lyrics(lines, LyricsSource.EMBEDDED)
                }
            }
        }.onFailure { Log.d(TAG, "embedded lyrics: ${it.javaClass.simpleName}") }
        null
    }

    /** Copies a user-picked .lrc/.txt into private storage for this track. Returns true on success. */
    suspend fun importLyrics(context: Context, trackId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            val text = app.contentResolver.openInputStream(source)?.use { input ->
                val bytes = input.readBytes(limit = MAX_LRC_BYTES.toInt())
                String(bytes, Charsets.UTF_8)
            } ?: return@runCatching false
            if (LrcParser.parse(text).isEmpty()) return@runCatching false
            val f = importedFile(app, trackId)
            f.parentFile?.mkdirs()
            f.writeText(text)
            true
        }.getOrDefault(false)
    }

    suspend fun removeImportedLyrics(context: Context, trackId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { importedFile(context.applicationContext, trackId).delete() }.getOrDefault(false)
    }

    suspend fun loadDetails(context: Context, uri: String): TrackDetails = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        // Streamed tracks (YouTube Music) have no local file to inspect.
        if (!uri.startsWith("content:") && !uri.startsWith("file:") && !uri.startsWith("/")) return@withContext TrackDetails(path = null, fileName = null)
        val path = runCatching { resolvePath(app, uri) }.getOrNull()
        var d = TrackDetails(path = path, fileName = path?.substringAfterLast('/'))
        val mmr = runCatching { MediaMetadataRetriever() }.getOrNull() ?: return@withContext d
        try {
            mmr.setDataSource(app, Uri.parse(uri))
            fun k(key: Int) = runCatching { mmr.extractMetadata(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val mime = k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            d = d.copy(
                albumArtist = k(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                composer = k(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                genre = k(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(::cleanGenre),
                year = k(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it != "0" }
                    ?: k(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.takeIf { it.all(Char::isDigit) && it != "1904" },
                trackNumber = k(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                discNumber = k(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                format = formatName(mime, path),
                bitrateKbps = k(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { (it + 500) / 1000 }?.takeIf { it > 0 },
                sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    k(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.takeIf { it > 0 } else null,
                bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    k(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it > 0 } else null,
            )
        } catch (t: Throwable) {
            Log.d(TAG, "metadata: ${t.javaClass.simpleName}")
            if (d.format == null) d = d.copy(format = formatName(null, path))
        } finally {
            runCatching { mmr.release() }
        }
        d
    }

    @Suppress("DEPRECATION")
    private fun resolvePath(context: Context, uri: String): String? {
        val u = Uri.parse(uri)
        if (u.scheme == "file") return u.path
        if (u.scheme != "content") return null
        return context.contentResolver.query(u, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (i >= 0 && !c.isNull(i)) c.getString(i)?.takeIf { it.isNotBlank() } else null
            } else null
        }
    }

    private fun cleanGenre(g: String): String {
        // ID3v1-style "(17)" numeric genres are shown as-is minus the parentheses.
        return g.removeSurrounding("(", ")")
    }

    private fun formatName(mime: String?, path: String?): String? {
        val ext = path?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return when {
            mime == null && ext.isEmpty() -> null
            mime?.contains("flac") == true || ext == "flac" -> "FLAC"
            mime?.contains("mpeg") == true || ext == "mp3" -> "MP3"
            mime?.contains("opus") == true || ext == "opus" -> "Opus"
            mime?.contains("ogg") == true || ext == "ogg" -> "OGG Vorbis"
            mime?.contains("wav") == true || ext == "wav" -> "WAV"
            ext == "m4a" || ext == "aac" || mime?.contains("mp4") == true || mime?.contains("aac") == true -> "AAC / M4A"
            ext.isNotEmpty() -> ext.uppercase(Locale.US)
            else -> mime
        }
    }
}

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val r = read(buf, 0, minOf(buf.size, limit - total))
        if (r <= 0) break
        out.write(buf, 0, r); total += r
    }
    return out.toByteArray()
}
