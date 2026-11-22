/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-31: the built-in engine never plays before the user accepted the privacy notice (peers see the IP). */
class TorrentNoticeTest {
    @Test fun engineNeedsFlagEngineAndAcceptedNotice() {
        assertFalse(TorrentStreams.allowed(offered = true, accepted = false))
        assertFalse(TorrentStreams.allowed(offered = false, accepted = true))
        assertTrue(TorrentStreams.allowed(offered = true, accepted = true))
    }

    @Test fun noticeSaysWhatPeersSeeAndThatPiecesAreShared() {
        val n = TorrentStreams.PRIVACY_NOTICE
        assertTrue(n.contains("IP address")); assertTrue(n.contains("sends them")); assertTrue(n.contains("VPN"))
    }
}
