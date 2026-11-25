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
        val decor = activity.window?.decorView as? ViewGroup
        listOfNotNull(decor, content).distinct().forEach { watch(it) }
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

    /** Yumeiro's Compose root, and the window's own parts (the decor's content column and bar backgrounds). */
    private fun isOwn(v: View) = v.javaClass.name.startsWith("androidx.compose.ui.") ||
        v.id == android.R.id.content || v.id == android.R.id.statusBarBackground || v.id == android.R.id.navigationBarBackground ||
        v.javaClass.name.startsWith("com.android.internal.")

    private fun check(v: View, w: Int, h: Int) {
        if (!shouldQuiet(false, w, h) || v.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        v.isFocusable = false
        if (v is ViewGroup) v.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        v.findFocus()?.clearFocus()
        Log.i(TAG, "quieted a ${w}x$h ${v.javaClass.simpleName} added by an extension (no screen reader, no focus)")
    }
}
