package com.lagradost.cloudstream3

import android.app.Activity
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UiText
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * CloudStream-compatible host object that extensions use to reach the current screen
 * (`CommonActivity.activity`) and to show short messages. Binary compatible with CloudStream.
 */
object CommonActivity {
    private var _activity: WeakReference<Activity>? = null

    /** The visible activity (an AppCompatActivity), or null when the app is in the background. */
    var activity: Activity?
        get() = _activity?.get()
        private set(value) { _activity = WeakReference(value) }

    fun setActivityInstance(newActivity: Activity?) { activity = newActivity }

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics
    val screenWidth: Int get() = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
    val screenHeight: Int get() = min(displayMetrics.widthPixels, displayMetrics.heightPixels)
    val screenWidthWithOrientation: Int get() = displayMetrics.widthPixels
    val screenHeightWithOrientation: Int get() = displayMetrics.heightPixels

    var isPipDesired: Boolean = false
    var isInPIPMode: Boolean = false
    var canEnterPipMode: Boolean = false
    var canShowPipMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    /** Key events (TV remotes). Return true from the listener to consume the event. */
    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null

    /** Hook used by the host app to show messages in its own style; falls back to a system toast. */
    @Volatile var toastHandler: ((String, Boolean) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var lastToast: Toast? = null

    fun showToast(@StringRes message: Int, duration: Int? = null) = showToast(activity, message, duration)
    fun showToast(message: String?, duration: Int? = null) = showToast(activity, message, duration)
    fun showToast(message: UiText?, duration: Int? = null) {
        val act = activity ?: return
        showToast(act, message?.asStringNull(act), duration)
    }
    fun showToast(act: Activity?, text: UiText, duration: Int) = showToast(act, text.asStringNull(act), duration)
    fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
        val a = act ?: activity ?: return
        showToast(a, runCatching { a.getString(message) }.getOrNull(), duration)
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (message.isNullOrBlank()) return
        val long = duration == Toast.LENGTH_LONG
        main.post {
            val handler = toastHandler
            if (handler != null) { runCatching { handler(message, long) }; return@post }
            val ctx = act ?: activity ?: CloudStreamApp.context ?: return@post
            runCatching {
                lastToast?.cancel()
                lastToast = Toast.makeText(ctx, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).also { it.show() }
            }
        }
    }
}
