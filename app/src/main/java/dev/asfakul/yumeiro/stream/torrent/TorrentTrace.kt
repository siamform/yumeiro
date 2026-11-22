/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

/**
 * B31-SEEK3 diagnostics: when set (device tests only; null in the app), a waiting [TorrentSource] read reports once a
 * second which block it needs, how many blocks of that piece are in, peers and download rate. Off = one null check.
 */
object TorrentTrace {
    @Volatile var sink: ((String) -> Unit)? = null
}
