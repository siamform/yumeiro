/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * ADR-014: finds the windows extension code opens and applies [ExtensionPopups]' policy to them.
 *
 * A wrapped WindowManager cannot be used: `Window.setWindowManager` casts the manager to the
 * framework's own class, so every extension Dialog would crash while it is built. Instead the
 * activity tells us when extension code asks for `WINDOW_SERVICE` (Dialog, AlertDialog and
 * PopupWindow do that while they are built), and we look at the process's root windows right
 * after that message (before the next frame is drawn, so nothing flashes) and again a few times
 * shortly after (for a dialog that is built first and shown a moment later). A new dialog-type
 * window that is not Compose (ours) is the extension's:
 *  - functional, or mode Show: it stays, framed with a thin outline and a "From extension" title;
 *  - promotional in Quiet: it is concealed in place (window alpha 0, not focusable, not touchable,
 *    no dim, hidden from screen readers) so Back and the D-pad stay with the app, and announced by one quiet card; "Show"
 *    restores it exactly as the extension built it. Concealing (not removing) keeps the
 *    extension's Dialog consistent: its own dismiss() still works;
 *  - promotional in Block promotional: concealed, not announced.
 */
object ExtensionWindowWatch {
    private const val TAG = "ExtensionWindows"
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Root windows already looked at (so each window is judged once). */
    private val seen: MutableSet<View> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))
    private val framed: MutableSet<View> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))
    private val concealed: MutableMap<View, WindowManager.LayoutParams> = Collections.synchronizedMap(WeakHashMap())
    private val appClass = ConcurrentHashMap<String, Boolean>()
    @Volatile private var pending: Pair<String, String>? = null
    @Volatile private var pendingUntil = 0L
    private var windowManager: WindowManager? = null

    /** Delays after the request at which the root windows are checked again. */
    private val RECHECK_MS = longArrayOf(16, 100, 400, 1_500, 5_000)

    /**
     * Called by MainActivity when extension code asks for the window manager. Cheap when no
     * extension is loaded. Marks the windows that exist now as seen and schedules the checks.
     */
    fun onWindowServiceRequested(wm: WindowManager) {
        if (!ExtensionManager.anyPluginLoaded() && ExtensionPopups.testExtensionPrefix == null) return
        val ext = callerExtension() ?: return
        windowManager = wm
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { arm(ext) }; return }
        arm(ext)
    }

    private fun arm(ext: Pair<String, String>) {
        if (pending == null) roots().forEach { seen.add(it) } // baseline: these are not the extension's
        pending = ext
        pendingUntil = android.os.SystemClock.uptimeMillis() + RECHECK_MS.last() + 50
        main.postAtFrontOfQueue { check() }
        RECHECK_MS.forEach { d -> main.sendMessageDelayed(Message.obtain(main) { check() }.apply { isAsynchronous = true }, d) }
    }

    private fun check() {
        val ext = pending ?: return
        if (android.os.SystemClock.uptimeMillis() > pendingUntil) { pending = null; return }
        for (root in roots()) {
            if (!seen.add(root)) continue
            val lp = root.layoutParams as? WindowManager.LayoutParams ?: continue
            if (lp.type !in WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW..WindowManager.LayoutParams.LAST_APPLICATION_WINDOW) continue
            if (isCompose(root)) continue // the app's own dialogs
            judge(root, lp, ext)
        }
    }

    private fun judge(root: View, lp: WindowManager.LayoutParams, ext: Pair<String, String>) {
        val (key, name) = ext
        val snap = ExtensionPopups.snapshot(root)
        val kind = ExtensionPopups.classify(snap)
        val action = ExtensionPopups.decide(
            kind, ExtensionPopups.mode.value, ExtensionPopups.playerOnScreen,
            ExtensionPopups.rateOk(ExtensionPopups.lastCard(key), System.currentTimeMillis()),
            userTyping = kind == ExtensionPopups.Kind.FUNCTIONAL &&
                dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.EXT_HOLD_WHILE_TYPING) && appTyping(root),
        )
        Log.i(TAG, "window from $name: $kind -> $action texts=${snap.texts.take(3)}")
        when (action) {
            ExtensionPopups.Action.SHOW_FRAMED -> frame(root, lp, name)
            ExtensionPopups.Action.DROP -> conceal(root, lp)
            ExtensionPopups.Action.HOLD_AND_CARD, ExtensionPopups.Action.HOLD_DEFERRED -> {
                conceal(root, lp)
                ExtensionPopups.hold(key, name, snap, root, deferredCard = action == ExtensionPopups.Action.HOLD_DEFERRED,
                    functional = kind == ExtensionPopups.Kind.FUNCTIONAL)
            }
        }
    }

    /**
     * True when another root window of ours (the app's, a sheet's) has the keyboard up or an editor in focus.
     * Main thread, a few field reads per window; no disk, network or regex.
     */
    private fun appTyping(newWindow: View): Boolean = roots().any { r ->
        if (r === newWindow || !r.isAttachedToWindow) return@any false
        val imeUp = Build.VERSION.SDK_INT >= 30 && r.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        imeUp || (r.hasWindowFocus() && r.findFocus()?.onCheckIsTextEditor() == true)
    }

    private fun conceal(root: View, lp: WindowManager.LayoutParams) {
        val saved = WindowManager.LayoutParams().apply { copyFrom(lp) }
        val hidden = WindowManager.LayoutParams().apply {
            copyFrom(lp)
            flags = (flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) and
                WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            alpha = 0f
            dimAmount = 0f
        }
        val ok = runCatching { windowManager?.updateViewLayout(root, hidden) }.onFailure { Log.w(TAG, "could not conceal: $it") }.isSuccess
        if (!ok) return
        // The view itself is left alone: Dialog.isShowing() reads the decor's visibility, and the
        // extension must keep seeing its dialog as open. Screen readers skip it while concealed.
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        concealed[root] = saved
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { concealed.remove(v); ExtensionPopups.forget(v) } // the extension closed it
        })
    }

    /** Shows a concealed window as the extension built it (the user asked for it). */
    internal fun reveal(view: View, extName: String): Boolean {
        val saved = concealed.remove(view) ?: return false
        if (!view.isAttachedToWindow) return false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        val ok = runCatching { windowManager?.updateViewLayout(view, saved) }.onFailure { Log.w(TAG, "could not reveal: $it") }.isSuccess
        if (ok) frame(view, saved, extName)
        return ok
    }

    internal fun isConcealed(view: View) = view in concealed

    /** The activity is going away; its windows go with it. */
    fun clear() { concealed.clear(); pending = null; windowManager = null }

    private fun isCompose(root: View): Boolean {
        var found = false
        var seenNodes = 0
        fun walk(v: View, depth: Int) {
            if (found || seenNodes++ > 200 || depth > 12) return
            if (v.javaClass.name.startsWith("androidx.compose.ui.")) { found = true; return }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    /** Every root window of this process: public API on Android 10+, the framework list before. */
    @Suppress("UNCHECKED_CAST")
    private fun roots(): List<View> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.view.inspector.WindowInspector.getGlobalWindowViews()
        else {
            val cls = Class.forName("android.view.WindowManagerGlobal")
            val g = cls.getMethod("getInstance").invoke(null)
            val lock = cls.getDeclaredField("mLock").apply { isAccessible = true }.get(g)!!
            synchronized(lock) { ArrayList(cls.getDeclaredField("mViews").apply { isAccessible = true }.get(g) as List<View>) }
        }
    }.getOrElse { Log.w(TAG, "root windows unavailable: $it"); emptyList() }

    /** Yumeiro's frame for an extension window: a thin accent outline and an accessible title. */
    private fun frame(view: View, lp: WindowManager.LayoutParams, extName: String) {
        if (!framed.add(view)) return // a dialog shown again keeps its first frame
        if (lp.title.isNullOrBlank() || lp.title.toString().contains("Activity")) {
            lp.title = "From extension: $extName"
            runCatching { windowManager?.updateViewLayout(view, lp) }
        }
        val density = view.resources.displayMetrics.density
        val outline = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.argb(150, 136, 120, 255))
            cornerRadius = cornerOf(view) ?: (4f * density)
        }
        runCatching {
            view.overlay.add(outline)
            val pos = IntArray(2)
            val place = {
                // Outline the dialog's visible card (the content frame), not the window's transparent insets.
                val content = view.findViewById<View>(android.R.id.content)
                if (content != null && content.width > 0) {
                    content.getLocationInWindow(pos)
                    outline.setBounds(pos[0], pos[1], pos[0] + content.width, pos[1] + content.height)
                } else outline.setBounds(0, 0, view.width, view.height)
                view.invalidate()
            }
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
            if (view.width > 0) place()
        }
    }

    /** Corner radius of the dialog's own card (its window background), so the outline follows it. */
    private fun cornerOf(view: View): Float? {
        var d = view.background
        var depth = 0
        while (d is android.graphics.drawable.DrawableWrapper && depth++ < 4) d = d.drawable // InsetDrawable too
        return (d as? GradientDrawable)?.let { g -> if (Build.VERSION.SDK_INT >= 24) g.cornerRadius.takeIf { it > 0f } else null }
    }

    /** (key, name) of the extension whose code is on the current stack; null when none is. */
    fun callerExtension(stack: Array<StackTraceElement> = Thread.currentThread().stackTrace): Pair<String, String>? {
        for (f in stack) {
            val c = f.className
            if (!isExtensionClass(c)) continue
            return ExtensionManager.extensionForClass(c) ?: ("unknown" to "An extension")
        }
        return null
    }

    /** True when [className] is extension code: outside the app and platform, or a test fake. */
    fun isExtensionClass(className: String): Boolean {
        ExtensionPopups.testExtensionPrefix?.let { if (className.startsWith(it)) return true }
        if (className.startsWith("com.lagradost.")) {
            // Many extensions use the CloudStream package name; the bundled library is in our APK.
            return appClass.getOrPut(className) {
                runCatching { Class.forName(className, false, ExtensionWindowWatch::class.java.classLoader) }.isSuccess
            }.not()
        }
        return ExtensionGuard.frameIsExtension(className)
    }
}
