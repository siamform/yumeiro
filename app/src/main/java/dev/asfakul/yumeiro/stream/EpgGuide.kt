/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.util.Log
import android.util.Xml
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.ui.screens.watch.StreamWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * F-15 (ADR-040): Live TV guide from the playlist's `url-tvg` (XMLTV, plain or gzip). Read as a stream with a
 * pull parser, so a 50 MB guide never sits in memory; only the next [WINDOW_MS] of programmes, at most
 * [PER_CHANNEL] per channel, are kept and saved for [TTL_MS]. Nothing is sent anywhere but the guide's own host.
 */
object EpgGuide {
    private const val TAG = "EpgGuide"
    const val WINDOW_MS = 12L * 60 * 60 * 1000
    const val TTL_MS = 6L * 60 * 60 * 1000
    const val PER_CHANNEL = 8
    /** Downloads larger than this stop (some national guides are hundreds of MB). */
    const val MAX_BYTES = 80L * 1024 * 1024

    data class Programme(val start: Long, val stop: Long, val title: String)

    class Guide(val programmes: Map<String, List<Programme>>, private val names: Map<String, String>, val fetchedAt: Long = 0L) {
        fun idForName(name: String): String? = names[StreamWatch.nameKey(name)]

        /** What is on [ch] at [now] and the programme after it; tvg-id first, then the channel name. */
        fun nowNext(ch: StreamRequest, now: Long): Pair<Programme?, Programme?> {
            val id = ch.tvgId?.takeIf { programmes.containsKey(it) } ?: idForName(ch.displayTitle) ?: return null to null
            val list = programmes[id].orEmpty()
            val i = list.indexOfFirst { now >= it.start && now < it.stop }
            return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
        }

        internal fun toJson(): String = JSONObject().put("at", fetchedAt)
            .put("names", JSONObject(names as Map<*, *>))
            .put("p", JSONObject().apply {
                programmes.forEach { (id, l) -> put(id, JSONArray().apply { l.forEach { put(JSONArray().put(it.start).put(it.stop).put(it.title)) } }) }
            }).toString()

        companion object {
            internal fun fromJson(s: String): Guide {
                val o = JSONObject(s)
                val n = o.getJSONObject("names"); val p = o.getJSONObject("p")
                val names = n.keys().asSequence().associateWith { n.getString(it) }
                val progs = p.keys().asSequence().associateWith { id ->
                    val a = p.getJSONArray(id)
                    (0 until a.length()).map { a.getJSONArray(it).let { e -> Programme(e.getLong(0), e.getLong(1), e.getString(2)) } }
                }
                return Guide(progs, names, o.optLong("at"))
            }
        }
    }

    private val EPG_URL = Regex("""(?:url-tvg|x-tvg-url)="([^"]+)"""")

    /** First guide address in an `#EXTM3U` header (several may be comma separated). */
    fun firstEpgUrl(header: String): String? = EPG_URL.find(header)?.groupValues?.get(1)
        ?.split(',')?.map { it.trim() }?.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }

