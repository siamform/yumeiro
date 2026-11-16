/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * EXT-T1 diagnostics. Logs (tag `YuWindowSpy`) every window our process adds, with the stack that
 * added it, the root view class, its texts and its layout type, plus a poll of the accessibility
 * window list. A window opened by extension code shows extension frames (e.g. `com.phisher98.…`)
 * in the stack. Diagnostic only; it never changes what the app does.
 */
object WindowSpy {
    private const val TAG = "YuWindowSpy"
    data class Added(val at: Long, val root: String, val title: String, val type: Int, val texts: List<String>, val foreign: List<String>)

    val added = CopyOnWriteArrayList<Added>()
    @Volatile private var logging = false
    @Volatile private var installed = false
    private var poller: Thread? = null

    private val FRAMEWORK = listOf("android.", "java.", "javax.", "kotlin.", "kotlinx.", "androidx.", "com.android.", "dalvik.", "libcore.", "sun.", "com.google.", "dev.asfakul.yumeiro.WindowSpy")

    fun start() {
        added.clear(); logging = true
        if (!installed) installed = runCatching { swapViewList() }.onFailure { Log.w(TAG, "view list hook unavailable: $it") }.getOrDefault(false)
        // Instrumentation.getUiAutomation() is not thread-safe: called from the poller thread while
        // the test thread connects, it threw "Already connected" and killed the run (43812114134,
        // api36, e8). Take it here, on the test thread, and hand it to the poller.
        val ua = runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation }
            .onFailure { Log.w(TAG, "no UiAutomation, window list off: $it") }.getOrNull()
        poller?.interrupt()
        poller = ua?.let { Thread({ pollWindows(it) }, "window-spy").apply { isDaemon = true; start() } }
        Log.i(TAG, "start hook=$installed")
    }

    fun stop() {
        logging = false; poller?.interrupt(); poller = null
        Log.i(TAG, "SUMMARY added=${added.size} foreign=${added.count { it.foreign.isNotEmpty() }} " +
            added.joinToString(" | ") { "${it.root} type=${it.type} title=${it.title} texts=${it.texts.take(4)} foreign=${it.foreign.take(3)}" })
    }

    /** WindowManagerGlobal keeps every root view in `mViews`; a list that logs `add` sees each new window. */
    @Suppress("UNCHECKED_CAST")
    private fun swapViewList(): Boolean {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val global = cls.getMethod("getInstance").invoke(null)
        val viewsField = cls.getDeclaredField("mViews").apply { isAccessible = true }
        val paramsField = cls.getDeclaredField("mParams").apply { isAccessible = true }
        val lock = cls.getDeclaredField("mLock").apply { isAccessible = true }.get(global)!!
        synchronized(lock) {
            val old = viewsField.get(global) as ArrayList<View>
            val spy = object : ArrayList<View>(old) {
                override fun add(element: View): Boolean {
                    val r = super.add(element)
                    if (logging) runCatching { onAdded(element, Throwable("window added"), paramsField, global) }
                    return r
                }
            }
            viewsField.set(global, spy)
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun onAdded(view: View, where: Throwable, paramsField: java.lang.reflect.Field, global: Any) {
        val frames = where.stackTrace.map { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val foreign = where.stackTrace.map { it.className }.filter { c -> FRAMEWORK.none { c.startsWith(it) } && !c.startsWith("dev.asfakul.yumeiro.") }.distinct()
        val ours = where.stackTrace.map { it.className }.filter { it.startsWith("dev.asfakul.yumeiro.") && !it.startsWith("dev.asfakul.yumeiro.WindowSpy") }.distinct()
        val t = SystemClock.uptimeMillis()
        Log.i(TAG, "ADD root=${view.javaClass.name} loader=${view.javaClass.classLoader?.javaClass?.simpleName} foreign=$foreign ours=${ours.take(5)}")
        Log.i(TAG, "ADD stack: " + frames.filter { f -> FRAMEWORK.none { f.startsWith(it) } || f.startsWith("android.app.Dialog") }.take(25).joinToString(" < "))
        // Params and texts are filled in after addView returns; read them a moment later on main.
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                val views = (global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>)
                val params = paramsField.get(global) as List<WindowManager.LayoutParams>
                val p = params.getOrNull(views.indexOf(view))
                val texts = texts(view)
                added += Added(t, view.javaClass.name, p?.title?.toString().orEmpty(), p?.type ?: -1, texts, foreign)
                Log.i(TAG, "ADDED title=${p?.title} type=${p?.type} flags=0x${Integer.toHexString(p?.flags ?: 0)} texts=${texts.take(10)} attached=${view.isAttachedToWindow}")
            }
        }, 600)
    }

    private fun texts(v: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (out.size >= 20) return out
        if (v is TextView && !v.text.isNullOrBlank()) out += v.text.toString().take(60)
        v.contentDescription?.takeIf { it.isNotBlank() }?.let { out += "cd:" + it.toString().take(60) }
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    /** Second source: the accessibility window list (titles, types, layers, focus) every 500 ms. */
    private fun pollWindows(ua: android.app.UiAutomation) {
        runCatching { ua.serviceInfo = ua.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS } }
        var last = ""
        while (logging && !Thread.currentThread().isInterrupted) {
            val now = runCatching {
                ua.windows.joinToString("; ") { w ->
                    val root: AccessibilityNodeInfo? = runCatching { w.root }.getOrNull()
                    "id=${w.id} type=${w.type} layer=${w.layer} focused=${w.isFocused} active=${w.isActive} title=${w.title} root=${root?.className} pkg=${root?.packageName}"
                }
            }.getOrElse { "error $it" }
            if (now != last) { Log.i(TAG, "WINDOWS $now"); last = now }
            try { Thread.sleep(500) } catch (_: InterruptedException) { break }
        }
    }
}
