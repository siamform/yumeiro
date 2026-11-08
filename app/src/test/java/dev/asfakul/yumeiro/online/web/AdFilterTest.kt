/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFilterTest {
    @Test fun blocksAdServersIncludingSubdomains() {
        assertTrue(AdFilter.isAd("https://pubads.g.doubleclick.net/gampad/ads?iu=x"))
        assertTrue(AdFilter.isAd("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
        assertTrue(AdFilter.isAd("https://syndication.exoclick.com/splash.php"))
        assertTrue(AdFilter.isAd("http://a.popads.net/pop.js"))
    }

    @Test fun blocksVideoAdPathsOnOtherHosts() {
        assertTrue(AdFilter.isAd("https://player.example.com/vast.xml?zone=1"))
        assertTrue(AdFilter.isAd("https://cdn.example.com/preroll/clip.mp4"))
        assertTrue(AdFilter.isAd("https://cdn.example.com/x?adtag=1"))
    }

    @Test fun neverBlocksRealStreams() {
        assertFalse(AdFilter.isAd("https://cdn1.video-host.net/hls/ep1/index.m3u8"))
        assertFalse(AdFilter.isAd("https://uploads.example.com/road/master.m3u8"))
        assertFalse(AdFilter.isAd("https://www.googleapis.com/youtube/v3/videos"))
        assertFalse(AdFilter.isAd("blob:https://x/1"))
        assertFalse(AdFilter.isAd("https://headless.example/advanced/file.mp4"))
    }
}
