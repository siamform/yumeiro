/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dev.asfakul.yumeiro.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Round 2, order 7: crash reports kept on the phone.
 *
 * Every crash (Kotlin/Java exception on any thread) is written to `files/crash/` before the app
 * closes; on the next start Android's own exit records (API 30+) add native crashes and "app not
 * responding" freezes the handler can't see. Nothing leaves the phone by itself: Settings →
 * Crash reports lists them so the user can read, copy or share one with the developer, or delete
 * them. Reports hold the app version, device model, Android version, thread and stack trace;
 * no account names, tokens, URLs with keys or file contents (see [scrub]).
 */
object CrashLog {
    private const val DIR = "crash"
    private const val KEEP = 25
    private const val PREFS = "yumeiro_crashlog"
    @Volatile private var dir: File? = null
    @Volatile private var appContext: Context? = null
    /** R15: released first when the app runs out of Java heap, so the report itself can still be written. */
    @Volatile private var reserve: ByteArray? = null

    data class Report(val file: File, val time: Long, val kind: String, val headline: String)

    /** Call first in Application.onCreate; chains to the previous handler (the system's crash dialog). */
    fun install(context: Context) {
        val d = File(context.filesDir, DIR).apply { mkdirs() }
        dir = d
        appContext = context.applicationContext
        reserve = ByteArray(512 * 1024)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            reserve = null
            val oom = generateSequence(error) { it.cause }.any { it is OutOfMemoryError }
            runCatching {
                val f = write(d, if (oom) "Out of memory" else "Crash", "Thread: ${thread.name}", error,
                    body = "\n\n--- Last memory / thread samples ---\n" + runCatching { MemoryWatch.currentTrail() }.getOrDefault(""))
                // R15 (Phase 5): the report is copied to Downloads right away; the user doesn't have to open Settings.
                appContext?.let { CrashExport.export(it, f) }
            }
            previous?.uncaughtException(thread, error)
        }
        Thread({
            runCatching { importExitReasons(context) }
            runCatching { CrashExport.exportPending(context) }
        }, "yu-crashlog").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** A problem that was caught (an extension crash the app survived), kept for the developer too. */
    fun nonFatal(where: String, error: Throwable) {
        val d = dir ?: return
        runCatching { write(d, "Handled", where, error) }
    }

    fun list(context: Context): List<Report> =
        (File(context.filesDir, DIR).listFiles { f -> f.name.endsWith(".txt") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val lines = runCatching { f.useLines { it.take(12).toList() } }.getOrDefault(emptyList())
                Report(f, f.lastModified(), lines.firstOrNull { it.startsWith("Kind: ") }?.removePrefix("Kind: ") ?: "Crash",
                    lines.firstOrNull { it.startsWith("Error: ") }?.removePrefix("Error: ") ?: f.name)
            }

    fun clear(context: Context) { File(context.filesDir, DIR).listFiles()?.forEach { it.delete() } }

    internal fun write(d: File, kind: String, where: String, error: Throwable?, body: String? = null, at: Long = System.currentTimeMillis()): File {
        d.mkdirs()
        val sw = StringWriter()
        error?.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("Yumeiro crash report\n")
            append("Kind: ").append(kind).append('\n')
            append("Error: ").append(scrub(error?.let { it.javaClass.name + (it.message?.let { m -> ": $m" } ?: "") } ?: body?.lineSequence()?.firstOrNull().orEmpty()).take(300)).append('\n')
            append("When: ").append(stamp(at)).append('\n')
            append("Where: ").append(where).append('\n')
            append("App: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append(BuildConfig.FLAVOR).append(' ').append(BuildConfig.BUILD_TYPE).append('\n')
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("ABI: ").append(Build.SUPPORTED_ABIS.joinToString()).append("\n\n")
            if (error != null) append(scrub(sw.toString()))
            if (body != null) append(scrub(body))
        }
        val f = File(d, "${kind.lowercase()}-$at.txt")
        f.writeText(text.take(256_000))
        (d.listFiles { x -> x.name.endsWith(".txt") } ?: emptyArray()).sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
        return f
    }

    /** Removes query strings (keys, tokens, signed links) and e-mail addresses. Pure → unit-tested. */
    internal fun scrub(s: String): String = s
        .replace(SCRUB_QUERY, "$1?…")
        .replace(SCRUB_EMAIL, "<email>")
        .replace(SCRUB_SECRET, "$1=…")
    private val SCRUB_QUERY = Regex("""(https?://[^\s?#"']+)\?[^\s"']*""")
    private val SCRUB_EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val SCRUB_SECRET = Regex("""(?i)(token|key|secret|password|auth)=([^\s&]+)""")

    /**
     * Pure. Which exit records are problems worth a report. A low-memory kill while the app was
     * on screen, playing or downloading (foreground service) or visible (picture-in-picture) is ours
     * to fix: "Out of memory". FINAL-50: a low-memory kill of a cached or background process is
     * Android reclaiming memory from an app nobody was using; it is not a crash and is no longer
     * written as one (1.9.0 put it in the crash list and Downloads as "Closed to free memory", and
     * the owner read it as the app crashing). It stays visible in the memory trail of the next report.
     */
    internal fun exitKind(reason: Int, importance: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "Freeze"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY ->
            if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) "Out of memory" else null
        else -> null
    }

    internal fun importanceName(i: Int): String = when {
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground (on screen)"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground service (playing / downloading)"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "background service"
        else -> "background / cached"
    }

    private fun stamp(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(t))

    /** Native crashes and freezes (ANR) from Android's records, once each. */
    private fun importExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getLong("last_exit", 0L)
        var newest = seen
        val d = dir ?: return
        val trail = MemoryWatch.previousTrail(context)
        am.getHistoricalProcessExitReasons(context.packageName, 0, 10).filter { it.timestamp > seen }.forEach { e ->
            newest = maxOf(newest, e.timestamp)
            val kind = exitKind(e.reason, e.importance) ?: return@forEach // Kotlin crashes are written by the handler; normal exits are not problems
            val trace = runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText().take(120_000) } }.getOrNull()
            // R15: how big the process was and whether the user was looking at it — a background
            // kill to free memory is normal Android behaviour, a foreground one is our bug.
            val facts = "Importance at exit: ${importanceName(e.importance)} · PSS ${e.pss / 1024} MB · RSS ${e.rss / 1024} MB · status ${e.status}"
            val body = (e.description ?: kind) + "\n" + facts + "\n\n" + (trace ?: "Android kept no trace for this one.") +
                (if (trail != null && e.reason == ApplicationExitInfo.REASON_LOW_MEMORY) "\n\n--- Last memory / thread samples before the kill ---\n$trail" else "")
            write(d, kind, "process ${e.processName} · ${e.description.orEmpty()}", null, body, e.timestamp)
        }
        if (newest > seen) prefs.edit().putLong("last_exit", newest).apply()
    }
}
