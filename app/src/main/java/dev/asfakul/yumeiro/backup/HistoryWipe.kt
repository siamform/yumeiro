/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import dev.asfakul.yumeiro.data.SafeJsonFile
import java.io.File

/**
 * B9 (beta chapter): "Clear watch and search history" left history behind. The reset deleted
 * `online_history.json` but not the `.tmp` copy [SafeJsonFile] reads back when the main file is missing
 * (so the next start could bring the whole history back) nor the `.bad` copy of an unreadable file; and
 * Play Link and Live TV recents, visited websites, the Live TV country picks, the "seen" episode marks and
 * the searches kept for a quick reopen (memory and the phone's cache folder) were never touched.
 *
 * One list, used by the launch-time reset ([ResetCenter.applyNow]) and by the Privacy buttons.
 */
object HistoryWipe {
    /** History files, in `filesDir` (main profile) and in every `filesDir/profiles/<id>/`. */
    val FILES = listOf("online_history.json", dev.asfakul.yumeiro.player.LastPlayed.QUEUE_FILE)

    /** Every folder a profile keeps its own files in. */
    private fun profileDirs(context: Context): List<File> {
        val files = context.applicationContext.filesDir
        return listOf(files) + (File(files, "profiles").listFiles()?.filter { it.isDirectory }.orEmpty())
    }

    /** A file and the copies [SafeJsonFile] keeps next to it. */
    internal fun withCopies(f: File): List<File> = listOf(f, SafeJsonFile.tmpOf(f), SafeJsonFile.badOf(f))

    /** Deletes the history files of every profile with their `.tmp` / `.bad` copies. Returns how many were removed. */
    fun deleteFiles(context: Context): Int =
        profileDirs(context).sumOf { dir -> FILES.sumOf { name -> withCopies(File(dir, name)).count { it.exists() && it.delete() } } }

    /** Leftover copies only (the live clear writes an empty main file itself). */
    fun deleteLeftovers(context: Context, name: String) {
        profileDirs(context).forEach { dir -> val f = File(dir, name); listOf(SafeJsonFile.badOf(f)).forEach { if (it.exists()) it.delete() } }
    }

    /**
     * The searches kept for a quick reopen: in memory and in the phone's cache folder. The folder is deleted here
     * too (not only through DataCache), because the launch-time reset runs before DataCache knows its folder.
     */
    fun dropSearchCaches(context: Context, live: Boolean = true) {
        // At launch ([live] false) nothing is in memory yet, and the repository is not started for it.
        if (live) runCatching { dev.asfakul.yumeiro.online.OnlineRepository.clearSearchCache() }
        else runCatching { dev.asfakul.yumeiro.cache.DataCache.dropNamespace("search") }
        runCatching { File(context.applicationContext.cacheDir, dev.asfakul.yumeiro.cache.DataCache.DIR + "/search").deleteRecursively() }
    }

    /** Watch history extras the Privacy "Clear watch history" button removes (call off the main thread). */
    fun watchExtras(context: Context) {
        val c = context.applicationContext
        removeKeys(c, dev.asfakul.yumeiro.profiles.Profiles.prefsName(c, "episode_seen")) { true }
        removeKeys(c, dev.asfakul.yumeiro.profiles.Profiles.prefsName(c, "live_tv")) { it == "country_recent" }
        deleteLeftovers(c, "online_history.json")
    }

    /** Listening extras: "Jump back in" and the saved play queue. */
    fun listeningExtras(context: Context) {
        runCatching { dev.asfakul.yumeiro.player.LastPlayed.clear(context.applicationContext) }
    }

    internal fun removeKeys(context: Context, name: String, test: (String) -> Boolean) {
        val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val gone = p.all.keys.filter(test)
        if (gone.isEmpty()) return
        val ed = p.edit()
        gone.forEach { ed.remove(it) }
        ed.commit()
    }
}
