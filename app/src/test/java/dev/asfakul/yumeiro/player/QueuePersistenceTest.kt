/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.security.Privacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** G6-QP (ADR-019): the Home Continue queue file. Unit (Robolectric); the device run is QueuePersistenceDeviceTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QueuePersistenceTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val file get() = File(ctx.filesDir, LastPlayed.QUEUE_FILE)
    private fun t(n: Int) = AudioTrack(id = "s$n", title = "Song $n", artist = "A", album = "Al", durationMs = 12_000, uri = "content://media/external/audio/media/$n", albumArtUri = null, albumId = 7, folderName = "Music", folderPath = "/Music")
    private val five = (1..5).map(::t)
    private val legacy = { listOf(t(2), t(1)) } // what Home built before G6-QP: Recently played

    @Before fun setUp() { LastPlayed.resetForTests(); file.delete(); File(ctx.filesDir, file.name + ".bad").delete(); File(ctx.filesDir, file.name + ".tmp").delete() }
    @After fun tearDown() { Privacy.setIncognito(ctx, false); LastPlayed.resetForTests() }

    private fun newProcess() { LastPlayed.resetForTests(); LastPlayed.loadQueue(ctx) }

    @Test fun fiveSongsSurviveANewProcess() {
        LastPlayed.saveQueue(ctx, five, t(2))
        LastPlayed.awaitQueueWrites()
        newProcess()
        val q = LastPlayed.queue.value
        assertEquals(five.map { it.id }, q?.tracks?.map { it.id })
        assertEquals("s2", q?.currentId)
        assertEquals(five, LastPlayed.continueQueue(q, t(2), legacy))
        assertEquals("all fields kept", five[3], q!!.tracks[3])
    }

    @Test fun oldSingleSongDataStillLoads() {
        // Before G6-QP only the prefs "audio" entry existed; there is no queue file.
        val old = LastPlayed.Audio(t(2), 4_000, 12_000, 1L)
        ctx.getSharedPreferences("yu_last_played", Context.MODE_PRIVATE).edit().putString("audio", LastPlayed.encodeAudio(old)).commit()
        LastPlayed.init(ctx)
        newProcess()
        LastPlayed.init(ctx)
        assertEquals("s2", LastPlayed.audio.value?.track?.id)
        assertEquals(4_000L, LastPlayed.audio.value?.positionMs)
        assertNull(LastPlayed.queue.value)
        assertEquals("falls back to the queue built before G6-QP", legacy(), LastPlayed.continueQueue(LastPlayed.queue.value, t(2), legacy))
    }

    @Test fun brokenFileFallsBackAndIsKept() {
        file.writeText("{\"v\":1,\"q\":[{\"id\":\"s1\",")
        newProcess()
        assertNull(LastPlayed.queue.value)
        assertTrue("broken file kept as .bad", File(ctx.filesDir, file.name + ".bad").exists())
        assertEquals(legacy(), LastPlayed.continueQueue(LastPlayed.queue.value, t(2), legacy))
    }

    @Test fun otherFormatsFallBack() {
        assertNull(LastPlayed.decodeQueue("{\"id\":\"s1\",\"u\":\"content://x\"}")) // a single-song entry, not a queue
        assertNull(LastPlayed.decodeQueue("{\"v\":1,\"q\":[]}"))
        assertNull(LastPlayed.decodeQueue(""))
        assertEquals(listOf("s1"), LastPlayed.decodeQueue("{\"v\":9,\"x\":1,\"q\":[{\"id\":\"s1\",\"u\":\"content://x\"},{\"id\":\"\"}]}")?.tracks?.map { it.id })
    }

    @Test fun queueWithoutTheContinueSongIsNotUsed() {
        val q = LastPlayed.SavedQueue(five, "s2", 1L)
        assertEquals(legacy(), LastPlayed.continueQueue(q, t(9), legacy))
    }

    @Test fun lastChangeWinsAndDuplicatesStay() {
        LastPlayed.saveQueue(ctx, five, t(1))
        LastPlayed.saveQueue(ctx, five + t(3), t(1)) // play next / add to queue
        LastPlayed.saveQueue(ctx, five.drop(1) + t(3), t(2)) // remove
        LastPlayed.awaitQueueWrites()
        newProcess()
        assertEquals(listOf("s2", "s3", "s4", "s5", "s3"), LastPlayed.queue.value?.tracks?.map { it.id })
    }

    @Test fun emptyQueueAndIncognitoSaveNothing() {
        LastPlayed.saveQueue(ctx, five, t(1)); LastPlayed.awaitQueueWrites()
        LastPlayed.saveQueue(ctx, emptyList(), null); LastPlayed.awaitQueueWrites() // Stop
        Privacy.setIncognito(ctx, true)
        LastPlayed.saveQueue(ctx, listOf(t(9)), t(9)); LastPlayed.awaitQueueWrites()
        newProcess()
        assertEquals(five.map { it.id }, LastPlayed.queue.value?.tracks?.map { it.id })
    }

    @Test fun clearRemovesTheFile() {
        LastPlayed.saveQueue(ctx, five, t(1)); LastPlayed.awaitQueueWrites()
        LastPlayed.clear(ctx); LastPlayed.awaitQueueWrites()
        assertTrue(!file.exists())
        newProcess()
        assertNull(LastPlayed.queue.value)
    }

    @Test fun longQueueIsSavedAsAWindowAroundTheCurrentSong() {
        val many = (1..3000).map(::t)
        val w = LastPlayed.window(many, "s1500")
        assertEquals(LastPlayed.QUEUE_MAX, w.size)
        assertEquals("200 songs before the current one", "s1300", w.first().id)
        assertTrue(w.any { it.id == "s1500" })
        // Near the end the window keeps 1000 songs and moves back (CI 44255824749 caught a wrong expectation here).
        assertEquals("s2001", LastPlayed.window(many, "s2500").first().id)
        assertEquals("s1", LastPlayed.window(many, "s1").first().id)
        assertEquals("s3000", LastPlayed.window(many, "s3000").last().id)
        assertEquals(five, LastPlayed.window(five, "s2"))
    }
}
