/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * FREEZE-1: keeps one computed value until [stamp] changes. Stamps are compared with equals, so a stamp
 * built from identities and counts ([ExtensionManager.providerStamp]) recomputes on every extension change.
 */
class StampMemo<V> {
    private class Entry<V>(val stamp: Any, val value: V)
    @Volatile private var entry: Entry<V>? = null

    fun get(stamp: Any, compute: () -> V): V {
        entry?.let { if (it.stamp == stamp) return it.value }
        val value = compute()
        entry = Entry(stamp, value)
        return value
    }
}
