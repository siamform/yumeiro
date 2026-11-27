/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import dev.asfakul.yumeiro.DeviceClass
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.stream.local.LocalStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * B-31 / ADR-053: the built-in torrent player path. One engine and one loopback server for the app's life (no
 * TorrServer, no per-link setup): [open] turns any magnet, bare info-hash or .torrent URL into a local
 * `http://127.0.0.1:<port>/s/<token>/<name>` URL that Yumeiro's player, external players and downloads read like
 * a web file. Only the chosen file downloads; it waits for at most [META_WAIT_MS] for a magnet's metadata.
 * Pieces stay in the app's cache (oldest torrents removed above [cacheBudget]) with resume data, so a replay starts
 * from what is already here. A torrent nobody read for [IDLE_MS] is paused (no seeding in the background).
 */
object TorrentStreams {
    const val META_WAIT_MS = 90_000L
    const val IDLE_MS = 120_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var core: TorrentCore? = null
    internal val active = HashMap<String, TorrentSource>()
    /** TOR-IDLE-1: torrents paused by the idle timer; their source stays open and the next read resumes them. */
    private val idlePaused = HashSet<String>()
    private val idleJobs = HashMap<String, Job>()
    val server: LocalStreamServer get() = LocalStreamServer.shared
    /** Tests swap the core so no native code runs on the JVM. */
    @Volatile internal var coreFactory: (Boolean) -> TorrentCore = { LtCore(it) }

    class Opened(val url: String, val name: String, val size: Long, val hash: String)

    /** Built-in engine plays only when the flag is on, an engine exists for this ABI and the user accepted the notice. */
    fun enabled(context: Context): Boolean {
        dev.asfakul.yumeiro.stream.TorrentSettings.init(context.applicationContext)
        return allowed(offered(context), dev.asfakul.yumeiro.stream.TorrentSettings.engineAccepted.value)
    }

    /** The Settings switch is shown (flag on and an engine for this phone). */
    fun offered(context: Context): Boolean = FeatureFlags.on(context, FeatureFlags.Flag.BUILTIN_TORRENT) && TorrentEngine.available()

    /** Pure: until the privacy notice is accepted, torrents keep going to TorrServer or another app. */
    fun allowed(offered: Boolean, accepted: Boolean): Boolean = offered && accepted

    /** The notice shown before the engine turns on (peers see the IP; pieces are shared while playing). */
    const val PRIVACY_NOTICE =
        tr(R.string.torrents_are_shared_between_the_people, "Torrents are shared between the people who have them. While a torrent plays, other people sharing it can see ") +
            "your IP address, and Yumeiro sends them the parts you already have. Downloading or sharing some files is " +
            "illegal in some countries. Use a VPN if your network or country restricts torrents. Pieces are kept in the " +
            "app's cache and removed when it is full."

    fun cacheBudget(lowEnd: Boolean): Long = if (lowEnd) 2L shl 30 else 4L shl 30

    /** Plain words for the player's error card; never the raw link. */
    class TorrentFailure(message: String) : IOException(message)

