/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import java.io.File
import java.io.FileOutputStream

/**
 * R16 (G5): reading and writing the small JSON files that hold personal data (My List, online
 * history, reading library, online downloads, inbox).
 *
 * Before, each store did `runCatching { parse(f.readText()) }.getOrNull() ?: return`: a file that
 * could not be read left the store empty and marked loaded, and the next save wrote the empty list
 * over the only copy. Two ways to get there:
 *  - the write fallback `f.delete(); tmp.renameTo(f)` killed between the two steps leaves only
 *    `<name>.tmp`, which the next start ignored and then overwrote;
 *  - MangaLibrary wrote straight over its file, so a kill mid-write left it truncated.
 *
 * [read] now tries the file, then a leftover `.tmp`, and keeps an unreadable file as `<name>.bad`
 * before the store starts over. [write] goes through a synced temp file and never deletes the old
 * copy before the new one is in place.
 */
object SafeJsonFile {
    private const val TAG = "SafeJsonFile"

    /** What [read] found; [recoveredFrom] names the file used when it wasn't the main one. */
    data class Result<T>(val value: T?, val recoveredFrom: String? = null, val keptBadCopy: Boolean = false)

    fun tmpOf(f: File) = File(f.parentFile, f.name + ".tmp")
    fun badOf(f: File) = File(f.parentFile, f.name + ".bad")

    /**
     * Parses [f] with [parse]; [parse] throws on bad content. Missing file and no leftover: value
     * null. Never throws.
     */
    fun <T : Any> read(f: File, parse: (String) -> T): Result<T> {
        var mainBroken = false
        if (f.exists()) {
            runCatching { parse(f.readText()) }
                .onSuccess { return Result(it) }
                .onFailure { e -> mainBroken = true; log("${f.name} unreadable: ${e.javaClass.simpleName}: ${e.message?.take(120)}") }
        }
        val tmp = tmpOf(f)
        val fromTmp = if (tmp.exists()) runCatching { parse(tmp.readText()) }.getOrNull() else null
        var kept = false
        if (mainBroken) kept = runCatching { f.copyTo(badOf(f), overwrite = true); true }.getOrDefault(false)
        if (fromTmp != null) {
            log("${f.name}: recovered from ${tmp.name}")
            return Result(fromTmp, recoveredFrom = tmp.name, keptBadCopy = kept)
        }
        if (mainBroken) log("${f.name}: kept as ${badOf(f).name} = $kept; starting empty")
        return Result(null, keptBadCopy = kept)
    }

    /** Writes [text] to [f] through a synced temp file. Throws on failure (callers log it). */
    fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = tmpOf(f)
        FileOutputStream(tmp).use { out -> out.write(text.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        if (!tmp.renameTo(f)) {
            // rename(2) replaces the target atomically; this branch is for odd file systems. The old
            // copy is replaced by a full copy and only then is the temp file removed.
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }

    private fun log(line: String) { runCatching { android.util.Log.w(TAG, line) } }
}
