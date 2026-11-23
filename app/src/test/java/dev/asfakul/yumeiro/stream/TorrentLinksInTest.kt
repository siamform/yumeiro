/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TOR-1 (owner list 12): an info-hash or magnet link from outside gets to the torrent player. The owner's example
 * hash BF2063A5E9A69F14716288FCA7C2D6078BDA7369 is used as given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TorrentLinksInTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val hash = "BF2063A5E9A69F14716288FCA7C2D6078BDA7369"

    @Test fun sharedTextFindsMagnetOrHash() {
        assertEquals("magnet:?xt=urn:btih:${hash.lowercase()}", IncomingMedia.linkFromText(hash))
        assertEquals("magnet:?xt=urn:btih:${hash.lowercase()}", IncomingMedia.linkFromText("  urn:btih:$hash \n"))
        val m = "magnet:?xt=urn:btih:$hash&dn=Big+Buck+Bunny"
        assertEquals(m, IncomingMedia.linkFromText("Watch this: $m"))
        assertEquals("https://example.com/a.m3u8", IncomingMedia.linkFromText("see https://example.com/a.m3u8."))
        assertNull(IncomingMedia.linkFromText("hello there"))
        assertTrue(LinkHosts.isTorrent(IncomingMedia.linkFromText(hash)!!))
    }

    @Test fun magnetViewAndSharedHashOpenThePlayer() {
        IncomingMedia.consume()
        val m = "magnet:?xt=urn:btih:$hash"
        assertTrue(IncomingMedia.handle(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(m))))
        assertEquals(m, IncomingMedia.pending.value?.uri)
        IncomingMedia.consume()
        assertTrue(IncomingMedia.handle(ctx, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, hash)))
        assertEquals("magnet:?xt=urn:btih:${hash.lowercase()}", IncomingMedia.pending.value?.uri)
        IncomingMedia.consume()
    }

    @Test fun manifestOffersYumeiroForMagnetLinks() {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("magnet:?xt=urn:btih:$hash")).addCategory(Intent.CATEGORY_BROWSABLE)
        val found = ctx.packageManager.queryIntentActivities(view, 0).map { it.activityInfo.packageName }
        assertTrue("magnet: resolves to $found", ctx.packageName in found)
    }
}
