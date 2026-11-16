/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package org.yumeirotest.fakeext

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView

/**
 * ADR-014 test extension. It lives outside the app's packages, so Yumeiro treats its code as
 * extension code, exactly like a class from a .cs3 dex. It opens the windows real extensions open:
 * a donation prompt (wording as in phisher98's DonationManager), a login form, a window added from
 * a worker thread and a window with a dead token.
 */
object FakeExtension {
    fun donation(activity: Activity): AlertDialog = AlertDialog.Builder(activity)
        .setTitle("Support the developer")
        .setMessage("If you enjoy these extensions, consider donating on Ko-fi.")
        .setPositiveButton("Donate", null).setNegativeButton("Later", null)
        .show()

    fun login(activity: Activity): AlertDialog = AlertDialog.Builder(activity)
        .setTitle("Log in to FakeExt")
        .setView(EditText(activity).apply { hint = "Username" })
        .setPositiveButton("Log in", null).setNegativeButton("Cancel", null)
        .show()

    /** Shows a dialog-type window with a token that belongs to no screen, from a main-thread post (as after the activity went away). */
    fun postDeadTokenWindow(activity: Activity) {
        val view = TextView(activity).apply { text = "Choose a server" }
        Handler(Looper.getMainLooper()).post {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(view, WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.TRANSLUCENT,
            ).apply { token = Binder() })
        }
    }

    /** An extension callback that throws on the main thread. */
    fun postThrowingCallback() {
        Handler(Looper.getMainLooper()).post { throw IllegalStateException("FakeExtension callback failed") }
    }
}
