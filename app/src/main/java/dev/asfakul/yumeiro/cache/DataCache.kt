/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cache

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Round 12: what was loaded once is kept on the phone for a while, so Yumix, details pages and
 * searches open instantly instead of asking every extension again on each visit. Entries expire
 * on their own ([Settings.freshMs]); anything older than [Settings.keepDays] is cleared
 * automatically, and the folder never grows past [Settings.maxBytes]. Pull to refresh always
 * loads fresh.
 *
 * Stored under the app's cache folder (Android may also clear it when storage runs low). Plain
 * JSON text the app wrote itself; never code, never sign-in data.
 */
object DataCache {
    private const val TAG = "YuCache"
    private const val DIR = "data_cache"
    @Volatile private var root: File? = null
    /**
     * In-memory copies, least recently used first. G12 X12-1: bounded by total size as well as by
     * count. Before, 48 entries of up to 4 MB text each had no total limit (UTF-16: up to ~380 MB),
     * on phones with a 192 MB heap. Pages bigger than a quarter of the budget are read from disk.
     */
    private val memory = LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true)
    private var memoryChars = 0L
    private const val MAX_MEMORY_ENTRIES = 48
    /** Test hook; otherwise read each time, because DataCache.init runs before DeviceClass.init. */
    internal var memoryBudgetOverride: Long? = null
    private val memoryBudgetChars: Long get() = memoryBudgetOverride ?: if (dev.asfakul.yumeiro.DeviceClass.lowEnd) 2_000_000L else 6_000_000L

    /** Call with the [memory] lock held. */
    private fun remember(mk: String, at: Long, v: String) {
        memory.remove(mk)?.let { memoryChars -= it.second.length }
        if (v.length > memoryBudgetChars / 4) return
        memory[mk] = at to v
        memoryChars += v.length
        val it = memory.entries.iterator()
        while ((memoryChars > memoryBudgetChars || memory.size > MAX_MEMORY_ENTRIES) && it.hasNext()) {
            val e = it.next(); it.remove(); memoryChars -= e.value.second.length
        }
    }

    internal fun memoryCharsForTests(): Long = synchronized(memory) { memoryChars }
    private val locks = ConcurrentHashMap<String, Any>()

    /** User settings (in Settings → Backup, storage & reset). */
    object Settings {
        private const val PREFS = "data_cache"
        @Volatile private var p: android.content.SharedPreferences? = null
        fun init(c: Context) { if (p == null) p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
        val enabled: Boolean get() = p?.getBoolean("enabled", true) ?: true
        /** How long a saved page counts as fresh (default 6 hours). */
        val freshHours: Int get() = p?.getInt("fresh_hours", 6) ?: 6
        val freshMs: Long get() = freshHours * 3_600_000L
        /** Saved pages older than this are deleted (default 7 days). */
        val keepDays: Int get() = p?.getInt("keep_days", 7) ?: 7
        /** Website cookies are cleared this often (0 = never; default 30 days). */
        val cookieDays: Int get() = p?.getInt("cookie_days", 30) ?: 30
        val maxBytes: Long get() = (p?.getInt("max_mb", 128) ?: 128) * 1024L * 1024
        fun set(enabled: Boolean? = null, freshHours: Int? = null, keepDays: Int? = null, cookieDays: Int? = null, maxMb: Int? = null) {
            val e = p?.edit() ?: return
            enabled?.let { e.putBoolean("enabled", it) }
            freshHours?.let { e.putInt("fresh_hours", it.coerceIn(1, 24 * 7)) }
            keepDays?.let { e.putInt("keep_days", it.coerceIn(1, 60)) }
            cookieDays?.let { e.putInt("cookie_days", it.coerceIn(0, 365)) }
            maxMb?.let { e.putInt("max_mb", it.coerceIn(16, 2048)) }
            e.apply()
        }
        internal var lastJanitor: Long
            get() = p?.getLong("last_janitor", 0) ?: 0
            set(v) { p?.edit()?.putLong("last_janitor", v)?.apply() }
        /** G2 E12 / ST-22: pages saved at or before this moment count as gone ([invalidateAll]). */
        internal var clearedAt: Long
            get() = p?.getLong("cleared_at", 0) ?: 0
            set(v) { p?.edit()?.putLong("cleared_at", v)?.apply() }
        internal var lastCookieClear: Long
            get() = p?.getLong("last_cookie_clear", 0) ?: 0
            set(v) { p?.edit()?.putLong("last_cookie_clear", v)?.apply() }
    }

    /** In-memory copy of [Settings.clearedAt] (read on every lookup). */
    @Volatile private var clearedAt = 0L
    private val clearer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "yu-cache-clear").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    fun init(context: Context) {
        Settings.init(context)
        if (clearedAt == 0L) clearedAt = Settings.clearedAt
        if (root == null) root = File(context.applicationContext.cacheDir, DIR).apply { mkdirs() }
    }

    internal fun fileFor(ns: String, key: String): File? {
        val r = root ?: return null
        val h = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(r, ns.filter { it.isLetterOrDigit() || it == '_' }), "$h.json")
    }

    /** The saved text for [key] when it's younger than [maxAgeMs], else null. */
    fun read(ns: String, key: String, maxAgeMs: Long = Settings.freshMs): String? {
        if (!Settings.enabled) return null
        val now = System.currentTimeMillis()
        val mk = "$ns|$key"
        synchronized(memory) { memory[mk] }?.let { (at, v) -> if (at > clearedAt && now - at in 0..maxAgeMs) return v }
        val f = fileFor(ns, key) ?: return null
        return runCatching {
            if (!f.isFile) return null
            val at = f.lastModified()
            if (at <= clearedAt || now - at !in 0..maxAgeMs) return null
            val text = f.readText()
            // The key is stored on the first line so a hash collision can never return another page.
            val nl = text.indexOf('\n')
            if (nl < 0 || text.substring(0, nl) != key.take(512)) return null
            val v = text.substring(nl + 1)
            synchronized(memory) { remember(mk, at, v) }
            v
        }.getOrNull()
    }

    /** Age of the saved entry in ms, or null when there is none. */
    fun ageOf(ns: String, key: String): Long? = fileFor(ns, key)?.takeIf { it.isFile && it.lastModified() > clearedAt }?.let { System.currentTimeMillis() - it.lastModified() }

    fun write(ns: String, key: String, value: String) {
        if (!Settings.enabled || value.length > 4 * 1024 * 1024) return
        val f = fileFor(ns, key) ?: return
        synchronized(memory) { remember("$ns|$key", System.currentTimeMillis(), value) }
        val lock = locks.getOrPut(f.path) { Any() }
        runCatching {
            synchronized(lock) {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(key.take(512) + "\n" + value)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }
        }.onFailure { Log.w(TAG, "write $ns", it) }
    }

    fun remove(ns: String, key: String) {
        synchronized(memory) { memory.remove("$ns|$key")?.let { memoryChars -= it.second.length } }
        fileFor(ns, key)?.delete()
    }

    fun clearMemory() = synchronized(memory) { memory.clear(); memoryChars = 0 }

    /** Deletes everything now, on the calling thread. For background callers and tests; the UI uses [invalidateAll]. */
    fun clearAll() { clearMemory(); root?.deleteRecursively(); root?.mkdirs() }

    /**
     * G2 E12 / ST-22: drops every saved page at once without touching the disk on the calling
     * thread. UI actions (adult content switch, extension changes, sign-in, restore) called
     * [clearAll] on the main thread, which deleted up to [Settings.maxBytes] of files there.
     * Now: a stamp makes every page saved until this moment a miss right away (memory and disk,
     * kept across restarts), and the files are deleted on a background thread. Pages saved after
     * the stamp are kept.
     */
    fun invalidateAll() {
        val stamp = System.currentTimeMillis().coerceAtLeast(clearedAt + 1)
        clearedAt = stamp
        Settings.clearedAt = stamp // apply(): saved off the calling thread
        clearMemory()
        clearer.execute {
            runCatching {
                root?.walkBottomUp()?.forEach { f -> if (f.isFile && f.lastModified() <= stamp) f.delete() }
            }.onFailure { Log.w(TAG, "clear", it) }
        }
    }

    /** Test hook: waits until background deletes queued so far are done. */
    internal fun awaitClearForTests() { clearer.submit {}.get(30, java.util.concurrent.TimeUnit.SECONDS) }

    fun bytesUsed(): Long = root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0

    /**
     * Deletes entries older than [keepMs], then the oldest ones until the folder fits [maxBytes].
     * Cheap; runs at most every few hours. Returns the bytes freed.
     */
    fun purge(keepMs: Long = Settings.keepDays * 86_400_000L, maxBytes: Long = Settings.maxBytes, now: Long = System.currentTimeMillis()): Long {
        val r = root ?: return 0
        val files = r.walkTopDown().filter { it.isFile }.toMutableList()
        var freed = 0L
        // G12 X12-2: a fresh .tmp is a write in progress (deleting it lost that page); only stale ones go.
        val gone = clearedAt
        files.removeAll { f -> (now - f.lastModified() > keepMs || f.lastModified() <= gone || (f.name.endsWith(".tmp") && now - f.lastModified() > 60_000L)).also { old -> if (old) { freed += f.length(); f.delete() } } }
        var total = files.sumOf { it.length() }
        if (total > maxBytes) {
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes) break
                total -= f.length(); freed += f.length(); f.delete()
            }
        }
        if (freed > 0) clearMemory()
        return freed
    }

    /** Runs the time limits: old pages and (when due) website cookies. Called at app start. */
    fun runJanitor(context: Context, now: Long = System.currentTimeMillis()) {
        init(context)
        if (now - Settings.lastJanitor > 3 * 3_600_000L) {
            runCatching { purge(now = now) }
            Settings.lastJanitor = now
        }
        val days = Settings.cookieDays
        if (days > 0) {
            val last = Settings.lastCookieClear
            if (last == 0L) Settings.lastCookieClear = now
            else if (now - last > days * 86_400_000L) {
                runCatching { dev.asfakul.yumeiro.backup.ResetCenter.clearCookies() }
                Settings.lastCookieClear = now
            }
        }
    }
}
