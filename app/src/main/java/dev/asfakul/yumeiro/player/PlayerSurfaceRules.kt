/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.app.Activity
import android.content.Intent
import android.os.Build

/**
 * R15 phase 4: where the video goes when something else happens (docs/SYSTEM-DESIGN.md §7).
 *
 * - "View downloads" while a video plays → the video moves to the floating player and Downloads opens.
 * - A *new* video from any source while the floating player or system picture-in-picture is showing
 *   → full screen at once. A source failover or quality switch of the same title is not a new
 *   video and leaves the window where it is.
 */
object PlayerSurfaceRules {
    /**
     * The title a player id belongs to. Online ids are `online:<title>:<link>`; the link part
     * changes on every source switch / failover, the title part doesn't.
     */
    fun contentKey(videoId: String): String =
        if (videoId.startsWith("online:") && videoId.count { it == ':' } >= 2) videoId.substringBeforeLast(':') else videoId

    /** True when [next] is another video than [previous] (null = nothing was playing). */
    fun isNewVideo(previous: String?, next: String): Boolean = previous == null || contentKey(previous) != contentKey(next)

    fun inSystemPip(activity: Activity?): Boolean =
        activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode

    /**
     * Brings the activity out of system picture-in-picture to full screen. Re-launching the
     * (singleTop) activity is the supported way to expand PiP from code.
     */
    fun expandFromPip(activity: Activity) {
        if (!inSystemPip(activity)) return
        runCatching {
            activity.startActivity(
                Intent(activity, activity.javaClass)
                    .setAction(ACTION_EXPAND_PLAYER)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    const val ACTION_EXPAND_PLAYER = "dev.asfakul.yumeiro.action.EXPAND_PLAYER"
}
