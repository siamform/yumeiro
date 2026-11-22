/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * R15: memory and thread breadcrumbs.
 *
 * Android's out-of-memory kills (low-memory killer) leave no stack trace, so a crash report used
 * to say "Android kept no trace for this one". This keeps a small ring of samples (Java heap,
 * native heap, threads grouped by name, system free memory, trim events, screen breadcrumbs) and
 * mirrors it to `files/crash/breadcrumbs.log` so the report written on the next start can show
 * what the app looked like just before it died.
 *
 * Cost: one sample every [PERIOD_MS] on a low-priority background thread; nothing on the main thread.
 */
object MemoryWatch {
    private const val TAG = "YuMemory"
    private const val PERIOD_MS = 30_000L
    private const val KEEP = 160
    private const val FILE = "breadcrumbs.log"
    private const val PREVIOUS = "breadcrumbs-previous.log"

    private val ring = ArrayDeque<String>(KEEP)
    @Volatile private var file: File? = null
    @Volatile private var handler: Handler? = null
    @Volatile private var app: Context? = null
    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun install(context: Context) {
        if (handler != null) return
        val c = context.applicationContext
        app = c
        val dir = File(c.filesDir, "crash").apply { mkdirs() }
        val f = File(dir, FILE)
        // The previous run's trail is what explains a kill reported on this start.
        runCatching { if (f.exists()) f.copyTo(File(dir, PREVIOUS), overwrite = true) }
        runCatching { f.delete() }
        file = f
        val t = HandlerThread("yu-memwatch", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        val h = Handler(t.looper)
        handler = h
        h.post(object : Runnable {
            override fun run() {
                runCatching { sample("tick") }
                h.postDelayed(this, PERIOD_MS)
            }
        })
        if (dev.asfakul.yumeiro.BuildConfig.DEBUG) MainThreadWatchdog.start()
    }

    /** A short, non-personal note (screen opened, player started). Safe from any thread. */
    fun breadcrumb(note: String) = add("crumb " + note.take(120))

    /** Called from Application.onTrimMemory / onLowMemory. */
    fun onTrim(context: Context, level: Int) {
        val name = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "running_moderate"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "running_low"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "running_critical"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "ui_hidden"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "background"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "moderate"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "complete"
            else -> "level$level"
        }
        // Our own caches. Coil trims its memory cache itself on the same callback.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            dev.asfakul.yumeiro.text.RegexCache.clear()
        }
        // FINAL-50: Android 17 sent the same TRIM_MEMORY_BACKGROUND about 15 times within one second
        // to a cached process (owner crash report 2026-11-20 14:08). Each sample walks /proc/self/task
        // and allocates, so the trail filled with copies and the heap rose with every one while the
        // app was trying to look small. One sample per level per [TRIM_REPEAT_MS]; repeats are counted.
        val repeats = trimGate.admit(name, SystemClock.elapsedRealtime()) ?: return
        val label = if (repeats > 0) "trim:$name (+$repeats repeats)" else "trim:$name"
        handler?.post { runCatching { sample(label) } } ?: add(label)
    }

    private const val TRIM_REPEAT_MS = 5_000L
    internal val trimGate = TrimGate(TRIM_REPEAT_MS)

    /**
     * Pure. Lets the first trim callback of a level through, then drops the same level for
     * [windowMs]. [admit] returns null for a dropped repeat, else how many repeats it dropped since
     * the last admitted one of that level.
     */
    internal class TrimGate(private val windowMs: Long) {
        private val lastAt = HashMap<String, Long>()
        private val dropped = HashMap<String, Int>()

        @Synchronized
        fun admit(level: String, nowMs: Long): Int? {
            val last = lastAt[level]
            if (last != null && nowMs - last < windowMs) {
                dropped[level] = (dropped[level] ?: 0) + 1
                return null
            }
            lastAt[level] = nowMs
            return dropped.remove(level) ?: 0
        }
    }

    /** One line with the numbers that matter for an out-of-memory kill. Background thread only. */
    internal fun sample(why: String) {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / MB
        val max = rt.maxMemory() / MB
        val native = Debug.getNativeHeapAllocatedSize() / MB
        val sys = app?.let { c ->
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            "sysFree=${mi.availMem / MB}M low=${mi.lowMemory}"
        }.orEmpty()
        val threads = threadGroups()
        val draws = DrawCounter.drain().let { if (it.isEmpty()) "" else " $it" } // FREEZE-4
        add("$why heap=${used}/${max}M native=${native}M $sys threads=$threads$draws")
        // A Java heap that is nearly full is the moment to let go of decoded images.
        if (max > 0 && used * 100 / max >= 85) {
            add("pressure: java heap ${used * 100 / max}% — clearing image memory cache")
            runCatching { app?.let { coil.Coil.imageLoader(it).memoryCache?.clear() } }
        }
    }

    /** "143 (OkHttp 12, DefaultDispatcher 64, ExoPlayer 6, …)" from /proc (no thread dumps). */
    internal fun threadGroups(): String {
        val names = runCatching {
            File("/proc/self/task").listFiles()?.mapNotNull { t -> runCatching { File(t, "comm").readText().trim() }.getOrNull() }
        }.getOrNull() ?: return Thread.activeCount().toString()
        val groups = names.groupingBy { groupOf(it) }.eachCount().entries.sortedByDescending { it.value }.take(6)
        return "${names.size} (" + groups.joinToString { "${it.key} ${it.value}" } + ")"
    }

    /** Pure: thread name → family ("OkHttp https://x/…" → "OkHttp", "DefaultDispatch" → "Dispatcher"). */
    internal fun groupOf(name: String): String = when {
        name.startsWith("OkHttp") -> "OkHttp"
        name.startsWith("DefaultDispatch") || name.startsWith("DefaultDispatcher") -> "Dispatcher"
        name.startsWith("Okio") -> "Okio"
        name.contains("ExoPlayer", true) || name.startsWith("Exo") -> "ExoPlayer"
        name.startsWith("coil") || name.startsWith("Coil") -> "Coil"
        name.startsWith("RenderThread") || name.startsWith("hwui") -> "Render"
        name.startsWith("Binder") || name.startsWith("binder") -> "Binder"
        name.startsWith("yu-") -> "Yumeiro"
        name.startsWith("pool-") -> "Executor"
        else -> name.takeWhile { !it.isDigit() && it != '-' && it != ':' }.ifBlank { "other" }.take(16)
    }

    /** The previous run's trail (for the report of a process Android killed). */
    fun previousTrail(context: Context): String? =
        runCatching { File(File(context.filesDir, "crash"), PREVIOUS).takeIf { it.exists() }?.readText() }.getOrNull()

    /** This run's trail (attached to a crash written by the handler). */
    fun currentTrail(): String = synchronized(ring) { ring.joinToString("\n") }

    private fun add(line: String) {
        val l = stamp.format(Date()) + " " + line
        synchronized(ring) {
            if (ring.size >= KEEP) ring.removeFirst()
            ring.addLast(l)
        }
        if (dev.asfakul.yumeiro.BuildConfig.DEBUG) Log.i(TAG, l)
        val f = file ?: return
        val write = Runnable { runCatching { f.writeText(currentTrail()) } }
        if (Looper.myLooper() == Looper.getMainLooper()) handler?.post(write) else write.run()
    }

    private const val MB = 1024L * 1024L
}

