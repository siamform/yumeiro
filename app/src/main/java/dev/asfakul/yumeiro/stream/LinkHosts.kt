/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLEncoder

/**
 * Cloud-drive and file-host links people paste into Live TV / "Play a link".
 *
 *  - **Google Drive / Dropbox** public share links are rewritten to their direct download address,
 *    so they play (and download) like any MP4 / MKV link. Private files still need the owner's
 *    permission — nothing is bypassed.
 *  - **TeraBox / DiskWala** only hand the video to their own web player after the page runs, so
 *    those open in Yumeiro's page browser, where the stream the page plays is picked up.
 *  - **Magnet / .torrent** links stream through a TorrServer the user runs (on the phone or on the
 *    local network). Yumeiro has no torrent engine of its own; without a server the link is handed
 *    to an installed torrent app.
 *
 * The classification is pure (unit-tested); [TorrentSettings] holds the optional server address.
 */
object LinkHosts {
    sealed interface Route
    /** Play / download [url] directly (rewritten share link). */
    data class Direct(val url: String, val host: String) : Route
    /** Open the page in the page browser; [reason] is shown to the user. */
    data class Browser(val pageUrl: String, val host: String, val reason: String) : Route
    /** A magnet or .torrent link. */
    data class Torrent(val link: String) : Route

    private val TERABOX = listOf(
        "terabox.com", "1024terabox.com", "teraboxapp.com", "terabox.app", "freeterabox.com", "terasharelink.com",
        "teraboxlink.com", "terafileshare.com", "nephobox.com", "4funbox.com", "4funbox.co", "mirrobox.com",
        "momerybox.com", "tibibox.com", "1024tera.com", "teraboxshare.com",
    )
    private val DISKWALA = listOf("diskwala.com", "diskwala.in", "diskwala.app")

    private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    private fun under(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun isTorrent(url: String): Boolean {
        val u = url.trim()
        if (u.startsWith("magnet:?", ignoreCase = true)) return true
        return u.startsWith("http", ignoreCase = true) && u.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true)
    }

    /** The file id of a Google Drive share link, or null. */
    fun googleDriveId(url: String): String? {
        val host = hostOf(url) ?: return null
        if (!under(host, "drive.google.com") && !under(host, "docs.google.com") && !under(host, "drive.usercontent.google.com")) return null
        Regex("/(?:file/)?d/([A-Za-z0-9_-]{10,})").find(url)?.let { return it.groupValues[1] }
        Regex("[?&]id=([A-Za-z0-9_-]{10,})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    /** Direct download address of a public Drive file (`confirm=t` skips the large-file scan page). */
    fun googleDriveDirect(id: String): String = "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"

    /** Dropbox share links → raw file. */
    fun dropboxDirect(url: String): String? {
        val host = hostOf(url) ?: return null
        if (!under(host, "dropbox.com")) return null
        if (!url.contains("/s/") && !url.contains("/scl/")) return null
        val clean = url.replace(Regex("([?&])(dl|raw)=[01]"), "$1").replace("?&", "?").trimEnd('?', '&')
        return clean + (if ('?' in clean) "&" else "?") + "raw=1"
    }

    fun classify(url: String): Route? {
        val u = url.trim()
        if (isTorrent(u)) return Torrent(u)
        val host = hostOf(u) ?: return null
        googleDriveId(u)?.let { return Direct(googleDriveDirect(it), "Google Drive") }
        dropboxDirect(u)?.let { return Direct(it, "Dropbox") }
        if (TERABOX.any { under(host, it) }) return Browser(u, "TeraBox",
            "TeraBox only gives the video to its own web player. The page opens here — start the video and Yumeiro picks up the stream. " +
                "Some files need you to sign in to TeraBox first.")
        if (DISKWALA.any { under(host, it) }) return Browser(u, "DiskWala",
            "DiskWala loads the video in its own web player. The page opens here — start the video and Yumeiro picks up the stream.")
        return null
    }

    /**
     * TorrServer address for [link]: `m3u` lists every file of the torrent (a series pack turns into
     * a playlist), `play` streams file [index] directly. See github.com/YouROK/TorrServer (`/stream`).
     */
    fun torrServerUrl(server: String, link: String, title: String? = null, m3u: Boolean = true, index: Int = 1): String {
        val base = server.trim().trimEnd('/').let { if (it.startsWith("http", true)) it else "http://$it" }
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val name = enc((title?.takeIf { it.isNotBlank() } ?: "stream").take(60))
        return "$base/stream/$name?link=${enc(link.trim())}&index=$index&" + (if (m3u) "m3u" else "play") +
            (title?.takeIf { it.isNotBlank() }?.let { "&title=" + enc(it) } ?: "")
    }
}

/** Optional TorrServer address (e.g. `http://127.0.0.1:8090`). Empty = hand torrents to another app. */
object TorrentSettings {
    private const val PREFS = "yu_torrent"
    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _server.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("server", "").orEmpty()
    }

    /** Keeps only http(s) host[:port] addresses; returns false when [value] isn't one. */
    fun setServer(context: Context, value: String): Boolean {
        val v = value.trim().trimEnd('/')
        val ok = v.isEmpty() || normalize(v) != null
        if (!ok) return false
        val n = if (v.isEmpty()) "" else normalize(v)!!
        _server.value = n
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("server", n).apply()
        return true
    }

    fun normalize(v: String): String? {
        val withScheme = if (v.startsWith("http://", true) || v.startsWith("https://", true)) v else "http://$v"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return "${uri.scheme.lowercase()}://${uri.host}" + (if (uri.port > 0) ":${uri.port}" else "")
    }
}
