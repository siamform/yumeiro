/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CancellationException

/**
 * R16 Group 2: bulk actions on installed extensions (select all / individual selection, then turn
 * on, turn off, update or remove). Items are processed one at a time, not in parallel: each update
 * downloads a file of up to 25 MB and the engine lock serialises the
 * install anyway, so parallel jobs only multiplied memory on 2 GB phones. Pure parts are unit tested.
 */
object ExtensionBulk {

    data class Outcome(val ok: Int, val skipped: Int, val failed: List<String>) {
        val total get() = ok + skipped + failed.size
    }

    /** Runs [action] for each item in order. A user "don't install" is a skip, not a failure. */
    suspend fun <T> runEach(items: List<T>, name: (T) -> String, action: suspend (T) -> Unit): Outcome {
        var ok = 0; var skipped = 0
        val failed = mutableListOf<String>()
        for (item in items) {
            try { action(item); ok++ }
            catch (c: CancellationException) { throw c }
            catch (d: ExtensionReview.Declined) { skipped++ }
            catch (t: Throwable) { failed += name(item) }
        }
        return Outcome(ok, skipped, failed)
    }

    /** One short message for the whole batch, naming what failed (at most three names). */
    fun summary(verb: String, o: Outcome): String {
        val head = if (o.ok == o.total) "$verb ${o.total} extension${if (o.total == 1) "" else "s"}" else "$verb ${o.ok} of ${o.total}"
        val skip = if (o.skipped > 0) " · ${o.skipped} skipped" else ""
        val fail = if (o.failed.isEmpty()) "" else {
            val names = o.failed.take(3).joinToString(", ") + if (o.failed.size > 3) " and ${o.failed.size - 3} more" else ""
            " · failed: $names"
        }
        return head + skip + fail
    }

    /** Select-all toggle over what is shown: selects all shown, or clears them when all are already selected. */
    fun toggleAll(selected: Set<String>, shown: List<String>): Set<String> =
        if (shown.isNotEmpty() && selected.containsAll(shown)) selected - shown.toSet() else selected + shown

    fun toggle(selected: Set<String>, key: String): Set<String> = if (key in selected) selected - key else selected + key

    /** Selection limited to keys that still exist (an extension removed elsewhere drops out). */
    fun prune(selected: Set<String>, existing: Collection<String>): Set<String> = selected.intersect(existing.toSet())
}
