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
                val seenIds = runBlocking { repo.audioTracks.first() }.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id in idSet } }
                val seen = seenIds.size
                val tRecent = runBlocking { repo.recentlyPlayedTracks.first() }.size
                times += tScan
                log("LL_SCAN round=$round n=$N seenInserted=$seen scanMs=$tScan recentResolved=$tRecent")
                if (seen < N) {
                    // FINAL-14 (api33-lowram P1, run 44627424706: 4999 of 5000): say which rows were missed
                    // and what MediaStore itself holds for them right now (its scanner rescans each file
                    // after IS_PENDING is cleared and may change a row's flags).
                    val missing = idSet - seenIds
                    val store = musicIdsInStore()
                    missing.take(5).forEach { id -> log("LL_MISSING id=$id inStoreAsMusic=${id in store} row=${rowOf(id.toLong())}") }
                    val storeMissed = missing.count { it in store }
                    log("LL_MISSING total=${missing.size} storeListsAsMusic=$storeMissed")
                    // The contract: the scan returns every song MediaStore lists as music (the app's own
                    // selection). A row MediaStore no longer lists as music (its row is logged above) is not
                    // the app's miss; the run records it as a MediaStore finding instead.
                    assertTrue("scan missed $storeMissed songs that MediaStore lists as music (of ${missing.size} missing)", storeMissed == 0)
                }
            }
            log("LL_RESULT n=$N scanMs=${times.joinToString("/")} median=${times.sorted()[1]}")
        } finally {
            val tDel = SystemClock.elapsedRealtime()
            ids.forEach { id -> runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), null, null) } }
            ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE).edit().remove("recently_played_ids").commit()
            log("LL_CLEANUP removed=${ids.size} ms=${SystemClock.elapsedRealtime() - tDel}")
        }
    }

    private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 10000"
    private fun musicIdsInStore(): Set<String> = HashSet<String>().also { out ->
        ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID), selection, null, null)
            ?.use { c -> while (c.moveToNext()) out += c.getLong(0).toString() }
    }
    private fun rowOf(id: Long): String = runCatching {
        ctx.contentResolver.query(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
            arrayOf(MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) "is_music=${c.getString(0)} duration=${c.getString(1)} mime=${c.getString(2)} name=${c.getString(3)}" else "no row" } ?: "query null"
    }.getOrElse { "error ${it.javaClass.simpleName}" }

    private companion object {
        const val N = 5000
        const val FOLDER = "YuLargeLibrary"
    }
}
