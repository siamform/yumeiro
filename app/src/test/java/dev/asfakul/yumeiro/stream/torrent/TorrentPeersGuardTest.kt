/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B31-PEERS-1 (lowram run 45303433155): LtCore wraps native libtorrent and can't run in a unit test, so this guards
 * the one setting that keeps seeds connected when a stream window is all downloaded. Device proof: the B31 trace
 * keeps its peers after the first frame (B31FrameDeviceTest).
 */
class TorrentPeersGuardTest {
    @Test fun seedsStayConnectedWhenTheWindowIsDone() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val src = File(root, "dev/asfakul/yumeiro/stream/torrent/LtCore.kt").readText()
        assertTrue("close_redundant_connections must be off", src.contains("bool_types.close_redundant_connections, false)"))
    }
}
