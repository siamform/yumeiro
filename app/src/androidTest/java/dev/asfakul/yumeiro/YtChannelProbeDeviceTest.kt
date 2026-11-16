/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.youtube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-2 (E5): does the bundled NewPipeExtractor still load a channel (header, videos tab) and find
 * channels by search, on a real emulator with network? Each step logs YuChannel so a failure says
 * which part of YouTube broke, not only "channel empty".
 */
@RunWith(AndroidJUnit4::class)
class YtChannelProbeDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun channelPageAndChannelSearchLoad() = runBlocking {
        val url = "https://www.youtube.com/@GoogleDevelopers"
        val steps = mutableListOf<String>()
        assertTrue("handle link is a channel link", YouTube.isChannelUrl(url))
        val ch = runCatching { YouTube.channel(ctx, url) }
            .onFailure { Log.w("YuChannel", "CHANNEL_HEADER FAIL", it) }.getOrNull()
        steps += "header=" + (ch?.let { "ok name='${it.name}' subs=${it.subscribers} tabs=${it.tabs}" } ?: "FAIL")
        val videos = ch?.tabs?.firstOrNull()?.let { tab ->
            runCatching { YouTube.channelTab(ctx, url, tab) }.onFailure { Log.w("YuChannel", "CHANNEL_TAB FAIL", it) }.getOrNull()
        }
        steps += "tab=" + (videos?.let { "ok items=${it.items.size} next=${it.next != null}" } ?: "FAIL")
        val found = YouTube.searchChannels(ctx, "Google for Developers")
        steps += "search=" + found.size + " first='" + (found.firstOrNull()?.name ?: "") + "'"
        val pass = ch != null && ch.name.isNotBlank() && (videos?.items?.size ?: 0) > 0 && found.isNotEmpty()
        Log.i("YuChannel", "CHANNEL_PROBE_RESULT ${if (pass) "PASS" else "FAIL"} ${steps.joinToString(" ")}")
        assertTrue("channel probe: $steps", pass)
    }
}
