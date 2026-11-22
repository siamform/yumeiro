/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OpeningKinds
import dev.asfakul.yumeiro.ui.screens.watch.WatchDetailsSkeleton
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C6 (original rounds 1 to 3): "skeleton or universal loader by video type". Until now this was a code read only.
 * Here the real skeleton is drawn for each tap: a media link gets the player skeleton, a source seen for the first
 * time gets the neutral loader, and a source that opened a page twice in a row gets the page / series shape.
 * Only one shape is on screen at a time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class C6LoaderTest {
    @get:Rule val rule = createComposeRule()

    private val tags = listOf("watch_skeleton_neutral", "watch_skeleton_direct", "watch_skeleton_page", "watch_skeleton_movie", "watch_skeleton_series")

    private fun kindFor(ctx: Context, item: OnlineItem) = WatchKind.forOpening(item.type, item.url, OpeningKinds.opensPage(ctx, item))

    @Test fun eachTapDrawsTheRightLoader() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val film = OnlineItem(name = "A film", url = "https://site.example/title/9", api = "C6Ext", posterUrl = null, type = "Movie")
        val show = OnlineItem(name = "A show", url = "https://site.example/show/4", api = "C6Ext", posterUrl = null, type = "TvSeries")
        val file = OnlineItem(name = "A file", url = "https://cdn.example/v/clip.m3u8", api = "C6Ext", posterUrl = null, type = null)
        var kind by mutableStateOf(WatchKind.NEUTRAL)
        rule.setContent { MTPlayerTheme { WatchDetailsSkeleton(kind) } }

        fun shows(expected: String, why: String) {
            rule.waitForIdle()
            tags.forEach { t ->
                val n = rule.onAllNodesWithTag(t).fetchSemanticsNodes().size
                assertEquals("$why: $t", if (t == expected) 1 else 0, n)
            }
        }

        kind = kindFor(ctx, file); shows("watch_skeleton_direct", "a media link plays at once")
        kind = kindFor(ctx, film); shows("watch_skeleton_neutral", "first tap on an unknown source")
        OpeningKinds.learn(ctx, film, opensPage = true)
        kind = kindFor(ctx, film); shows("watch_skeleton_neutral", "one page open is not enough")
        OpeningKinds.learn(ctx, film, opensPage = true)
        kind = kindFor(ctx, film); shows("watch_skeleton_page", "opened a page twice: page shape")
        OpeningKinds.learn(ctx, show, opensPage = true); OpeningKinds.learn(ctx, show, opensPage = true)
        kind = kindFor(ctx, show); shows("watch_skeleton_series", "a series source that opens pages: season and episode rows")
        OpeningKinds.learn(ctx, film, opensPage = false)
        kind = kindFor(ctx, film); shows("watch_skeleton_neutral", "the source changed: back to neutral")
        kind = WatchKind.MOVIE; shows("watch_skeleton_movie", "film shape")
    }
}
