/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * F-02 / ADR-052: TDLib's native library is not in the APK. The first time the user opens Telegram sign-in, the file
 * for this phone's ABI (about 9-12 MB, gzip) is downloaded from the `tdlib-bin` branch at a pinned commit, checked
 * against two pinned SHA-256 values (the gzip and the library inside), written read-only to `noBackupFilesDir`, and
 * loaded with `System.load`. A file with any other hash is deleted and never loaded. No pins = sign-in hidden.
 */
object TdEngine {
    /** tdlib/td commit the library was built from (TDLib 1.8.70). */
    const val TD_COMMIT = "03220f6b3043c6fd7785fbf0901d63f19ad19974"

    /** Commit of the `tdlib-bin` branch that holds the files below (built by tdlib-build.yml run 44871959357). */
    const val BIN_COMMIT = "010f8192ddb0aa9f593eff628c934f6d62b330a8"

    class Pin(val abi: String, val soSha256: String, val gzSha256: String, val gzBytes: Long, val soBytes: Long)

    /** Filled from tdlib-build.yml's SHA256 / SIZES files (ledger row F-02 login has the run ID). */
    val PINS: List<Pin> = listOf(
        Pin("arm64-v8a", "df2dc2ea2f8f83395f0950f8c152a0244e7c20c33c137e3875dc90646e21078f", "302d9ba90d83625a1e962d42b71fcf7a67fdecc2c3d56ecd0d3e9a2a6ddb7e53", 9517585, 19037912),
        Pin("armeabi-v7a", "fcbb87f3bc3aaff925435a8c1f1c0dd09f057fc0ea999094c2f46e3c32edeecd", "959382c6916420698a80eb0c25c15ecf0075787f4f06167bac9d590198ffd15e", 8582731, 15923976),
        Pin("x86_64", "0b49ed935bec930a59be6eee761b900200182682228693a5431dfae429e6900d", "8e5cc7306040fd6b530d68664dc82d967a59e2898dcac59f6c583475b3293cb8", 9024381, 22276816),
        Pin("x86", "7a691046280b528a34fbf998fb6b02e1b57c7b2888df0ea13310ee2178ecfeb2", "a1cd916b79e6db1de625f548ccb693301ccdd383091713966095c84618a2021d", 9689272, 24056816),
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
    /** Tests swap the loader so no native code runs on the JVM. */
    @Volatile internal var loader: (File) -> Unit = { System.load(it.absolutePath) }
    @Volatile internal var urlFor: (Pin) -> String = { pin ->
        "https://raw.githubusercontent.com/siamform/yumeiro/$BIN_COMMIT/${pin.abi}/libtdjsonjava.so.gz"
    }

    /** True when this build carries pins for one of the phone's ABIs. */
    fun available(abis: List<String> = Build.SUPPORTED_ABIS.toList()): Boolean = BIN_COMMIT.isNotBlank() && pick(abis) != null

    /** The first ABI the phone prefers that has a pin (SUPPORTED_ABIS is in preference order). */
    fun pick(abis: List<String>, pins: List<Pin> = PINS): Pin? = abis.firstNotNullOfOrNull { a -> pins.firstOrNull { it.abi == a } }

    fun isLoaded(): Boolean = loaded

    /** Downloads (if needed), checks and loads the library. Safe to call many times and from many screens. */
    suspend fun ensure(context: Context, http: OkHttpClient, pins: List<Pin> = PINS, abis: List<String> = Build.SUPPORTED_ABIS.toList()): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (loaded) return@withLock true
                val pin = pick(abis, pins) ?: run { _state.value = State.Failed("Telegram sign-in isn't available for this device yet."); return@withLock false }
                val dir = File(context.noBackupFilesDir, "tdlib/${TD_COMMIT.take(8)}/${pin.abi}")
                val lib = File(dir, "libtdjsonjava.so")
                try {
                    if (!(lib.isFile && sha256(lib) == pin.soSha256)) {
                        lib.delete()
                        dir.mkdirs()
                        _state.value = State.Downloading(0, pin.gzBytes)
                        val req = Request.Builder().url(urlFor(pin)).build()
                        http.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                            val body = r.body.byteStream()
                            install(body, pin, lib) { done -> _state.value = State.Downloading(done, pin.gzBytes) }
                        }
                    }
                    loader(lib)
                    loaded = true
                    _state.value = State.Ready
                    cleanOld(File(context.noBackupFilesDir, "tdlib"), TD_COMMIT.take(8))
                    true
                } catch (e: Throwable) {
                    _state.value = State.Failed(
                        when (e) {
                            is SecurityException -> "The Telegram engine download was damaged and was deleted. Try again."
                            is UnsatisfiedLinkError -> "The Telegram engine can't run on this device."
                            else -> "Couldn't download the Telegram engine. Check your connection and try again."
                        }
                    )
                    false
                }
            }
        }

    /**
     * Streams [gz] into [dest] through gunzip, hashing both sides on the way. Only a file whose two hashes match the
     * pin is renamed into place (read-only); anything else is deleted and [SecurityException] is thrown.
     */
    internal fun install(gz: InputStream, pin: Pin, dest: File, progress: (Long) -> Unit = {}) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        tmp.delete()
        val gzDigest = MessageDigest.getInstance("SHA-256")
        val soDigest = MessageDigest.getInstance("SHA-256")
        val counted = CountingInput(DigestInputStream(gz, gzDigest), progress)
        try {
            val unzip = GZIPInputStream(counted, 64 * 1024)
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = unzip.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > pin.soBytes) throw SecurityException("larger than pinned")
                    soDigest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
                out.fd.sync()
            }
            // Drain anything the gzip reader left so the gzip hash covers the whole download.
            val rest = ByteArray(8 * 1024)
            while (counted.read(rest, 0, rest.size) >= 0) Unit
            unzip.close()
            val gzHex = hex(gzDigest.digest())
            val soHex = hex(soDigest.digest())
            if (gzHex != pin.gzSha256 || soHex != pin.soSha256) throw SecurityException("hash mismatch")
            if (!tmp.renameTo(dest)) throw IOException("rename failed")
            dest.setReadOnly()
        } catch (e: Throwable) {
            tmp.delete()
            if (e is java.util.zip.ZipException || e is java.io.EOFException) throw SecurityException("not a valid gzip", e)
            throw e
        }
    }

    private fun cleanOld(root: File, keep: String) {
        root.listFiles()?.forEach { if (it.name != keep) it.deleteRecursively() }
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

    private class CountingInput(private val inner: InputStream, private val progress: (Long) -> Unit) : InputStream() {
        private var done = 0L
        private var lastReport = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) step(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) step(it.toLong()) }
        private fun step(n: Long) {
            done += n
            if (done - lastReport >= 256 * 1024) { lastReport = done; progress(done) }
        }
        override fun close() = inner.close()
    }
}
