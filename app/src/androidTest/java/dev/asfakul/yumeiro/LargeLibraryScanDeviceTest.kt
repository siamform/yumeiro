/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G5 open item (GROUP-5, timing run 44203529014): Library "Jump back in" and "Most played" are built
 * from the scanned track list, and that list starts empty on every cold start until the MediaStore
 * scan ends. With 3 songs that wait was ~2 ms. This measures the wait with a large library:
 * inserts [N] small songs, then times three cold scans with a fresh repository (a new process has
 * no cached list either), and the time until recently played resolves to tracks.
 *
 * It measures; it fails only when the scan misses inserted songs. Logs `YuLibraryTest LL_…`.
 */
@RunWith(AndroidJUnit4::class)
class LargeLibraryScanDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)

    @Test fun scanTimeWithLargeLibrary() {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        // A real song head (4 KB): MediaStore indexes it as audio; the app's scan reads only the row.
        val head = inst.context.assets.open("yt_audio_only.m4a").use { s -> ByteArray(4096).also { s.read(it) } }
        val ids = ArrayList<Long>(N)
        val tInsert = SystemClock.elapsedRealtime()
        try {
            for (i in 0 until N) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "ll_$i.m4a")
                    put(MediaStore.Audio.Media.TITLE, "Large library $i")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$FOLDER"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
                }
                val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: break
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(head) }
                if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                ids += android.content.ContentUris.parseId(uri)
            }
            log("LL_INSERT n=${ids.size} ms=${SystemClock.elapsedRealtime() - tInsert} sdk=${Build.VERSION.SDK_INT}")
            assumeTrue("MediaStore accepted only ${ids.size} of $N rows", ids.size == N)

            // Recently played points at the last inserted songs, as after real use.
            val recent = ids.takeLast(15).reversed().joinToString("\n") { it.toString() }
            ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE).edit().putString("recently_played_ids", recent).commit()

            val idSet = ids.mapTo(HashSet()) { it.toString() }
            val times = ArrayList<Long>()
            repeat(3) { round ->
                val repo = OfflineMediaRepository(ctx)
                val t0 = SystemClock.elapsedRealtime()
                runBlocking { repo.scanDeviceMedia() }
                val tScan = SystemClock.elapsedRealtime() - t0
                val seen = runBlocking { repo.audioTracks.first() }.count { it.id in idSet }
                val tRecent = runBlocking { repo.recentlyPlayedTracks.first() }.size
                times += tScan
                log("LL_SCAN round=$round n=$N seenInserted=$seen scanMs=$tScan recentResolved=$tRecent")
                assertTrue("scan saw $seen of $N inserted songs", seen >= N)
            }
            log("LL_RESULT n=$N scanMs=${times.joinToString("/")} median=${times.sorted()[1]}")
        } finally {
            val tDel = SystemClock.elapsedRealtime()
            ids.forEach { id -> runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), null, null) } }
            ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE).edit().remove("recently_played_ids").commit()
            log("LL_CLEANUP removed=${ids.size} ms=${SystemClock.elapsedRealtime() - tDel}")
        }
    }

    private companion object {
        const val N = 5000
        const val FOLDER = "YuLargeLibrary"
    }
}
