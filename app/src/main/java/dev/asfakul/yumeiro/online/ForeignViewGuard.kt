/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * A11Y-EXT-1 (owner list 22 probe 45475409248): some extensions (AnimePahe from Phisher's repo) attach their own
 * WebView to the activity's content view, 1 × 1 px, to pass a site's check. Nobody can see it, but it took the
 * focus and screen readers read its page ("Attention Required! | Cloudflare … you have been blocked") on Yumix.
 * On a TV the remote could get stuck in it.
 *
 * Yumeiro's own UI is the one Compose root; any other view added to the content that is (or stays) at most
 * [TINY_PX] in both directions is kept out of screen readers and focus. The view keeps running: the extension's
 * page and its scripts work as before. Views of a normal size are left alone (an extension may show real UI).
 */
object ForeignViewGuard {
    private const val TAG = "YuForeignView"
    const val TINY_PX = 4

    /** Pure: whether a view that isn't Yumeiro's Compose root should be quieted. */
    fun shouldQuiet(isOwnRoot: Boolean, widthPx: Int, heightPx: Int): Boolean =
        !isOwnRoot && widthPx in 0..TINY_PX && heightPx in 0..TINY_PX

    fun attach(activity: Activity) {
        // A11Y-EXT-1b (probe 45530059278): AnimePahe's 1 × 1 FrameLayout + WebView goes straight into the window's
        // root (the decor view, beside the content), not into android.R.id.content. Watch both.
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val groups = listOfNotNull(decor, content).distinct()
        groups.forEach { watch(it) }
        // A11Y-EXT-1c (probe 45533712008: still focused after 1b): the add can happen in ways the hierarchy listener
        // doesn't see, so also look at the window's top-level views after every layout (a handful of views: cheap),
        // and give back any focus that lands inside a quieted view.
        val tree = decor.viewTreeObserver
        tree.addOnGlobalLayoutListener { groups.forEach { g -> for (i in 0 until g.childCount) g.getChildAt(i)?.let { c -> if (!isOwn(c)) check(c, c.width, c.height) } } }
        tree.addOnGlobalFocusChangeListener { _, now ->
            if (now != null && insideQuieted(now)) {
                now.clearFocus()
                content?.let { c -> (0 until c.childCount).map { c.getChildAt(it) }.firstOrNull { isOwn(it) }?.requestFocus() }
                Log.w(TAG, "focus moved out of a hidden extension view")
            }
        }
    }

    private fun insideQuieted(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p.getTag(TAG_KEY) == true) return true
            p = p.parent as? View
        }
        return false
    }

    private fun watch(group: ViewGroup) {
        group.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                child ?: return
                if (isOwn(child)) return
                // Check the size it was given and again after each layout (it may be sized later).
                check(child, child.layoutParams?.width ?: -1, child.layoutParams?.height ?: -1)
                child.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> check(v, r - l, b - t) }
            }
            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
    }

    /**
     * Pure: a class name that belongs to Yumeiro's own UI stack. A11Y-EXT-1e (probe 45537866533): enableEdgeToEdge()
     * adds a 0 × 0 androidx.activity view to the decor; the guard quieted it and logged it as "added by an
     * extension", which was wrong. Quieting it did no harm, but the log must only name extension views.
     */
    fun isOwnClass(name: String): Boolean =
        name.startsWith("androidx.compose.ui.") || name.startsWith("androidx.activity.") || name.startsWith("com.android.internal.")

    /** Yumeiro's Compose root, and the window's own parts (the decor's content column and bar backgrounds). */
    private fun isOwn(v: View) = isOwnClass(v.javaClass.name) ||
        v.id == android.R.id.content || v.id == android.R.id.statusBarBackground || v.id == android.R.id.navigationBarBackground ||
        (v is ViewGroup && v.findViewById<View>(android.R.id.content) != null)

    private fun check(v: View, w: Int, h: Int) {
        if (!shouldQuiet(false, w, h) || v.getTag(TAG_KEY) == true) return
        v.setTag(TAG_KEY, true)
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        v.isFocusable = false
        if (v is ViewGroup) v.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        v.findFocus()?.clearFocus()
        // Warning level: release builds strip Log.i (proguard-rules.pro), which hid this line in probe 45530059278.
        Log.w(TAG, "quieted a ${w}x$h ${v.javaClass.name} added by an extension (no screen reader, no focus)")
    }

    /** A framework-reserved-free tag key (an app resource id). */
    private val TAG_KEY = dev.asfakul.yumeiro.R.id.yu_foreign_quiet
}
