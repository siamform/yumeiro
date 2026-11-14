package dev.asfakul.yumeiro.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.cache.ImageCacheSize
import dev.asfakul.yumeiro.cache.OnlineCodec
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/** Round 12: backup format 2, staged restore, resets, the saved-pages cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 29, 34, 36]) // Android 7, 10, 14, 16
class BackupV2Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        PendingRestore.discard(ctx)
        ctx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test fun sectionsSortFilesIntoTheRightPlace() {
        assertEquals(Backup.Section.SETTINGS, Backup.sectionOf("prefs.json"))
        assertEquals(Backup.Section.SETTINGS, Backup.sectionOf("prefs/player.xml"))
        assertEquals(Backup.Section.LIBRARY, Backup.sectionOf("files/online_history.json"))
        assertEquals(Backup.Section.SOURCES, Backup.sectionOf("files/extensions/state.json"))
        assertEquals(Backup.Section.EXTENSION_FILES, Backup.sectionOf("files/extensions/plugins/a.cs3"))
        assertEquals(Backup.Section.EXTENSION_FILES, Backup.sectionOf("files/tachiyomi/anime/x.ext"))
        assertEquals(Backup.Section.LIBRARY, Backup.sectionOf("files/tachiyomi/memo/x.json"))
        assertNull(Backup.sectionOf("lib/evil.so"))
        assertNull(Backup.sectionOf("files/random/x.bin"))
    }

    @Test fun deviceBoundPrefsNeverTravel() {
        assertFalse(Backup.prefIncluded("mt_updater"))
        assertFalse(Backup.prefIncluded("online_secrets"))
        assertFalse(Backup.prefIncluded(BackupMeta.PREFS))
        assertTrue(Backup.prefIncluded("player"))
    }

    @Test fun typedPrefsRoundTrip() {
        val values = mapOf("s" to "x", "b" to true, "i" to 7, "l" to 9_000_000_000L, "f" to 1.5f, "ss" to setOf("a", "b"))
        val back = PrefCodec.decode(org.json.JSONObject(PrefCodec.encode(values).toString()))
        assertEquals(values, back)
    }

    @Test fun legacyXmlPrefsParse() {
        val xml = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map><string name="name">Yumi</string><boolean name="on" value="true" /><int name="n" value="3" />
            <long name="t" value="12" /><set name="tags"><string>a</string><string>b</string></set></map>""".trimIndent()
        val m = PrefCodec.parseXml(xml.byteInputStream())
        assertEquals("Yumi", m["name"]); assertEquals(true, m["on"]); assertEquals(3, m["n"]); assertEquals(12L, m["t"])
        assertEquals(setOf("a", "b"), m["tags"])
    }

    @Test fun exportStageApplyRestoresSettingsAndFiles() = runBlocking {
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 150).putBoolean("pip", true).commit()
        File(ctx.filesDir, "online_history.json").writeText("""[{"a":1},{"a":2}]""")
        File(ctx.filesDir, "extensions").mkdirs()
        File(ctx.filesDir, "extensions/state.json").writeText("""{"installed":[{"name":"X","sourceUrl":"https://e/x.cs3"}],"repos":[]}""")
        File(ctx.filesDir, "extensions/plugins").mkdirs()
        File(ctx.filesDir, "extensions/plugins/x.cs3").writeText("code")

        val out = ByteArrayOutputStream()
        Backup.export(ctx, out, Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES)
        val bytes = out.toByteArray()
        val info = Backup.inspect { bytes.inputStream() }
        assertEquals(Backup.FORMAT, info.format)
        assertFalse(info.hasCode)
        assertEquals(1, info.extensions)
        assertEquals(2, info.counts.optInt("history"))

        // The phone changes after the backup.
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 100).putString("junk", "y").commit()
        File(ctx.filesDir, "online_history.json").writeText("[]")

        Backup.stage(ctx, { bytes.inputStream() }, info.sections)
        assertTrue(PendingRestore.hasPending(ctx))
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertFalse(PendingRestore.hasPending(ctx))

        val p = ctx.getSharedPreferences("player", Context.MODE_PRIVATE)
        assertEquals(150, p.getInt("speed", 0))
        assertTrue(p.getBoolean("pip", false))
        assertNull(p.getString("junk", null))
        assertEquals("""[{"a":1},{"a":2}]""", File(ctx.filesDir, "online_history.json").readText())
        assertTrue(BackupMeta.pendingDownloads(ctx))
        assertNotNull(BackupMeta.takeNote(ctx))
    }

    @Test fun restoreOnlyTakesChosenSections() = runBlocking {
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 150).commit()
        File(ctx.filesDir, "online_history.json").writeText("[1]")
        val out = ByteArrayOutputStream()
        Backup.export(ctx, out, Backup.Section.entries.toSet())
        val bytes = out.toByteArray()
        File(ctx.filesDir, "online_history.json").writeText("[1,2,3]")
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 90).commit()
        Backup.stage(ctx, { bytes.inputStream() }, setOf(Backup.Section.SETTINGS))
        PendingRestore.applyIfAny(ctx)
        assertEquals(150, ctx.getSharedPreferences("player", Context.MODE_PRIVATE).getInt("speed", 0))
        assertEquals("[1,2,3]", File(ctx.filesDir, "online_history.json").readText())
    }

    @Test fun notABackupIsRejected() = runBlocking {
        val r = runCatching { Backup.inspect { "hello".byteInputStream() } }
        assertTrue(r.isFailure)
        assertFalse(PendingRestore.hasPending(ctx))
    }

    @Test fun brokenPlanIsDroppedAfterTwoTries() {
        PendingRestore.writePlan(ctx, PendingRestore.Plan(attempts = 2))
        assertFalse(PendingRestore.applyIfAny(ctx))
        assertFalse(PendingRestore.hasPending(ctx))
    }

    @Test fun historyResetKeepsSettings() {
        File(ctx.filesDir, "online_history.json").writeText("[1]")
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 150).commit()
        ctx.getSharedPreferences("search_recent", Context.MODE_PRIVATE).edit().putString("q", "x").commit()
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.HISTORY))
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertFalse(File(ctx.filesDir, "online_history.json").exists())
        assertTrue(ctx.getSharedPreferences("search_recent", Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(150, ctx.getSharedPreferences("player", Context.MODE_PRIVATE).getInt("speed", 0))
    }

    @Test fun settingsResetKeepsLibraryAndDeviceState() {
        ctx.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().putInt("speed", 150).commit()
        ctx.getSharedPreferences("mt_updater", Context.MODE_PRIVATE).edit().putString("k", "v").commit()
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.SETTINGS))
        PendingRestore.applyIfAny(ctx)
        assertEquals(0, ctx.getSharedPreferences("player", Context.MODE_PRIVATE).getInt("speed", 0))
        assertEquals("v", ctx.getSharedPreferences("mt_updater", Context.MODE_PRIVATE).getString("k", null))
    }

    @Test fun pruneKeepsNewestOfOneKindOnly() {
        fun item(n: String, t: Long) = BackupStore.Item(Uri.parse("file:///$n"), n, 1, t, BackupStore.Where.APP)
        val all = listOf(item("yumeiro-auto-1.zip", 1), item("yumeiro-auto-2.zip", 2), item("yumeiro-auto-3.zip", 3), item("yumeiro-backup-1.zip", 0))
        val gone = BackupStore.pruneList(all, "auto", 2)
        assertEquals(listOf("yumeiro-auto-1.zip"), gone.map { it.name })
        assertEquals("safety", item("yumeiro-before-restore-x.zip", 0).kind)
    }

    @Test fun dataCacheReadsFreshAndForgetsOld() {
        DataCache.init(ctx)
        DataCache.clearAll()
        DataCache.write("home", "k", "value")
        assertEquals("value", DataCache.read("home", "k"))
        DataCache.clearMemory()
        assertEquals("value", DataCache.read("home", "k")) // from disk
        val f = DataCache.fileFor("home", "k")!!
        f.setLastModified(System.currentTimeMillis() - 30L * 86_400_000L)
        DataCache.clearMemory()
        assertNull(DataCache.read("home", "k"))
        DataCache.write("home", "k2", "v2")
        DataCache.fileFor("home", "k2")!!.setLastModified(System.currentTimeMillis() - 30L * 86_400_000L)
        DataCache.write("home", "k3", "v3")
        DataCache.purge()
        assertFalse(DataCache.fileFor("home", "k2")!!.exists())
        assertTrue(DataCache.fileFor("home", "k3")!!.exists())
    }

    @Test fun onlineCodecRoundTrip() {
        val item = OnlineItem("Big Buck Bunny", "https://x/bbb", "Demo", "https://x/p.jpg", mapOf("Referer" to "https://x"), "Movie", "HD", 7.5, setOf("Dubbed"))
        assertEquals(listOf(item), OnlineCodec.items(OnlineCodec.items(listOf(item))))
        val row = OnlineRow("Demo", "Popular", listOf(item), true, "Popular", "/popular", true)
        assertEquals(listOf(row), OnlineCodec.rows(OnlineCodec.rows(listOf(row))))
        val ep = OnlineEpisode("d1", "Pilot", 1, 1, null, "plot", 24)
        val d = OnlineDetail("Demo", "https://x/bbb", "Big Buck Bunny", OnlineKind.SERIES, "TvSeries", null, null, emptyMap(), "plot", 2008,
            listOf("Animation"), 10, 7.5, null, null, mapOf("" to listOf(ep)), mapOf(1 to "Season 1"), listOf(item), imdbId = "tt1254207")
        assertEquals(d, OnlineCodec.detail(OnlineCodec.detail(d)))
    }

    @Test fun imageCacheScalesWithFreeSpace() {
        assertEquals(64L shl 20, ImageCacheSize.pick(0))
        assertEquals(512L shl 20, ImageCacheSize.pick(1L shl 40))
        assertEquals((10L shl 30) / 50, ImageCacheSize.pick(10L shl 30))
    }
}
