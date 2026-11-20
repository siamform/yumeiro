/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import dev.asfakul.yumeiro.player.BackgroundPlaybackWatch
import dev.asfakul.yumeiro.player.LastPlayed
import dev.asfakul.yumeiro.player.ListeningTime
import dev.asfakul.yumeiro.service.PlaybackService
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G5 from an empty profile (master prompt §8 L2 G5): empty → like / add / play → counts, play
 * time, order; kill and reopen; Jump back in right away.
 *
 * `scripts/device-check.sh` runs `pm clear` first, so phase a starts with no app data.
 *
 * Phase a:
 * - Fresh-install checks: no history, no counts, no listening time, no Home Continue, and only
 *   the empty internal liked list.
 * - Adds three 12 s songs to MediaStore, opens MainActivity and uses its own ViewModel.
 * - Likes B then A, and makes a playlist [C, A].
 * - Plays A, B, C as a queue with a real pause (excluded from play time) and a forward seek.
 * - Once B plays, destroys the activity (what swiping from recents does to the screen). The music
 *   goes on, and C starts by itself with no screen.
 * - When the queue ends, records what was saved and kills its own process.
 *
 * Phase b (a new process): history, counts, likes, playlist and play time survived the kill.
 * - G5-PC1: C, played with no screen, is counted, and Recently played is C, B, A.
 * - G5-PC2: Home Continue (Jump back in) shows C as soon as the app opens.
 * Then it removes what it added.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LibraryEmptyProfileDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private fun log(s: String) = Log.i("YuEmptyProfile", s)
    private val music get() = ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE)
    private val notes get() = ctx.getSharedPreferences("yu_test_empty_profile", Context.MODE_PRIVATE)

    private fun counts(): Map<String, Int> = org.json.JSONObject(music.getString("play_counts_json", null) ?: "{}")
        .let { o -> o.keys().asSequence().associateWith { o.optInt(it) } }
    private fun recent(): List<String> = music.getString("recently_played_ids", null).orEmpty().split('\n').filter { it.isNotBlank() }

    private fun waitFor(what: String, ms: Long, cond: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < ms) { if (cond()) return; Thread.sleep(200) }
        throw AssertionError("timed out after $ms ms: $what")
    }

    private fun <T> await(what: String, ms: Long, block: suspend () -> T): T = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeoutOrNull(ms) { block() } ?: throw AssertionError("timed out after $ms ms: $what")
    }

    private fun current(): String? { var id: String? = null; inst.runOnMainSync { id = PlaybackService.sharedPlayer?.currentMediaItem?.mediaId }; return id }
    private fun playing(): Boolean { var p = false; inst.runOnMainSync { p = PlaybackService.sharedPlayer?.isPlaying == true }; return p }

    private fun addSong(title: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                // FINAL-24 (run 44659196962, tv-api24): before Android 10 MediaStore needs the file path (DATA),
                // else its insert throws NullPointerException in MediaProvider.
                put(MediaStore.Audio.Media.DATA, legacyMusicFile("$title.m4a"))
            }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("MediaStore insert failed for $title")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> inst.context.assets.open("yt_audio_only.m4a").use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return android.content.ContentUris.parseId(uri)
    }

    private fun removeSongs() {
        val ids = notes.getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        ids.forEach { id ->
            runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), null, null) }
        }
        log("EP cleaned songs=$ids")
    }

    private fun openApp(): Pair<ActivityScenario<MainActivity>, MainViewModel> {
        val sc = ActivityScenario.launch(MainActivity::class.java)
        var vm: MainViewModel? = null
        sc.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        return sc to vm!!
    }

    @Test fun a_emptyProfileLikeAddPlayThenDie() {
        assumeTrue(phase == "a")
        // ---- empty profile: nothing invented ----
        val fresh = OfflineMediaRepository(ctx)
        val pls = fresh.playlists.value
        log("EP_A fresh playlists=${pls.map { it.id + ":" + it.trackIds.size }} recent=${recent()} counts=${counts()}")
        assertTrue("fresh install has history: ${recent()}", recent().isEmpty())
        assertTrue("fresh install has play counts: ${counts()}", counts().isEmpty())
        assertEquals("fresh install has demo playlists", listOf("favorites_default"), pls.map { it.id })
        assertTrue("fresh liked list is not empty", pls.first().trackIds.isEmpty())
        ListeningTime.load(ctx)
        assertEquals("fresh install has listening time", 0L, ListeningTime.total.value)
        LastPlayed.init(ctx)
        assertTrue("fresh install has a Home Continue song", LastPlayed.audio.value == null)

        // ---- three songs on the device ----
        val ids = listOf("YuEP Song A", "YuEP Song B", "YuEP Song C").map { addSong(it).toString() }
        notes.edit().putString("ids", ids.joinToString(",")).commit()
        val (a, b, c) = ids
        log("EP_A songs A=$a B=$b C=$c")

        val (sc, vm) = openApp()
        inst.runOnMainSync { vm.scanMedia() }
        val tracks: List<AudioTrack> = await("the three songs in the library", 30_000) {
            vm.rawAudioTracks.first { l -> l.count { it.id in ids } == 3 }.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
        }

        // ---- like B then A, playlist [C, A], through the screen's own store (one writer) ----
        inst.runOnMainSync { vm.onToggleFavorite(b) }
        Thread.sleep(300)
        inst.runOnMainSync { vm.onToggleFavorite(a) }
        Thread.sleep(300)
        inst.runOnMainSync { vm.createPlaylist(LIST, listOf(c, a)) }
        Thread.sleep(500)

        // ---- play A, B, C with a pause and a seek ----
        val before = ListeningTime.total.value
        inst.runOnMainSync { vm.playTrack(tracks[0], tracks); PlaybackService.sharedPlayer?.volume = 0f }
        waitFor("A playing", 20_000) { current() == a && playing() }
        Thread.sleep(3_000)
        inst.runOnMainSync { PlaybackService.sharedPlayer?.pause() }
        Thread.sleep(8_000) // paused: must add nothing
        inst.runOnMainSync { PlaybackService.sharedPlayer?.seekTo(8_000); PlaybackService.sharedPlayer?.play() } // skips ~5 s of A
        waitFor("B playing", 20_000) { current() == b && playing() }
        Thread.sleep(1_000)

        // ---- the screen goes away (swipe from recents); music goes on ----
        sc.close()
        log("EP_A screen closed at B; playing=${playing()}")
        waitFor("C started with no screen", 25_000) { current() == c }
        waitFor("queue ended", 25_000) { !playing() }
        Thread.sleep(2_000) // apply() writes; a person does not kill the app within 1 s
        val gained = ListeningTime.total.value - before
        val w = BackgroundPlaybackWatch.state.value
        log("EP_A after queue: counts=${counts()} recent=${recent()} gainedMs=$gained watch=${w.currentTrack?.id}")
        notes.edit().putLong("gained", gained).putLong("total", ListeningTime.total.value).commit()
        // Played: A 3 s + (12 - 8) s, B 12 s, C 12 s ≈ 31 s. The 8 s pause and the skipped 5 s don't count.
        assertTrue("play time $gained ms is outside 25..36 s (pause or seek counted?)", gained in 25_000..36_000)
        log("EP_A pid=${android.os.Process.myPid()} kill")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_newProcessShowsHistoryRightAway() {
        assumeTrue(phase == "b")
        val ids = notes.getString("ids", null)?.split(',').orEmpty()
        assertEquals("phase a did not finish", 3, ids.size)
        val (a, b, c) = ids
        // Every check runs and every miss is reported, so one run shows each item that is wrong.
        val miss = mutableListOf<String>()
        fun check(what: String, expected: Any?, actual: Any?) { if (expected != actual) miss += "$what: expected $expected, was $actual" }
        try {
            log("EP_B pid=${android.os.Process.myPid()} counts=${counts()} recent=${recent()}")
            val n = counts()
            check("A count", 1, n[a] ?: 0)
            check("B count", 1, n[b] ?: 0)
            check("G5-PC1 C (played with no screen) count", 1, n[c] ?: 0)
            check("G5-PC1 Recently played order", listOf(c, b, a), recent().take(3))

            val repo = OfflineMediaRepository(ctx)
            check("liked order", listOf(b, a), repo.playlists.value.first { it.id == "favorites_default" }.trackIds.filter { it in ids })
            check("playlist order", listOf(c, a), repo.playlists.value.firstOrNull { it.name == LIST }?.trackIds)

            ListeningTime.load(ctx)
            check("play time after the kill", notes.getLong("total", -1), ListeningTime.total.value)

            // ---- reopen: Jump back in right away ----
            val t0 = System.currentTimeMillis()
            val (sc, vm) = openApp()
            val tOpen = System.currentTimeMillis() - t0
            try {
                val cont = runCatching { waitFor("Home Continue", 5_000) { LastPlayed.audio.value?.track?.id == c } }
                    .let { LastPlayed.audio.value?.track?.id }
                val tCont = System.currentTimeMillis() - t0
                check("G5-PC2 Home Continue (Jump back in) song", c, cont)
                val shown = kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) { vm.recentlyPlayedTracks.first { l -> l.map { it.id }.filter { it in ids } == listOf(c, b, a) } }
                        ?.map { it.id }?.filter { it in ids } ?: vm.recentlyPlayedTracks.value.map { it.id }.filter { it in ids }
                }
                val tRecent = System.currentTimeMillis() - t0
                check("Library Jump back in order", listOf(c, b, a), shown)
                val most = kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) { vm.mostPlayedTracks.first { l -> l.count { it.id in ids } == 3 } }
                        ?.map { it.id }?.filter { it in ids } ?: vm.mostPlayedTracks.value.map { it.id }.filter { it in ids }
                }
                check("Most played songs", ids.toSet(), most.toSet())
                log("EP_B timing open=${tOpen} continue=${tCont} libraryJumpBackIn=${tRecent} ms (from launch)")
                log("EP_B reopen ${System.currentTimeMillis() - t0} ms: continue=$cont jumpBackIn=$shown mostPlayed=$most")
            } finally { sc.close() }
            miss.forEach { log("EP_MISS $it") }
            assertTrue("G5 empty profile: ${miss.size} wrong\n" + miss.joinToString("\n"), miss.isEmpty())
            log("EP_RESULT PASS")
        } finally {
            val repo = OfflineMediaRepository(ctx)
            kotlinx.coroutines.runBlocking {
                repo.playlists.value.filter { it.name == LIST }.forEach { repo.deletePlaylist(it.id) }
                ids.forEach { id -> if (repo.playlists.value.first { it.id == "favorites_default" }.trackIds.contains(id)) repo.toggleFavorite(id) }
            }
            removeSongs()
            Thread.sleep(1_000)
        }
    }

    private companion object {
        const val FOLDER = "YuEmptyProfile"
        const val LIST = "YuEP list"
    }

    /** Android 7–9: the path MediaStore indexes for a new song in Music/FOLDER (the folder is made first). */
    private fun legacyMusicFile(name: String): String {
        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), FOLDER)
        dir.mkdirs()
        return java.io.File(dir, name).absolutePath
    }
}
