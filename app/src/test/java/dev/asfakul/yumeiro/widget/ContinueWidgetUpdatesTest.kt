/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.security.Privacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.Socket

/**
 * G12 "widget data updates" (deferred from OMT-8): the placed widget follows the watch history.
 * The widget is hosted by Robolectric's AppWidgetManager; every update the app sends is applied to
 * the hosted view, which is what a launcher shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContinueWidgetUpdatesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private var id = 0

    @Before fun place() {
        OnlineHistory.clear(ctx)
        Privacy.setIncognito(ctx, false)
        id = shadowOf(AppWidgetManager.getInstance(ctx)).createWidget(ContinueWidget::class.java, R.layout.widget_continue)
        ContinueWidget.refresh(ctx)
        awaitTitle(ctx.getString(R.string.widget_empty_title))
    }

    @After fun tidy() { Privacy.setIncognito(ctx, false); OnlineHistory.clear(ctx) }

    private fun view(): View = shadowOf(AppWidgetManager.getInstance(ctx)).getViewFor(id)
    private fun title() = view().findViewById<TextView>(R.id.widget_title).text.toString()
    private fun subtitle() = view().findViewById<TextView>(R.id.widget_subtitle).text.toString()

    private fun awaitTitle(want: String, ms: Long = 5_000) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (title() == want) return
            Thread.sleep(25)
        }
        assertEquals("widget title", want, title())
    }

    private fun detail(name: String, poster: String? = null) = OnlineDetail(
        "Demo", "https://x/$name", name, OnlineKind.SERIES, "TvSeries", poster, null, emptyMap(), null, 2020,
        emptyList(), null, null, null, null, emptyMap(), emptyMap(), emptyList(),
    )
    private fun ep(n: Int) = OnlineEpisode("ep-$n", null, 1, n, null, null, 24)

    @Test fun showsTheLastWatchedTitleAndItsProgress() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        assertTrue(subtitle(), subtitle().startsWith("E1"))
        assertEquals(View.VISIBLE, view().findViewById<View>(R.id.widget_progress).visibility)
    }

    @Test fun aTitlePlayedAgainAfterRemovalComesBack() {
        // remove() refreshed the widget to empty; the next save of the same title was throttled for
        // 2 minutes (same key), so the widget stayed empty while the title was playing.
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        OnlineHistory.remove(ctx, "Demo", "https://x/Frieren")
        awaitTitle(ctx.getString(R.string.widget_empty_title))
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 310_000, 1_200_000)
        awaitTitle("Frieren")
    }

    @Test fun theNextEpisodeShowsAtOnce() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 1_150_000, 1_200_000)
        awaitTitle("Frieren")
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(2), 5_000, 1_200_000)
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && !subtitle().startsWith("E2")) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(25) }
        assertTrue("still shows the old episode: ${subtitle()}", subtitle().startsWith("E2"))
    }

    @Test fun incognitoHidesAndRestores() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        Privacy.setIncognito(ctx, true)
        awaitTitle(ctx.getString(R.string.widget_empty_title))
        assertTrue(subtitle(), subtitle().contains("Hidden"))
        Privacy.setIncognito(ctx, false)
        awaitTitle("Frieren")
    }

    @Test fun aSlowPosterNeverPaintsAnOlderStateOverANewerOne() {
        // A server that accepts and never answers: the poster load waits for its 8 s timeout.
        ServerSocket(0).use { server ->
            val held = mutableListOf<Socket>()
            val acceptor = Thread { runCatching { while (true) held += server.accept() } }.apply { isDaemon = true; start() }
            OnlineHistory.record(ctx, detail("Frieren", "http://127.0.0.1:${server.localPort}/p.jpg"), "", ep(1), 300_000, 1_200_000)
            Thread.sleep(500) // the render for Frieren is now waiting on the poster
            OnlineHistory.remove(ctx, "Demo", "https://x/Frieren")
            awaitTitle(ctx.getString(R.string.widget_empty_title))
            // Let the old render's poster time out; it must not bring the removed title back.
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end) {
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("a stale render repainted a removed title", ctx.getString(R.string.widget_empty_title), title())
                Thread.sleep(100)
            }
            acceptor.interrupt(); held.forEach { runCatching { it.close() } }
        }
    }
}
