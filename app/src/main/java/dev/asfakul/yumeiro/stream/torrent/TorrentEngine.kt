/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * B-31 / ADR-053: the built-in torrent engine's native part (libtorrent through libtorrent4j, MIT; libtorrent BSD-3).
 * The Java part is in the APK; the ~6 MB native library for the phone's ABI is fetched once from Maven Central's
 * own libtorrent4j-android-<abi> jar, checked against two pinned SHA-256 values (jar and .so) and stored read-only
 * in no-backup storage, so the APK stays small and nothing unverified is ever loaded. Phone = 2.1.0-39 (minSdk 28),
 * TV = 2.1.0-38 (minSdk 24; -39 needs Android 9): the pin set follows the Java jar's own version string.
 */
object TorrentEngine {
    class Pin(val version: String, val abi: String, val artifact: String, val jarSha256: String, val jarBytes: Long, val soSha256: String, val soBytes: Long) {
        val url: String get() = "https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j-android-$artifact/$version/libtorrent4j-android-$artifact-$version.jar"
        val entry: String get() = "lib/$abi/libtorrent4j.so"
    }

    /** From Maven Central, 2026-11-21 (ledger row B-31 has the commands). */
    val PINS: List<Pin> = listOf(
        Pin("2.1.0-39", "arm64-v8a", "arm64", "59aa4c32c0dc62c95e24c2e3fdf805624625a1f8214e2442fae492e381ecbbdc", 6210960, "6428158f8e012f048f85eb790317e55cf8557ba4018d53e543e86dd9c25cef2f", 15883152),
        Pin("2.1.0-39", "armeabi-v7a", "arm", "914157778f514661aabe4d3e72451c1d3004401ce6659407ab7ce94a3f273fbc", 5423797, "f8fbe9f7ba3703092d05bbd8ea39b75227cbd3b84c43aeefa5798464e018863c", 13734884),
        Pin("2.1.0-39", "x86", "x86", "f25b3b60f3ad59c20e45adad0d161d3fc36d34dc32df33518b6330b79ac570bb", 6108185, "de5d10d1e43882e56b4795b4e49071ea62d435444410a37c517fa60247ad0276", 16360060),
        Pin("2.1.0-39", "x86_64", "x86_64", "07ed97a0cbffbd8573a838e42549aa139b04d00ae2769eeef1a6a5402aba0cf3", 6025969, "5c48c09033912792451ebe8d0c495b9655ce44c921bed8c78b2e08a7c1d6a399", 16358808),
        Pin("2.1.0-38", "arm64-v8a", "arm64", "d9ea7d3d82e7484e07260d063a73c8f9fe5778cc06299717eba49858a44045ef", 6249092, "9141ae3d8e3af134eef6098bf83dc8d818e7bb45b4e1cb6b493503e7a83c3c63", 16645560),
        Pin("2.1.0-38", "armeabi-v7a", "arm", "46b417c525c35ebd45b225b4e002ab13629cffc1ec8d8290ece02a686491952b", 5429948, "ae95836b99cfda7616f21cb1d82d772f4e5702500ac2a808d4aefbb4611ad226", 13858964),
        Pin("2.1.0-38", "x86", "x86", "a8e256027101697bc752cbb493d274019afc3c851dfe3b5648aec5505d12dc1e", 6137622, "7e37e24a00582337942abff978a59a0e809a59b12e4244b43c5771e6441f3f40", 16616548),
        Pin("2.1.0-38", "x86_64", "x86_64", "da4f06357d14ef2637c5412638d6f29a47bd42205ea108d024d9afffd6d49273", 6090179, "9531eba0ab3e1caea3f98202e60469198523d02c00c60cccff57b336816f1230", 17183928),
    )

    sealed interface State {
        data object Idle : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var loaded = false

    /** The Java jar's own version (a constant in its bytecode, no native code needed). */
    @Volatile internal var javaVersion: () -> String = { org.libtorrent4j.LibTorrent.libtorrent4jVersion() }
    /** Tests swap the loader so no native code runs on the JVM. */
    @Volatile internal var loader: (File) -> Unit = { f ->
        System.setProperty("libtorrent4j.jni.path", f.absolutePath)
        System.load(f.absolutePath)
    }
    @Volatile internal var urlFor: (Pin) -> String = { it.url }

    fun pick(abis: List<String>, version: String, pins: List<Pin> = PINS): Pin? =
        abis.firstNotNullOfOrNull { a -> pins.firstOrNull { it.abi == a && it.version == version } }

