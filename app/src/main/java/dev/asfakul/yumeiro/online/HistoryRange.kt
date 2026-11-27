/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * Group Y (B6): "Delete history" by time, like a browser: the last hour, the last 24 hours, the last 7 or 30 days,
 * or everything. A title belongs to the range when it was last watched inside it. Pure.
 */
enum class HistoryRange(val ms: Long?) {
    HOUR(3_600_000L), DAY(86_400_000L), WEEK(7 * 86_400_000L), MONTH(30 * 86_400_000L), ALL(null);

    /** The first moment inside the range (null = from the beginning). */
    fun since(now: Long): Long? = ms?.let { now - it }

    fun contains(at: Long, now: Long): Boolean = since(now)?.let { at >= it } ?: true

    companion object {
        fun count(entries: List<OnlineHistory.Entry>, range: HistoryRange, now: Long): Int = entries.count { range.contains(it.updatedAt, now) }
    }
}
