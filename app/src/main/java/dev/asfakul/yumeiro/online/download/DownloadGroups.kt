/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

/**
 * How the Downloads page and Library sort downloads (pure, unit-tested):
 * - **In progress**: queued, running, paused or failed.
 * - **Permanent**: finished and pinned ("Keep forever"), never auto-deleted.
 * - **Offline**: finished and not pinned; removed by the auto-delete timer when it is on.
 * Inside each part, items are grouped by folder (series / YouTube / YouTube Music), newest first.
 */
object DownloadGroups {
    data class Folder(val name: String, val items: List<OnlineDownloads.Item>) {
        val bytes: Long get() = items.filter { it.status == OnlineDownloads.Status.DONE }.sumOf { it.bytes }
    }

    data class Split(val active: List<OnlineDownloads.Item>, val permanent: List<Folder>, val offline: List<Folder>) {
        val permanentCount get() = permanent.sumOf { it.items.size }
        val offlineCount get() = offline.sumOf { it.items.size }
        val isEmpty get() = active.isEmpty() && permanent.isEmpty() && offline.isEmpty()
    }

    fun split(items: List<OnlineDownloads.Item>): Split {
        val active = items.filter { it.status != OnlineDownloads.Status.DONE }.sortedByDescending { it.createdAt }
        val done = items.filter { it.status == OnlineDownloads.Status.DONE }
        return Split(active, folders(done.filter { it.keep }), folders(done.filterNot { it.keep }))
    }

    fun folders(items: List<OnlineDownloads.Item>): List<Folder> =
        items.groupBy { it.folder.ifBlank { LOOSE } }
            .map { (name, list) -> Folder(name, list.sortedWith(compareBy({ it.order }, { -it.createdAt }))) }
            .sortedByDescending { f -> f.items.maxOf { maxOf(it.finishedAt, it.createdAt) } }

    /** Whole days left before auto-delete, or null when it never happens (pinned / timer off / not finished). */
    fun daysLeft(item: OnlineDownloads.Item, days: Int, now: Long): Int? {
        if (item.keep || days <= 0 || item.finishedAt <= 0) return null
        val left = (item.finishedAt + days * 86_400_000L - now) / 86_400_000.0
        return kotlin.math.ceil(left).toInt().coerceAtLeast(0)
    }

    /** Folder name for downloads saved without a series folder. */
    const val LOOSE = "Other downloads"
}
