/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {
    private val day = 86_400_000L
    private fun item(id: String, folder: String, status: Status, keep: Boolean, order: Int = 0, finished: Long = 10 * day) =
        Item(id, "t", "api", "u", null, null, emptyMap(), "", false, emptyMap(), "f", status = status, keep = keep,
            folder = folder, order = order, finishedAt = if (status == Status.DONE) finished else 0, createdAt = order.toLong(), bytes = 100)

    @Test fun splitsActivePermanentAndOffline() {
        val s = DownloadGroups.split(listOf(
            item("a", "Show-(Ext-(Sub))", Status.DONE, keep = true, order = 2),
            item("b", "Show-(Ext-(Sub))", Status.DONE, keep = true, order = 1),
            item("c", "Show-(Ext-(Sub))", Status.DONE, keep = false, order = 3),
            item("d", "YouTube", Status.RUNNING, keep = true),
            item("e", "", Status.DONE, keep = false),
            item("f", "Show-(Ext-(Sub))", Status.FAILED, keep = false),
        ))
        assertEquals(setOf("d", "f"), s.active.map { it.id }.toSet())
        assertEquals(1, s.permanent.size)
        assertEquals(listOf("b", "a"), s.permanent.single().items.map { it.id })
        assertEquals(2, s.permanentCount)
        assertEquals(setOf("Show-(Ext-(Sub))", DownloadGroups.LOOSE), s.offline.map { it.name }.toSet())
        assertEquals(2, s.offlineCount)
        assertEquals(200L, s.permanent.single().bytes)
    }

    @Test fun emptySplit() = assertTrue(DownloadGroups.split(emptyList()).isEmpty)

    @Test fun daysLeftBeforeAutoDelete() {
        val done = item("a", "x", Status.DONE, keep = false, finished = 10 * day)
        assertEquals(3, DownloadGroups.daysLeft(done, 3, 10 * day))
        assertEquals(1, DownloadGroups.daysLeft(done, 3, 12 * day + 1))
        assertEquals(0, DownloadGroups.daysLeft(done, 3, 20 * day))
        assertNull(DownloadGroups.daysLeft(done, 0, 10 * day))
        assertNull(DownloadGroups.daysLeft(done.copy(keep = true), 3, 10 * day))
    }
}
