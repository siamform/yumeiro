/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.app.ActivityManager
import android.content.Context
import android.webkit.CookieManager
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reset options, from gentle to complete. Cache and cookies are cleared on the spot; resets that
 * touch data the app holds in memory (history, settings, extensions) are staged and applied at
 * the next launch by [PendingRestore], the same safe path a restore takes. Every reset offers a
 * backup first.
 */
object ResetCenter {
    enum class Scope(val id: String, val label: String, val hint: String, val needsRestart: Boolean) {
        CACHE("cache", "Clear cache", "Saved pages, posters and temporary files. Everything loads fresh once", false),
        COOKIES("cookies", "Clear cookies", "Website sign-ins and Cloudflare checks. Some sources may ask for a check again", false),
        HISTORY("history", "Clear watch and search history", "Continue watching, recent searches, play counts", true),
        SETTINGS("settings", "Reset settings", "Every switch back to default. Your library, sources and extensions stay", true),
        EXTENSIONS("extensions", "Remove all extensions and sources", "Extensions, repositories, playlists, servers, add-ons", true),
    }

    fun labelOf(id: String) = Scope.entries.firstOrNull { it.id == id }?.label?.lowercase() ?: id

    private val historyFiles = listOf("online_history.json")
    private val historyPrefs = listOf("play_counts", "search_recent", "yt_history")
    private val sourceDirs = listOf("extensions", "tachiyomi", "js", "stremio", "streams", "servers")

    /** Clears caches now; returns the bytes freed. */
    suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        var freed = 0L
        listOfNotNull(c.cacheDir, c.externalCacheDir, c.codeCacheDir.takeIf { false }).forEach { root ->
            root.listFiles()?.forEach { f -> freed += sizeOf(f); f.deleteRecursively() }
        }
        runCatching { dev.asfakul.yumeiro.cache.DataCache.clearMemory() }
        freed
    }

    fun clearCookies() {
        NetworkClient.cookies.clear()
        // WebView's cookie store wants the main thread on some Android versions.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        }
    }

    fun sizeOf(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    suspend fun cacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
        listOfNotNull(context.cacheDir, context.externalCacheDir).sumOf { sizeOf(it) }
    }

    /** Stages the data resets for the next launch. The caller makes a safety backup first, then restarts. */
    fun stage(context: Context, scopes: Set<Scope>) {
        scopes.filter { !it.needsRestart }.forEach { s -> if (s == Scope.COOKIES) clearCookies() }
        val staged = scopes.filter { it.needsRestart }.map { it.id }.toSet()
        if (staged.isNotEmpty()) PendingRestore.writePlan(context, PendingRestore.Plan(reset = staged))
    }

    /** Runs at launch, before anything loads (from [PendingRestore]). */
    internal fun applyNow(context: Context, ids: Set<String>) {
        val files = context.filesDir
        if ("history" in ids) {
            historyFiles.forEach { File(files, it).delete() }
            historyPrefs.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        }
        if ("extensions" in ids) {
            sourceDirs.forEach { d -> File(files, d).walkBottomUp().forEach { it.setWritable(true); it.delete() } }
            context.getSharedPreferences("tachiyomi_bridge", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("provider_overrides", Context.MODE_PRIVATE).edit().clear().commit()
        }
        if ("settings" in ids) {
            // Library-like and device-bound prefs survive a settings reset.
            val keep = historyPrefs + listOf("manga_reader", "reminders", "tachiyomi_bridge", "online_secrets", "mt_updater",
                "fcm_push", "push_wake", "yt_account", "site_logins", BackupMeta.PREFS, "setup_guide", "starter_extensions")
            Backup.prefsDir(context).listFiles()?.filter { it.extension == "xml" }?.map { it.nameWithoutExtension }
                ?.filter { it !in keep && Backup.prefIncluded(it) }
                ?.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }

    /** Erases everything, like a fresh install (Android closes the app). */
    fun eraseEverything(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.clearApplicationUserData() != true) {
            // Fallback (should not happen): wipe our folders and restart.
            listOf(context.filesDir, context.cacheDir, Backup.prefsDir(context)).forEach { d -> d.listFiles()?.forEach { it.deleteRecursively() } }
            Restart.now(context)
        }
    }
}
