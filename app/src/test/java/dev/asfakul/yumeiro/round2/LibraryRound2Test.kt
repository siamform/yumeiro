/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import dev.asfakul.yumeiro.player.ListeningTime
import dev.asfakul.yumeiro.ui.screens.library.listenedLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round 2, order 5: Library shows only real likes, user playlists and measured play time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRound2Test {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun freshInstallHasNoDemoPlaylists() = runBlocking {
        ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val ids = OfflineMediaRepository(ctx).playlists.first().map { it.id }
        assertEquals(listOf("favorites_default"), ids)
    }

    @Test fun seededDemoPlaylistsFromOldVersionsAreRemovedUnlessUsed() = runBlocking {
        val old = """[{"id":"favorites_default","name":"Favorite Tracks","trackIds":[]},{"id":"workout_mix","name":"Workout Energy","trackIds":[]},{"id":"chill_vibes","name":"Late Night Chill","trackIds":["42"]},{"id":"p1","name":"Mine","trackIds":[]}]"""
        ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE).edit().clear().putString("playlists_json", old).commit()
        val ids = OfflineMediaRepository(ctx).playlists.first().map { it.id }
        assertTrue(ids.toString(), "workout_mix" !in ids)
        assertTrue(ids.toString(), "p1" in ids && "chill_vibes" in ids) // user's own, and a demo they filled
    }

    @Test fun listenedLabelAndAccumulator() {
        assertEquals("0m", listenedLabel(0)); assertEquals("42m", listenedLabel(42 * 60_000L)); assertEquals("3h 05m", listenedLabel(185 * 60_000L))
        assertEquals(1_000L, ListeningTime.add(0, 1_000)); assertEquals(60_000L, ListeningTime.add(0, 3_600_000)); assertEquals(5L, ListeningTime.add(5, -10))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun clockRunsOnlyWhilePlaying() {
        val scope = TestScope()
        var now = 0L
        val playing = MutableStateFlow(false)
        val before = ListeningTime.total.value
        ListeningTime.track(ctx, scope, playing) { now }
        scope.runCurrent()
        now += 120_000; scope.advanceTimeBy(120_000); scope.runCurrent()
        assertEquals(before, ListeningTime.total.value) // paused: nothing counted
        playing.value = true; scope.runCurrent()
        repeat(4) { now += 30_000; scope.advanceTimeBy(30_000); scope.runCurrent() }
        playing.value = false; scope.runCurrent()
        assertEquals(before + 120_000, ListeningTime.total.value)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