    /** XMLTV time `yyyyMMddHHmmss [+-]hhmm` → epoch ms (no offset = UTC). */
    fun time(s: String): Long? {
        val t = s.trim()
        if (t.length < 14 || !t.substring(0, 14).all { it.isDigit() }) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(t.substring(0, 4).toInt(), t.substring(4, 6).toInt() - 1, t.substring(6, 8).toInt(),
                t.substring(8, 10).toInt(), t.substring(10, 12).toInt(), t.substring(12, 14).toInt())
        }
        val off = t.substring(14).trim()
        val offMs = if (off.length == 5 && (off[0] == '+' || off[0] == '-') && off.substring(1).all { it.isDigit() }) {
            val m = off.substring(1, 3).toInt() * 60 + off.substring(3, 5).toInt()
            (if (off[0] == '-') -m else m) * 60_000L
        } else 0L
        return cal.timeInMillis - offMs
    }

    fun maybeGunzip(input: InputStream): InputStream {
        val b = if (input.markSupported()) input else BufferedInputStream(input)
        b.mark(2)
        val m1 = b.read(); val m2 = b.read()
        b.reset()
        return if (m1 == 0x1f && m2 == 0x8b) GZIPInputStream(b, 16 * 1024) else b
    }

    /** Programmes that overlap [from, to], per channel id, plus a channel-name → id map. */
    fun parse(input: InputStream, from: Long, to: Long): Guide {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val progs = HashMap<String, ArrayList<Programme>>()
        val names = HashMap<String, String>()
        var chId: String? = null
        var pCh: String? = null; var pStart = 0L; var pStop = 0L; var keep = false
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) when (parser.name) {
                "channel" -> chId = parser.getAttributeValue(null, "id")
                "display-name" -> chId?.let { id -> StreamWatch.nameKey(parser.nextText()).takeIf { it.isNotBlank() }?.let { names.putIfAbsent(it, id) } }
                "programme" -> {
                    pCh = parser.getAttributeValue(null, "channel")
                    pStart = parser.getAttributeValue(null, "start")?.let(::time) ?: 0L
                    pStop = parser.getAttributeValue(null, "stop")?.let(::time) ?: (pStart + 30 * 60_000L)
                    keep = pCh != null && pStart > 0L && pStop > from && pStart < to && (progs[pCh]?.size ?: 0) < PER_CHANNEL
                }
                "title" -> if (keep && pCh != null) {
                    val title = parser.nextText().trim()
                    if (title.isNotEmpty()) progs.getOrPut(pCh) { ArrayList() }.add(Programme(pStart, pStop, title.take(120)))
                    keep = false // first title only
                }
            } else if (ev == XmlPullParser.END_TAG) when (parser.name) {
                "channel" -> chId = null
                "programme" -> { pCh = null; keep = false }
            }
            ev = parser.next()
        }
        return Guide(progs.mapValues { (_, l) -> l.sortedBy { it.start } }, names, System.currentTimeMillis())
    }

    private class Capped(i: InputStream, private val max: Long) : FilterInputStream(i) {
        private var n = 0L
        private fun count(r: Int): Int { if (r > 0) { n += r; if (n > max) throw IOException("guide too large") }; return r }
        override fun read(): Int = super.read().also { if (it >= 0) count(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = count(super.read(b, off, len))
    }

    private val mutex = Mutex()
    @Volatile private var memory: Pair<String, Guide>? = null

    private fun file(context: Context, url: String): File {
        val h = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "epg_$h.json")
    }

    /**
     * The guide for [url]: memory, then the saved copy (under [TTL_MS] old), then a download. Null on failure.
     * On a metered connection (mobile data) only a saved copy is used: guides run to tens of MB.
     */
    suspend fun guide(context: Context, url: String): Guide? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            memory?.takeIf { it.first == url && now - it.second.fetchedAt < TTL_MS }?.let { return@withLock it.second }
            val f = file(context, url)
            if (f.exists() && now - f.lastModified() < TTL_MS) {
                runCatching { Guide.fromJson(f.readText()) }.getOrNull()?.let { memory = url to it; return@withLock it }
            }
            val metered = runCatching { context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered == true }.getOrDefault(true)
            val fresh = if (metered) null else runCatching {
                val req = Request.Builder().url(url).header("Accept", "application/xml,application/gzip,*/*").build()
                NetworkClient.get(context).newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body ?: error("empty")
                    maybeGunzip(Capped(body.byteStream(), MAX_BYTES)).use { parse(it, now - 60 * 60_000L, now + WINDOW_MS) }
                }
            }.onFailure { Log.w(TAG, "guide: ${it.message}") }.getOrNull()
            if (fresh != null) {
                memory = url to fresh
                runCatching { f.writeText(fresh.toJson()) }
            }
            fresh ?: runCatching { if (f.exists()) Guide.fromJson(f.readText()) else null }.getOrNull()
        }
    }
}
