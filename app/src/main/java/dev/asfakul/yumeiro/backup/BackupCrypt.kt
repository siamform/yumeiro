/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * B20 S7: password-locked backups (`.ymbk`).
 *
 * Layout: `YMBK1` · iterations (4 bytes) · salt (16) · nonce prefix (8) · password check (32), then chunks of at
 * most 64 KiB of the zip, each `flag (1) · length (4) · AES-256-GCM ciphertext + tag`. The key comes from
 * PBKDF2-HMAC-SHA256 with 600 000 iterations (OWASP's figure for that hash), written here with [Mac] because
 * `PBKDF2WithHmacSHA256` only exists from Android 8 and the TV app starts at Android 7. Each chunk's nonce is the
 * random prefix plus the chunk number, and the header, chunk number and "last chunk" flag are authenticated, so a
 * chunk that is changed, moved, dropped or cut off fails instead of restoring a partial backup.
 *
 * The password is never stored; a lost password means the backup can't be opened.
 */
object BackupCrypt {
    private val MAGIC = "YMBK1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 5_000_000
    const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128
    private const val HEADER = 5 + 4 + 16 + 8 + 32
    const val MIN_PASSWORD = 8
    const val EXTENSION = ".ymbk"

    class NeedsPassword : IllegalStateException("This backup is locked with a password")
    class WrongPassword : IllegalStateException("That password doesn't open this backup")
    class Damaged(why: String) : IllegalStateException("This backup is damaged or incomplete ($why)")

    /** True when [head] (the first bytes of a file) starts a locked backup. */
    fun isLocked(head: ByteArray): Boolean = head.size >= MAGIC.size && (MAGIC.indices).all { head[it] == MAGIC[it] }

    /** Reads the first bytes of [open]'s stream to tell a locked backup from a zip. */
    fun isLocked(open: () -> InputStream?): Boolean = open()?.use { s ->
        val b = ByteArray(MAGIC.size); var n = 0
        while (n < b.size) { val r = s.read(b, n, b.size - n); if (r < 0) break; n += r }
        isLocked(b.copyOf(n))
    } ?: false

    /** A problem with the password the user typed, or null when it can be used. */
    fun passwordProblem(password: CharArray, confirm: CharArray? = null): String? = when {
        password.size < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters"
        password.all { it.isWhitespace() } -> "Use at least $MIN_PASSWORD characters"
        confirm != null && !password.contentEquals(confirm) -> "The two passwords don't match"
        else -> null
    }

    // region key

    /** PBKDF2-HMAC-SHA256 (RFC 8018) of the UTF-8 password; 32 bytes. */
    internal fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val pw = utf8(password)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(if (pw.isEmpty()) emptyKey() else SecretKeySpec(pw, "HmacSHA256"))
            mac.update(salt); mac.update(byteArrayOf(0, 0, 0, 1))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            return t
        } finally { pw.fill(0) }
    }

    /** SecretKeySpec refuses an empty key; HMAC pads an empty key with zeros, so one zero byte is the same key. */
    private fun emptyKey() = SecretKeySpec(ByteArray(1), "HmacSHA256")

    private fun utf8(cs: CharArray): ByteArray {
        val bb = Charsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(cs))
        val out = ByteArray(bb.remaining()); bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    private fun hmac(key: ByteArray, label: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(label.toByteArray(Charsets.US_ASCII))

    private class Keys(val enc: ByteArray, val check: ByteArray)

    private fun keys(password: CharArray, salt: ByteArray, iterations: Int): Keys {
        val master = pbkdf2(password, salt, iterations)
        try { return Keys(hmac(master, "YMBK1 encrypt"), hmac(master, "YMBK1 check")) } finally { master.fill(0) }
    }

    // endregion

    // region writing

    /** Wraps [out]: what is written goes out encrypted; [OutputStream.close] writes the last chunk. */
    /** Tests only: fewer iterations so a round trip doesn't take seconds under Robolectric. */
    @Volatile internal var iterationsForTests: Int? = null

    fun seal(out: OutputStream, password: CharArray, iterations: Int = iterationsForTests ?: ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val k = keys(password, salt, iterations)
        val header = ByteArrayOutputStream(HEADER).apply { write(MAGIC); write(int(iterations)); write(salt); write(prefix); write(k.check) }.toByteArray()
        out.write(header)
        return Sealer(out, header, prefix, k.enc)
    }

    private class Sealer(private val sink: OutputStream, private val header: ByteArray, private val prefix: ByteArray, private val key: ByteArray) : FilterOutputStream(sink) {
        private val buf = ByteArray(CHUNK)
        private var used = 0
        private var index = 0
        private var closed = false

        override fun write(b: Int) { if (used == CHUNK) emit(false); buf[used++] = b.toByte() }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off; var left = len
            while (left > 0) {
                if (used == CHUNK) emit(false)
                val n = minOf(left, CHUNK - used)
                System.arraycopy(b, o, buf, used, n); used += n; o += n; left -= n
            }
        }

        override fun flush() { sink.flush() }

        private fun emit(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(prefix, index)))
            c.updateAAD(aad(header, index, last))
            val ct = c.doFinal(buf, 0, used)
            sink.write(if (last) 1 else 0); sink.write(int(ct.size)); sink.write(ct)
            used = 0; index++
        }

        override fun close() {
            if (closed) return
            closed = true
            try { emit(true); sink.flush() } finally { key.fill(0); buf.fill(0); sink.close() }
        }
    }

    // endregion

    // region reading

    /**
     * Opens a locked backup. A wrong password fails here, before anything is read; a changed or cut-off file
     * fails at the chunk where it happens ([Damaged]), and a file without its last chunk fails at the end.
     */
    fun open(input: InputStream, password: CharArray): InputStream {
        val din = DataInputStream(input)
        val header = ByteArray(HEADER)
        try { din.readFully(header) } catch (e: EOFException) { throw Damaged("too short") }
        if (!isLocked(header)) throw Damaged("not a locked backup")
        val iterations = readInt(header, 5)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw Damaged("bad header")
        val salt = header.copyOfRange(9, 25)
        val prefix = header.copyOfRange(25, 33)
        val check = header.copyOfRange(33, 65)
        val k = keys(password, salt, iterations)
        if (!MessageDigest.isEqual(k.check, check)) { k.enc.fill(0); throw WrongPassword() }
        return Opener(din, header, prefix, k.enc)
    }

    private class Opener(private val din: DataInputStream, private val header: ByteArray, private val prefix: ByteArray, private val key: ByteArray) : InputStream() {
        private var chunk = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var done = false

        private fun next(): Boolean {
            if (done) return false
            val flag = try { din.readUnsignedByte() } catch (e: EOFException) { throw Damaged("the end is missing") }
            if (flag != 0 && flag != 1) throw Damaged("bad chunk")
            val len = try { din.readInt() } catch (e: EOFException) { throw Damaged("the end is missing") }
            if (len < TAG_BITS / 8 || len > CHUNK + TAG_BITS / 8) throw Damaged("bad chunk")
            val ct = ByteArray(len)
            try { din.readFully(ct) } catch (e: EOFException) { throw Damaged("the end is missing") }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(prefix, index)))
            c.updateAAD(aad(header, index, flag == 1))
            chunk = try { c.doFinal(ct) } catch (e: AEADBadTagException) { throw Damaged("chunk ${index + 1} was changed") }
            pos = 0; index++
            if (flag == 1) {
                done = true
                if (din.read() >= 0) throw Damaged("extra data after the end")
                key.fill(0)
            }
            return true
        }

        override fun read(): Int {
            while (pos >= chunk.size) if (!next()) return -1
            return chunk[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= chunk.size) if (!next()) return -1
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n); pos += n
            return n
        }

        override fun available(): Int = chunk.size - pos
        override fun close() { key.fill(0); din.close() }
    }

    // endregion

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun readInt(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
    private fun nonce(prefix: ByteArray, index: Int) = prefix + int(index)
    private fun aad(header: ByteArray, index: Int, last: Boolean) = header + int(index) + byteArrayOf(if (last) 1 else 0)
}
