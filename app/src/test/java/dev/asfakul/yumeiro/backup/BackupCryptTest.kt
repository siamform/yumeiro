/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** B20 S7: password-locked backups and the keys-and-sign-ins section. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupCryptTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pw = "correct horse battery".toCharArray()

    @Before fun setUp() {
        BackupCrypt.iterationsForTests = 10_000
        PendingRestore.discard(ctx)
        Profiles.resetForTests(ctx)
        OnlineSettings.resetForTests(); OnlineSettings.init(ctx)
    }
    @After fun tearDown() {
        BackupCrypt.iterationsForTests = null
        PendingRestore.discard(ctx)
        OnlineSettings.setSecret(ctx, "subdl_user", null); OnlineSettings.setSecret(ctx, "ai_key_p${Profiles.DEFAULT}", null)
        OnlineSettings.resetForTests(); Profiles.resetForTests(ctx)
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun seal(plain: ByteArray, password: CharArray = pw): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypt.seal(out, password).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun open(locked: ByteArray, password: CharArray = pw): ByteArray = BackupCrypt.open(ByteArrayInputStream(locked), password).use { it.readBytes() }

    // region key derivation

    @Test fun pbkdf2MatchesTheStandardVectors() {
        // RFC 7914 §11 / the widely used PBKDF2-HMAC-SHA256 vectors.
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", hex(BackupCrypt.pbkdf2("password".toCharArray(), "salt".toByteArray(), 1)))
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43", hex(BackupCrypt.pbkdf2("password".toCharArray(), "salt".toByteArray(), 2)))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", hex(BackupCrypt.pbkdf2("password".toCharArray(), "salt".toByteArray(), 4096)))
    }

    @Test fun pbkdf2MatchesTheJvmForUnicodePasswords() {
        val p = "পাসওয়ার্ড-密码-🔑".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val jvm = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(p, salt, 1000, 256)).encoded
        // The JVM's PBEKeySpec takes UTF-8 for PBKDF2 too.
        assertArrayEquals(jvm, BackupCrypt.pbkdf2(p, salt, 1000))
    }

    @Test fun owaspIterationCountIsTheDefault() { assertEquals(600_000, BackupCrypt.ITERATIONS) }

    // endregion

    // region format

    @Test fun roundTripsEverySize() {
        for (size in listOf(0, 1, BackupCrypt.CHUNK - 1, BackupCrypt.CHUNK, BackupCrypt.CHUNK + 1, 3 * BackupCrypt.CHUNK + 17)) {
            val plain = ByteArray(size) { (it * 31 + 7).toByte() }
            val locked = seal(plain)
            assertTrue(BackupCrypt.isLocked(locked))
            assertArrayEquals("size $size", plain, open(locked))
        }
    }

    @Test fun sameInputLocksDifferentlyEachTime() {
        val plain = "same".toByteArray()
        assertFalse(seal(plain).contentEquals(seal(plain)))
    }

    @Test fun wrongPasswordFailsBeforeReading() {
        val locked = seal("secret stuff".toByteArray())
        try { open(locked, "wrong password!".toCharArray()); fail() } catch (e: BackupCrypt.WrongPassword) { assertNotNull(e.message) }
    }

    @Test fun changedBytesAreCaught() {
        val locked = seal(ByteArray(2 * BackupCrypt.CHUNK + 5) { it.toByte() })
        // Header (salt), first chunk body and last chunk body.
        for (at in listOf(12, 80, locked.size - 3)) {
            val bad = locked.copyOf(); bad[at] = (bad[at].toInt() xor 1).toByte()
            try { open(bad); fail("byte $at") } catch (e: IllegalStateException) { assertTrue(e is BackupCrypt.Damaged || e is BackupCrypt.WrongPassword) }
        }
    }

    @Test fun cutOffAndExtendedFilesAreCaught() {
        val locked = seal(ByteArray(2 * BackupCrypt.CHUNK + 5) { it.toByte() })
        val firstChunkEnd = 65 + 5 + BackupCrypt.CHUNK + 16
        for (cut in listOf(firstChunkEnd, firstChunkEnd + 100, locked.size - 1, 40)) {
            try { open(locked.copyOf(cut)); fail("cut at $cut") } catch (e: BackupCrypt.Damaged) { assertNotNull(e.message) }
        }
        try { open(locked + byteArrayOf(0)); fail() } catch (e: BackupCrypt.Damaged) { assertNotNull(e.message) }
    }

    @Test fun droppingOrSwappingChunksIsCaught() {
        val locked = seal(ByteArray(3 * BackupCrypt.CHUNK) { it.toByte() } + ByteArray(5))
        val chunk = 5 + BackupCrypt.CHUNK + 16
        val head = locked.copyOfRange(0, 65)
        val c0 = locked.copyOfRange(65, 65 + chunk); val c1 = locked.copyOfRange(65 + chunk, 65 + 2 * chunk)
        val rest = locked.copyOfRange(65 + 2 * chunk, locked.size)
        try { open(head + c1 + c0 + rest); fail() } catch (e: BackupCrypt.Damaged) { assertNotNull(e.message) }
        try { open(head + c0 + rest); fail() } catch (e: BackupCrypt.Damaged) { assertNotNull(e.message) }
    }

    @Test fun passwordRules() {
        assertNotNull(BackupCrypt.passwordProblem("short".toCharArray()))
        assertNotNull(BackupCrypt.passwordProblem("        ".toCharArray()))
        assertNotNull(BackupCrypt.passwordProblem("long enough".toCharArray(), "long enougH".toCharArray()))
        assertNull(BackupCrypt.passwordProblem("long enough".toCharArray(), "long enough".toCharArray()))
    }

    // endregion

    // region backups

    private fun entries(zip: ByteArray, password: CharArray? = null): Map<String, ByteArray> {
        val (s, _) = Backup.plain(ByteArrayInputStream(zip), password)
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(s).use { z -> generateSequence { z.nextEntry }.forEach { e -> out[e.name] = z.readBytes() } }
        return out
    }

    @Test fun keysOnlyGoIntoALockedBackup() = runBlocking {
        OnlineSettings.setSecret(ctx, "subdl_user", "b20-user-name")
        OnlineSettings.setSecret(ctx, "ai_key_p${Profiles.DEFAULT}", "AIza-b20-test-not-a-real-key")
        val plain = ByteArrayOutputStream().also { Backup.export(ctx, it, Backup.Section.entries.toSet()) }.toByteArray()
        assertFalse(BackupCrypt.isLocked(plain))
        assertFalse(entries(plain).containsKey(SecretsBackup.ENTRY))
        assertFalse(String(plain, Charsets.ISO_8859_1).contains("b20-test-not-a-real-key"))

        val locked = ByteArrayOutputStream().also { Backup.export(ctx, it, Backup.Section.entries.toSet(), password = pw) }.toByteArray()
        assertTrue(BackupCrypt.isLocked(locked))
        // Nothing readable in the file itself.
        assertFalse(String(locked, Charsets.ISO_8859_1).contains("b20-test-not-a-real-key"))
        assertFalse(String(locked, Charsets.ISO_8859_1).contains("yumeiro-backup.json"))
        val inside = entries(locked, pw)
        val secrets = JSONObject(String(inside.getValue(SecretsBackup.ENTRY))).getJSONObject("secrets")
        assertEquals("AIza-b20-test-not-a-real-key", secrets.getString("secret:ai_key_p${Profiles.DEFAULT}"))
        assertEquals("b20-user-name", secrets.getString("secret:subdl_user"))
        assertTrue(JSONObject(String(inside.getValue(Backup.MANIFEST))).getBoolean("locked"))
    }

    @Test fun inspectNeedsThePasswordAndReportsTheSection() = runBlocking {
        OnlineSettings.setSecret(ctx, "subdl_user", "b20-user-name")
        val locked = ByteArrayOutputStream().also { Backup.export(ctx, it, Backup.Section.entries.toSet(), password = pw) }.toByteArray()
        try { Backup.inspect({ ByteArrayInputStream(locked) }); fail() } catch (e: BackupCrypt.NeedsPassword) { assertNotNull(e.message) }
        try { Backup.inspect({ ByteArrayInputStream(locked) }, "not the password".toCharArray()); fail() } catch (e: BackupCrypt.WrongPassword) { assertNotNull(e.message) }
        val info = Backup.inspect({ ByteArrayInputStream(locked) }, pw)
        assertTrue(info.locked)
        assertTrue(Backup.Section.SECRETS in info.sections)
        // A cut-off copy fails before anything is staged.
        val cut = locked.copyOf(locked.size - 20)
        try { Backup.stage(ctx, { ByteArrayInputStream(cut) }, info.sections, pw); fail() } catch (e: IllegalStateException) { assertNotNull(e.message) }
        assertFalse(PendingRestore.hasPending(ctx))
    }

    @Test fun lockedBackupRestoresLikeAPlainOne() = runBlocking {
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 175).commit()
        File(ctx.filesDir, "online_history.json").writeText("""[{"a":1}]""")
        val locked = ByteArrayOutputStream().also { Backup.export(ctx, it, setOf(Backup.Section.SETTINGS, Backup.Section.LIBRARY), password = pw) }.toByteArray()
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir, "online_history.json").delete()
        Backup.stage(ctx, { ByteArrayInputStream(locked) }, setOf(Backup.Section.SETTINGS, Backup.Section.LIBRARY), pw)
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertEquals(175, ctx.getSharedPreferences("player", Context.MODE_PRIVATE).getInt("speed", 0))
        assertEquals("""[{"a":1}]""", File(ctx.filesDir, "online_history.json").readText())
    }

    @Test fun aPlainZipCannotCarryKeys() = runBlocking {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry(Backup.MANIFEST)); z.write("""{"format":2,"app":"dev.asfakul.yumeiro"}""".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry(SecretsBackup.ENTRY)); z.write("""{"secrets":{"secret:anilist_token":"evil"}}""".toByteArray()); z.closeEntry()
        }
        val info = Backup.inspect({ ByteArrayInputStream(out.toByteArray()) })
        assertFalse(info.locked); assertFalse(Backup.Section.SECRETS in info.sections)
        Backup.stage(ctx, { ByteArrayInputStream(out.toByteArray()) }, Backup.Section.entries.toSet())
        assertFalse(File(PendingRestore.dir(ctx), SecretsBackup.STAGED).exists())
    }

    // endregion

    // region secrets section

    @Test fun resealDropsWhatCannotBeSealedAndOddNames() {
        val o = JSONObject().put("secrets", JSONObject().put("secret:anilist_token", "tok").put("subdl_key", "k").put("../evil", "x").put("secret:", "y").put("secret:mal_user", ""))
            .put("prefs", JSONObject().put("yt_account", JSONObject()).put("player", JSONObject()))
        val (sealed, failed) = SecretsBackup.reseal(o) { v -> if (v == "k") null else "v1:sealed-$v" }
        val s = sealed.getJSONObject("secrets")
        assertEquals("v1:sealed-tok", s.getString("secret:anilist_token"))
        assertFalse(s.has("subdl_key")); assertEquals(1, failed)
        assertFalse(s.has("../evil")); assertFalse(s.has("secret:")); assertFalse(s.has("secret:mal_user"))
        assertTrue(sealed.getJSONObject("prefs").has("yt_account")); assertFalse(sealed.getJSONObject("prefs").has("player"))
    }

    @Test fun applyWritesOnlySealedValues() {
        val staged = JSONObject().put("secrets", JSONObject().put("secret:anilist_token", "v1:abc").put("secret:mal_token", "plain-text"))
        assertEquals(1, SecretsBackup.apply(ctx, staged))
        val p = ctx.getSharedPreferences("online_secrets", Context.MODE_PRIVATE)
        assertEquals("v1:abc", p.getString("secret:anilist_token", null))
        assertNull(p.getString("secret:mal_token", null))
        p.edit().clear().commit()
    }

    @Test fun aLockedOtherProfilesAiKeyStaysOut() {
        val other = Profiles.add(ctx, "Other", dev.asfakul.yumeiro.ui.theme.AccentTone.AO)
        OnlineSettings.setSecret(ctx, "ai_key_p${other.id}", "AIza-other-profile-key")
        OnlineSettings.setSecret(ctx, "ai_key_p${Profiles.DEFAULT}", "AIza-mine")
        assertEquals(other.id, SecretsBackup.aiProfileOf("secret:ai_key_p${other.id}"))
        assertTrue(Profiles.setPin(ctx, other.id, "4826"))
        val s = SecretsBackup.collect(ctx).getJSONObject("secrets")
        assertFalse(s.has("secret:ai_key_p${other.id}"))
        assertEquals("AIza-mine", s.getString("secret:ai_key_p${Profiles.DEFAULT}"))
        OnlineSettings.setSecret(ctx, "ai_key_p${other.id}", null)
    }

    // endregion
}