/**
 * R15, debug builds: catches main-thread stalls as they happen. Posts a tick to the main thread
 * every second; when one is late by more than [STALL_MS] it records the main thread's stack
 * (logcat tag `YuStall` + breadcrumbs). The device stress test reads these lines as evidence.
 */
object MainThreadWatchdog {
    private const val STALL_MS = 700L
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (running) {
                val posted = SystemClock.uptimeMillis()
                val ran = java.util.concurrent.atomic.AtomicLong(0)
                val lock = Object()
                main.post { ran.set(SystemClock.uptimeMillis()); synchronized(lock) { lock.notifyAll() } }
                synchronized(lock) { if (ran.get() == 0L) runCatching { lock.wait(STALL_MS) } }
                if (ran.get() == 0L) {
                    val first = Looper.getMainLooper().thread.stackTrace
                    val stack = first.take(28).joinToString("\n    at ")
                    // G12 YuStall lead (43765039266 api33-lowram): the 28 printed frames were Compose
                    // measure while `ours=` named HomeOnline:88 from deeper in the same stack. Print
                    // our frames of the whole stack and its depth so the two can be tied together.
                    val msg = "main thread stalled > ${STALL_MS}ms (depth ${first.size}, ours in full stack: ${appFrames(first, 12)})\n    at $stack"
                    Log.w("YuStall", msg)
                    MemoryWatch.breadcrumb("stall " + (Looper.getMainLooper().thread.stackTrace.firstOrNull()?.toString() ?: "?"))
                    // Wait for it to recover before measuring again. A long stall is sampled every
                    // second: the first sample alone often shows framework code (Compose slot table,
                    // binder) and not which screen or call of ours is slow.
                    var nextSample = SystemClock.uptimeMillis() + 1000
                    val kinds = ArrayList<String>().apply { add(kindOf(Looper.getMainLooper().thread.stackTrace)) }
                    var oursSeen = appFrames(Looper.getMainLooper().thread.stackTrace)
                    while (ran.get() == 0L && running) {
                        runCatching { Thread.sleep(100) }
                        if (ran.get() == 0L && SystemClock.uptimeMillis() >= nextSample) {
                            nextSample += 1000
                            val trace = Looper.getMainLooper().thread.stackTrace
                            kinds += kindOf(trace)
                            appFrames(trace).takeIf { !it.startsWith("(") }?.let { oursSeen = it }
                            Log.w("YuStall", "still stalled ${SystemClock.uptimeMillis() - posted}ms, ours: " + appFrames(trace) +
                                " | top: " + trace.take(3).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName })
                        }
                    }
                    // R16: what the stall was. "render" = the main thread waited for the render
                    // thread (GPU work: on CI that is SwiftShader, a CPU GPU); "harness" = the test
                    // framework; "app" = our own main-thread work (composition, layout, draw
                    // recording or a call of ours; `ours=` names the app frames seen).
                    val kind = kinds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "app"
                    Log.w("YuStall", "main thread recovered after ${SystemClock.uptimeMillis() - posted}ms · kind=$kind · samples=${kinds.joinToString(",")} · ours=$oursSeen")
                    stallListener?.let { l -> runCatching { l(SystemClock.uptimeMillis() - posted, kind) } }
                }
                runCatching { Thread.sleep(1000) }
            }
        }, "yu-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** GATE-1-T11: told about every stall over [STALL_MS] once it ends (length, kind); the gate walk counts them. */
    @Volatile var stallListener: ((Long, String) -> Unit)? = null

    internal fun kindOf(trace: Array<StackTraceElement>): String {
        val top = trace.take(6)
        return when {
            top.any { it.className.endsWith("HardwareRenderer") || it.className.endsWith("ThreadedRenderer") && it.methodName.startsWith("n") } -> "render"
            trace.any { it.className.startsWith("androidx.compose.ui.test.") || it.className.startsWith("androidx.test.") } -> "harness"
            // GATE-1-T11: the main thread answering an accessibility query (TalkBack, or a test reading the screen).
            trace.any { it.className == "android.view.AccessibilityInteractionController" || it.className.startsWith("android.view.AccessibilityInteractionController$") } -> "a11y"
            // ST-20: a frame from an extension's dex (third-party code posted to our main looper).
            trace.any { dev.asfakul.yumeiro.online.ExtensionWindowWatch.isExtensionClass(it.className) } -> "extension"
            else -> "app"
        }
    }

    /** The first frames that belong to the app (composables included), innermost first. */
    internal fun appFrames(trace: Array<StackTraceElement>, max: Int = 8): String =
        trace.asSequence()
            .filter { it.className.startsWith("dev.asfakul.yumeiro.") && !it.className.startsWith("dev.asfakul.yumeiro.diagnostics.") }
            // The guarded main looper (ExtensionGuard.install) sits under every main-thread frame;
            // its D8 lambda class name ends in "$$ExternalSyntheticLambdaN", so match the prefix.
            .filter { !it.className.substringBefore("$$").endsWith("ExtensionGuard") }
            .map { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber }
            .distinct().take(max).joinToString(" < ").ifEmpty { "(framework only)" }
}
