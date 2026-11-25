/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * ROT-1 (owner list 29): turns the video picture in 90° clockwise steps without touching the source,
 * the device orientation or the subtitles.
 *
 * - TextureView renderer (phones by default): the surface view itself is rotated and scaled so the
 *   turned picture still fits (or, in Zoom mode, still fills) the player box. No pipeline change, so
 *   playback never stops.
 * - SurfaceView renderer (TV by default): SurfaceView ignores view rotation, so the turn is a Media3
 *   GPU effect ([gpuDegrees], read by applyEngineLevel) and the player re-fits the rotated frame.
 */
object VideoRotation {
    /** Clockwise degrees the SurfaceView path asks the GPU for (0 = no rotation effect). */
    @Volatile var gpuDegrees: Int = 0

    fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360

    fun next(degrees: Int): Int = normalize(normalize(degrees) + 90)

    fun quarterTurn(degrees: Int): Boolean = normalize(degrees) % 180 != 0

    fun label(degrees: Int): String = "${normalize(degrees)}°"

    /**
     * Scale for a [width]×[height] view turned by [degrees] inside a [boxW]×[boxH] box. [cover] = the
     * picture fills the box (Zoom mode); otherwise it fits whole. 0° and 180° never change the size.
     */
    fun fitScale(degrees: Int, width: Float, height: Float, boxW: Float, boxH: Float, cover: Boolean): Float {
        if (!quarterTurn(degrees) || width <= 0f || height <= 0f || boxW <= 0f || boxH <= 0f) return 1f
        val sx = boxW / height
        val sy = boxH / width
        return if (cover) max(sx, sy) else min(sx, sy)
    }

    /** Current transform of one player's surface; re-applied on every layout of the surface or its box. */
    class SurfaceTransform {
        var degrees: Int = 0
        var zoom: VideoZoom.State = VideoZoom.State()
        var cover: Boolean = false

        fun apply(surface: View, box: View) {
            if (surface is android.view.SurfaceView) { applyLaidOut(surface, box); return }
            val d = normalize(degrees)
            val s = fitScale(d, surface.width.toFloat(), surface.height.toFloat(), box.width.toFloat(), box.height.toFloat(), cover)
            val turned = quarterTurn(d)
            val visualW = (if (turned) surface.height else surface.width) * s
            val visualH = (if (turned) surface.width else surface.height) * s
            val z = if (visualW > 0f && visualH > 0f) VideoZoom.clampPan(zoom, visualW, visualH) else zoom
            surface.rotation = d.toFloat()
            surface.scaleX = z.scale * s
            surface.scaleY = z.scale * s
            surface.translationX = z.panX
            surface.translationY = z.panY
        }

        /**
         * GROUP K: a SurfaceView is composited by the system, so view scale is never drawn (pinch zoom
         * did nothing on the TV renderer). There the zoom is done by layout: the video frame (the
         * AspectRatioFrameLayout around the surface) is laid out [VideoZoom.State.scale] times the
         * player box and centred, then panned. The GPU effect already turns the picture there.
         */
        private fun applyLaidOut(surface: View, box: View) {
            val frame = surface.parent as? View ?: return
            val lp = frame.layoutParams ?: return
            val z = if (box.width > 0 && box.height > 0) VideoZoom.clampPan(zoom, box.width.toFloat(), box.height.toFloat()) else zoom
            val w = if (z.zoomed) (box.width * z.scale).toInt() else android.view.ViewGroup.LayoutParams.MATCH_PARENT
            val h = if (z.zoomed) (box.height * z.scale).toInt() else android.view.ViewGroup.LayoutParams.MATCH_PARENT
            if (lp.width != w || lp.height != h) {
                lp.width = w; lp.height = h
                (lp as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.CENTER
                frame.layoutParams = lp
            }
            frame.translationX = z.panX
            frame.translationY = z.panY
            surface.rotation = 0f; surface.scaleX = 1f; surface.scaleY = 1f
        }
    }
}