    fun available(abis: List<String> = Build.SUPPORTED_ABIS.toList()): Boolean =
        runCatching { pick(abis, javaVersion()) != null }.getOrDefault(false)

    fun isLoaded(): Boolean = loaded

    /** Downloads (once), checks and loads the native engine. Safe to call often, from any screen. */
    suspend fun ensure(context: Context, http: OkHttpClient, abis: List<String> = Build.SUPPORTED_ABIS.toList()): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (loaded) return@withLock true
                val pin = pick(abis, runCatching { javaVersion() }.getOrDefault(""))
                    ?: run { _state.value = State.Failed("Built-in torrent playback isn't available for this device yet."); return@withLock false }
                val root = File(context.noBackupFilesDir, "torrent-engine")
                val dir = File(root, "${pin.version}/${pin.abi}")
                val lib = File(dir, "libtorrent4j.so")
                try {
                    if (!(lib.isFile && sha256(lib) == pin.soSha256)) {
                        lib.delete(); dir.mkdirs()
                        _state.value = State.Downloading(0, pin.jarBytes)
                        http.newCall(Request.Builder().url(urlFor(pin)).build()).execute().use { r ->
                            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                            install(r.body.byteStream(), pin, lib) { done -> _state.value = State.Downloading(done, pin.jarBytes) }
                        }
                    }
                    loader(lib)
                    loaded = true
                    _state.value = State.Ready
                    root.listFiles()?.filter { it.name != pin.version }?.forEach { it.deleteRecursively() }
                    true
                } catch (e: Throwable) {
                    _state.value = State.Failed(
                        when (e) {
                            is SecurityException -> "The torrent engine download was damaged and was deleted. Try again."
                            is UnsatisfiedLinkError -> "The torrent engine can't run on this device."
                            else -> "Couldn't download the torrent engine. Check your connection and try again."
                        }
                    )
                    false
                }
            }
        }

    /**
     * Reads the whole jar from [jar] (hashing it), unpacks only [Pin.entry] into [dest] (hashing it). Only when both
     * hashes and sizes match the pin is the file renamed into place, read-only; otherwise it is deleted and
     * [SecurityException] is thrown. [progress] gets the jar bytes read so far, ending at the full size.
     */
    internal fun install(jar: InputStream, pin: Pin, dest: File, progress: (Long) -> Unit = {}) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        tmp.delete()
        val jarDigest = MessageDigest.getInstance("SHA-256")
        val soDigest = MessageDigest.getInstance("SHA-256")
        val counted = Counting(jar, progress)
        var soBytes = -1L
        try {
            val raw = DigestInputStream(counted, jarDigest)
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name != pin.entry || soBytes >= 0) continue
                    var n = 0L
                    tmp.outputStream().use { out ->
                        val b = ByteArray(64 * 1024)
                        while (true) {
                            val r = zip.read(b); if (r < 0) break
                            soDigest.update(b, 0, r); out.write(b, 0, r); n += r
                            if (n > pin.soBytes) throw SecurityException("engine larger than pinned")
                        }
                    }
                    soBytes = n
                }
                // The central directory and anything after it: the jar hash covers every byte.
                val rest = ByteArray(64 * 1024)
                while (raw.read(rest) >= 0) Unit
            }
            counted.finish()
            val ok = soBytes == pin.soBytes && counted.total == pin.jarBytes &&
                hex(jarDigest.digest()) == pin.jarSha256 && hex(soDigest.digest()) == pin.soSha256
            if (!ok) throw SecurityException("torrent engine hash mismatch")
            if (!tmp.renameTo(dest)) throw IOException("rename failed")
            dest.setReadOnly()
        } catch (e: Throwable) {
            tmp.delete(); dest.delete()
            throw e
        }
    }

    internal fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; d.update(b, 0, n) } }
        return hex(d.digest())
    }

    private fun hex(b: ByteArray): String {
        val c = "0123456789abcdef"
        val sb = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xff; sb.append(c[v ushr 4]).append(c[v and 15]) }
        return sb.toString()
    }

    private class Counting(private val inner: InputStream, private val progress: (Long) -> Unit) : InputStream() {
        var total = 0L
            private set
        private var last = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) step(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) step(it.toLong()) }
        private fun step(n: Long) { total += n; if (total - last >= 256 * 1024) { last = total; progress(total) } }
        fun finish() { if (total != last) { last = total; progress(total) } }
        override fun close() = inner.close()
    }
}
