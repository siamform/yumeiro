/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BUG-2 (E3, E4, E1): channel links survive history, shared channel links are recognised, launch is one-shot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YtChannelLinkTest {
    private val ch = "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw"

    @Test fun historyKeepsTheChannelLink() {
        val item = YtItem("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "T", "Chan", channelUrl = ch, channelAvatar = "https://a/x.jpg")
        val back = YtHistory.decode(YtHistory.encode(listOf(item))).single()
        assertEquals(ch, back.channelUrl)
        assertEquals("https://a/x.jpg", back.channelAvatar)
    }

    @Test fun oldHistoryWithoutChannelStillDecodes() {
        val old = """[{"id":"abc","url":"https://www.youtube.com/watch?v=abc","t":"T","c":"Chan","th":"","d":10,"m":false,"l":false}]"""
        val back = YtHistory.decode(old).single()
        assertEquals("abc", back.id)
        assertNull(back.channelUrl)
        assertNull(back.channelAvatar)
    }

    @Test fun replayWithoutChannelKeepsTheLearnedOne() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        YtHistory.clear(ctx)
        val bare = YtItem("abc", "https://www.youtube.com/watch?v=abc", "T", "Chan")
        YtHistory.add(ctx, bare)
        YtHistory.learnChannel(ctx, "abc", ch, null)
        assertEquals(ch, YtHistory.items.value.first().channelUrl)
        YtHistory.add(ctx, bare)
        assertEquals(ch, YtHistory.items.value.first().channelUrl)
        YtHistory.clear(ctx)
    }

    @Test fun channelLinksAreRecognised() {
        assertTrue(YouTube.isChannelUrl("https://www.youtube.com/@MrBeast"))
        assertTrue(YouTube.isChannelUrl(ch))
        assertTrue(YouTube.isChannelUrl("https://www.youtube.com/user/GoogleDevelopers"))
        assertTrue(YouTube.isChannelUrl("https://www.youtube.com/c/GoogleDevelopers"))
        assertFalse(YouTube.isChannelUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YouTube.isChannelUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(YouTube.isChannelUrl("https://example.com/@someone"))
    }

    @Test fun launchIsTakenOnce() {
        YtChannelLaunch.open(ch, "Chan")
        assertEquals(YtChannelLaunch.Request(ch, "Chan"), YtChannelLaunch.take())
        assertNull(YtChannelLaunch.take())
    }
}
