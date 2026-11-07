/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

/** Picture-in-picture helpers: aspect ratio, play/pause action and auto-enter on Home. */
object PipController {
    const val ACTION_PIP_CONTROL = "dev.asfakul.yumeiro.action.PIP_CONTROL"
    const val EXTRA_CONTROL = "control"
    const val CONTROL_PLAY_PAUSE = 1
    const val CONTROL_REWIND = 2
    const val CONTROL_FORWARD = 3

    /**
     * True while the activity is in system picture-in-picture. The in-app floating player reads it
     * to fill the PiP window, and app overlays (messages, popups) hide themselves meanwhile.
     */
    val inPip = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Set by the video screen; MainActivity calls it from onUserLeaveHint (Android 9–11). */
    @Volatile var onUserLeaveHint: (() -> Unit)? = null

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun params(context: Context, width: Int, height: Int, playing: Boolean, autoEnter: Boolean, sourceHint: Rect? = null): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        if (width > 0 && height > 0) {
            // Android limits PiP to ratios between 1:2.39 and 2.39:1.
            val r = (width.toFloat() / height).coerceIn(0.42f, 2.38f)
            b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
        } else b.setAspectRatio(Rational(16, 9))
        sourceHint?.let { b.setSourceRectHint(it) }
        b.setActions(actions(context, playing))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(autoEnter)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    fun enter(activity: Activity, width: Int, height: Int, playing: Boolean): Boolean = runCatching {
        activity.enterPictureInPictureMode(params(activity, width, height, playing, autoEnter = false))
    }.getOrDefault(false)

    private fun actions(context: Context, playing: Boolean): List<RemoteAction> = listOf(
        action(context, CONTROL_REWIND, android.R.drawable.ic_media_rew, "Rewind 10s"),
        if (playing) action(context, CONTROL_PLAY_PAUSE, android.R.drawable.ic_media_pause, "Pause")
        else action(context, CONTROL_PLAY_PAUSE, android.R.drawable.ic_media_play, "Play"),
        action(context, CONTROL_FORWARD, android.R.drawable.ic_media_ff, "Forward 10s"),
    )

    private fun action(context: Context, control: Int, icon: Int, title: String): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(context.packageName).putExtra(EXTRA_CONTROL, control)
        val pi = PendingIntent.getBroadcast(context, control, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(context, icon), title, title, pi)
    }
}
