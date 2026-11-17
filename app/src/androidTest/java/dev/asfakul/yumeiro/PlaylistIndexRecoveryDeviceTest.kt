/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * G7 LT-1 on the device's file system: the playlist index torn by a kill mid-write.
 *  a: index cut in half, a complete `.tmp` left from the interrupted write → the playlists come back from it.
 *  b: index cut in half, no `.tmp` → the torn file is kept as `.bad` (not overwritten), the store starts.
 * The user's real index is parked in cacheDir and put back afterwards. Log tag YuPlIndex: PLI_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistIndexRecoveryDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir get() = File(ctx.filesDir, "streams")
    private val index get() = File(dir, "playlists.json")
    private val tmp get() = File(dir, "playlists.json.tmp")
    private val bad get() = File(dir, "playlists.json.bad")
    private val parked get() = File(ctx.cacheDir, "pli_playlists.json")
    private fun log(s: String) = Log.i("YuPlIndex", s)

    @Before fun park() {
        runBlocking { PlaylistStore.init(ctx) }
        if (index.exists()) index.copyTo(parked, overwrite = true)
        tmp.delete(); bad.delete()
    }

    @After fun restore() {
        tmp.delete(); bad.delete()
        if (parked.exists()) { parked.copyTo(index, overwrite = true); parked.delete() }
        PlaylistStore.unloadForTests()
        runBlocking { PlaylistStore.init(ctx) }
    }

    private fun mine(name: String) = runBlocking { PlaylistStore.createLocal(ctx, name) }

    @Test fun tornIndexWithLeftoverTmpRecovers() {
        val p = mine("YuPlIndex mine A")
        val full = index.readText()
        assertTrue(full.contains(p.id))
        tmp.writeText(full)                       // the interrupted write's complete temp file
        index.writeText(full.take(full.length / 2)) // the torn main file
        PlaylistStore.unloadForTests()
        runBlocking { PlaylistStore.init(ctx) }
        val back = PlaylistStore.get(p.id)
        log("PLI a recovered=${back != null} bad=${bad.exists()}")
        assertTrue("LT-1: playlist lost after a torn index with a complete .tmp", back != null)
        assertEquals("YuPlIndex mine A", back!!.name)
        log("PLI_RESULT PASS a")
    }

    @Test fun tornIndexWithoutTmpIsKeptAsBad() {
        val p = mine("YuPlIndex mine B")
        val full = index.readText()
        val torn = full.take(full.length / 2)
        index.writeText(torn)
        tmp.delete()
        PlaylistStore.unloadForTests()
        runBlocking { PlaylistStore.init(ctx) }
        log("PLI b bad=${bad.exists()} badLen=${if (bad.exists()) bad.length() else -1} tornLen=${torn.length} present=${PlaylistStore.get(p.id) != null}")
        assertTrue("LT-1: the torn index must be kept as .bad", bad.exists())
        assertEquals("LT-1: .bad must hold the torn bytes", torn, bad.readText())
        log("PLI_RESULT PASS b")
    }
}
