@file:Suppress("unused", "RedundantSuspendModifier")

package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo

object TorrentUtils {
    suspend fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        throw UnsupportedOperationException("Torrent sources are not supported in Yumeiro yet")
    }
}
