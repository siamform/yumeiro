/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OpeningKinds
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round 2, order 3: a shaped skeleton only when it is certain; else the neutral loader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoaderRound2Test {
    @Test fun neutralUntilTheSourceIsKnown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val item = OnlineItem(name = "T", url = "https://site/title/1", api = "SomeExt", posterUrl = null, type = "Movie")
        val kind = { WatchKind.forOpening(item.type, item.url, OpeningKinds.opensPage(ctx, item)) }
        assertEquals(WatchKind.NEUTRAL, kind())
        OpeningKinds.learn(ctx, item, opensPage = true)
        assertEquals(WatchKind.NEUTRAL, kind()) // once is not enough
        OpeningKinds.learn(ctx, item, opensPage = true)
        assertEquals(WatchKind.PAGE, kind())
        OpeningKinds.learn(ctx, item, opensPage = false) // it changed its mind: back to neutral
        assertNull(OpeningKinds.opensPage(ctx, item))
        assertEquals(WatchKind.NEUTRAL, kind())
        assertEquals(WatchKind.DIRECT, WatchKind.forOpening(null, "https://cdn/x.m3u8", null))
    }
}
