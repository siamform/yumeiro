/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.widget.ContinueWidget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 "widget data updates" (deferred from OMT-8) on an emulator: the real Continue widget is bound
 * to an AppWidgetHost, as a launcher does, and every update goes through the system
 * AppWidgetService to the hosted view. Covers the three bugs found by ContinueWidgetUpdatesTest
 * (re-added title, next episode, no stale repaint) plus progress and incognito.
 * Debug app only; the test clears the debug app's online history.
 */
@RunWith(AndroidJUnit4::class)
class WidgetUpdatesDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private lateinit var host: AppWidgetHost
    private lateinit var view: AppWidgetHostView
    private var id = 0

    private fun log(s: String) = Log.i("YuWidgetTest", s)

    @Before fun bind() {
        FirstRun.shell("appwidget grantbind --package ${ctx.packageName} --user 0")
        OnlineHistory.clear(ctx)
        Privacy.setIncognito(ctx, false)
        val manager = AppWidgetManager.getInstance(ctx)
        inst.runOnMainSync {
            host = AppWidgetHost(ctx, HOST_ID)
            host.startListening()
            id = host.allocateAppWidgetId()
        }
        val bound = manager.bindAppWidgetIdIfAllowed(id, ComponentName(ctx, ContinueWidget::class.java))
        assertTrue("could not bind the widget (grantbind)", bound)
        inst.runOnMainSync { view = host.createView(ctx, id, manager.getAppWidgetInfo(id)) }
        ContinueWidget.refresh(ctx)
        awaitTitle(ctx.getString(R.string.widget_empty_title))
    }

    @After fun unbind() {
        Privacy.setIncognito(ctx, false)
        OnlineHistory.clear(ctx)
        inst.runOnMainSync { runCatching { host.deleteAppWidgetId(id); host.stopListening() } }
    }

    private fun text(viewId: Int): String {
        var s = ""
        inst.runOnMainSync { s = view.findViewById<TextView>(viewId)?.text?.toString().orEmpty() }
        return s
    }

    private fun await(what: String, ms: Long = 15_000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (ok()) return; Thread.sleep(100) }
        assertTrue("$what · title='${text(R.id.widget_title)}' subtitle='${text(R.id.widget_subtitle)}'", ok())
    }

    private fun awaitTitle(want: String) = await("widget title '$want'") { text(R.id.widget_title) == want }

    private fun detail(name: String, poster: String? = null) = OnlineDetail(
        "Demo", "https://x/$name", name, OnlineKind.SERIES, "TvSeries", poster, null, emptyMap(), null, 2020,
        emptyList(), null, null, null, null, emptyMap(), emptyMap(), emptyList(),
    )
    private fun ep(n: Int) = OnlineEpisode("ep-$n", null, 1, n, null, null, 24)

    @Test fun w1_titleAndProgress() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        assertTrue(text(R.id.widget_subtitle), text(R.id.widget_subtitle).startsWith("E1"))
        var vis = -1
        inst.runOnMainSync { vis = view.findViewById<View>(R.id.widget_progress).visibility }
        assertEquals("progress bar", View.VISIBLE, vis)
        log("w1 ok: ${text(R.id.widget_title)} / ${text(R.id.widget_subtitle)}")
    }

    @Test fun w2_removedThenPlayedAgain() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        OnlineHistory.remove(ctx, "Demo", "https://x/Frieren")
        awaitTitle(ctx.getString(R.string.widget_empty_title))
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 310_000, 1_200_000)
        awaitTitle("Frieren")
        log("w2 ok")
    }

    @Test fun w3_nextEpisode() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 1_150_000, 1_200_000)
        awaitTitle("Frieren")
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(2), 5_000, 1_200_000)
        await("next episode label") { text(R.id.widget_subtitle).startsWith("E2") }
        log("w3 ok: ${text(R.id.widget_subtitle)}")
    }

    @Test fun w4_incognitoHidesAndRestores() {
        OnlineHistory.record(ctx, detail("Frieren"), "", ep(1), 300_000, 1_200_000)
        awaitTitle("Frieren")
        Privacy.setIncognito(ctx, true)
        awaitTitle(ctx.getString(R.string.widget_empty_title))
        assertTrue(text(R.id.widget_subtitle), text(R.id.widget_subtitle).contains("Hidden"))
        Privacy.setIncognito(ctx, false)
        awaitTitle("Frieren")
        log("w4 ok")
    }

    @Test fun w5_slowPosterDoesNotRepaintARemovedTitle() {
        java.net.ServerSocket(0).use { server ->
            val held = java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())
            val acceptor = Thread { runCatching { while (true) held += server.accept() } }.apply { isDaemon = true; start() }
            OnlineHistory.record(ctx, detail("Frieren", "http://127.0.0.1:${server.localPort}/p.jpg"), "", ep(1), 300_000, 1_200_000)
            Thread.sleep(500)
            OnlineHistory.remove(ctx, "Demo", "https://x/Frieren")
            awaitTitle(ctx.getString(R.string.widget_empty_title))
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end) {
                assertEquals("a stale render repainted a removed title", ctx.getString(R.string.widget_empty_title), text(R.id.widget_title))
                Thread.sleep(200)
            }
            acceptor.interrupt(); held.forEach { runCatching { it.close() } }
        }
        log("w5 ok")
    }

    private companion object { const val HOST_ID = 0x5157 }
}