    suspend fun open(context: Context, link: String, title: String?, http: OkHttpClient): Opened = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!TorrentEngine.ensure(app, http)) throw TorrentFailure((TorrentEngine.state.value as? TorrentEngine.State.Failed)?.message ?: tr(R.string.the_torrent_engine_didnt_start, "The torrent engine didn't start."))
        val lowEnd = DeviceClass.lowEnd
        val c = core ?: synchronized(this@TorrentStreams) { core ?: coreFactory(lowEnd).also { core = it } }
        val root = File(app.cacheDir, "torrents")
        val known = TorrentPlan.infoHash(link)
        val magnet = known?.let { h -> if (link.trim().startsWith("magnet:?", true)) link.trim() else "magnet:?xt=urn:btih:$h" }
        val hash = when {
            known != null && magnet != null -> {
                val resume = File(root, "$known.resume")
                (if (resume.isFile) runCatching { c.addResume(resume.readBytes(), root) }.getOrNull() else null)
                    ?: c.addMagnet(TorrentPlan.withTrackers(magnet), root)
            }
            link.trim().startsWith("http", true) -> {
                val bytes = http.newCall(Request.Builder().url(link.trim()).build()).execute().use { r ->
                    if (!r.isSuccessful) throw TorrentFailure("The .torrent file didn't download (HTTP ${r.code}).")
                    val body = r.body
                    if (body.contentLength() > MAX_TORRENT_FILE) throw TorrentFailure(tr(R.string.that_torrent_file_is_too_large, "That .torrent file is too large."))
                    body.byteStream().readNBytesCompat(MAX_TORRENT_FILE + 1).also { if (it.size > MAX_TORRENT_FILE) throw TorrentFailure(tr(R.string.that_torrent_file_is_too_large, "That .torrent file is too large.")) }
                }
                runCatching { c.addTorrentFile(bytes, root) }.getOrElse { throw TorrentFailure(tr(R.string.that_isnt_a_valid_torrent_file, "That isn't a valid .torrent file.")) }
            }
            else -> throw TorrentFailure(tr(R.string.that_isnt_a_torrent_link, "That isn't a torrent link."))
        }
        synchronized(this@TorrentStreams) { active.keys.filter { it != hash }.forEach { pauseNow(c, it, root) } }
        c.resume(hash)
        val meta = awaitMeta(c, hash)
        val file = TorrentPlan.pickFile(meta.files, TorrentPlan.selectOnly(link), title) ?: throw TorrentFailure(tr(R.string.this_torrent_has_no_playable_file, "This torrent has no playable file."))
        c.onlyFile(hash, file.index, meta.files.size)
        val source = TorrentSource(c, meta, file, TorrentPlan.readAheadBytes(lowEnd), touched = { touch(c, hash, root) },
            window = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.B31_STREAM_WINDOW),
            targetFirst = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.B31_TARGET_FIRST))
        source.prime()
        synchronized(this@TorrentStreams) { active.put(hash, source)?.close() }
        touch(c, hash, root)
        scope.launch { evict(root, keep = hash, budget = cacheBudget(lowEnd)) }
        val name = file.path.substringAfterLast('/')
        Opened(server.publish(source) + "/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"), name, file.size, hash)
    }

    private fun awaitMeta(c: TorrentCore, hash: String): TorrentMeta {
        val deadline = System.currentTimeMillis() + META_WAIT_MS
        synchronized(c.signal) {
            while (true) {
                c.meta(hash)?.let { return it }
                c.error(hash)?.let { throw TorrentFailure("The torrent failed: $it") }
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) throw TorrentFailure(
                    if (c.stats(hash).first == 0) tr(R.string.no_peers_found_for_this_torrent, "No peers found for this torrent. It may have no seeders right now.") else tr(R.string.peers_were_found_but_didnt_send, "Peers were found but didn't send the torrent's file list in time."),
                )
                c.signal.wait(minOf(left, 1_000))
            }
        }
    }

    internal fun touch(c: TorrentCore, hash: String, root: File) {
        synchronized(this) {
            // TOR-IDLE-1: a read after the idle pause (e.g. the player was paused over 2 min) resumes the torrent.
            if (idlePaused.remove(hash)) { if (hash in active) { c.resume(hash); TorrentTrace.sink?.invoke("idle resume") } }
            else if (idleJobs[hash]?.isActive == true && System.nanoTime() - (lastTouch[hash] ?: 0L) < 5_000_000_000L) return
            lastTouch[hash] = System.nanoTime()
            idleJobs.remove(hash)?.cancel()
            idleJobs[hash] = scope.launch { delay(IDLE_MS); idle(c, hash, root) }
        }
    }

    /**
     * TOR-IDLE-1: nobody read for [IDLE_MS]. The engine pauses (no background seeding) but the source stays open,
     * so the same player can read on; before, the source was closed and the next read failed.
     */
    internal fun idle(c: TorrentCore, hash: String, root: File) = synchronized(this) {
        if (hash !in active) return@synchronized
        c.saveResume(hash, File(root, "$hash.resume"))
        c.pause(hash)
        idleJobs.remove(hash)?.cancel()
        idlePaused += hash
        TorrentTrace.sink?.invoke("idle pause")
    }
    private val lastTouch = HashMap<String, Long>()

    private fun pauseNow(c: TorrentCore, hash: String, root: File) {
        c.saveResume(hash, File(root, "$hash.resume"))
        c.pause(hash)
        idleJobs.remove(hash)?.cancel()
        idlePaused.remove(hash)
        active.remove(hash)?.close()
    }

    /** Drops the oldest torrents (pieces + resume data) until the cache is under [budget]; never [keep]. */
    internal fun evict(root: File, keep: String, budget: Long) {
        val dirs = root.listFiles { f -> f.isDirectory && f.name != keep }.orEmpty().toList()
        var total = dirs.sumOf { size(it) } + size(File(root, keep))
        for (d in dirs.sortedBy { it.lastModified() }) {
            if (total <= budget) break
            total -= size(d)
            d.deleteRecursively(); File(root, d.name + ".resume").delete()
        }
    }

    private fun size(f: File): Long = if (!f.exists()) 0 else f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private const val MAX_TORRENT_FILE = 8 * 1024 * 1024

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val b = ByteArray(16 * 1024)
        while (out.size() < limit) { val n = read(b, 0, minOf(b.size, limit - out.size())); if (n < 0) break; out.write(b, 0, n) }
        return out.toByteArray()
    }
}
