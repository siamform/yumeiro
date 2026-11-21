/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable

/**
 * F-07 hand-off to another video player with what the stream needs: request headers, subtitles, title and start
 * position, in each app's own published intent extras (MX Player's API, which Just Player follows; VLC's and
 * mpv-android's documented extras). Contracts were read from the apps' public docs and CloudStream's player table
 * (2dcc25c, ideas only). Apps not in the table get the generic chooser with the widely read MX-style extras.
 *
 * Secrets: headers can hold cookies or tokens. They are put in the intent only (the user chose that app) and are
 * never logged.
 */
object ExternalPlayers {
    enum class App(val pkg: String, val label: String) {
        VLC("org.videolan.vlc", "VLC"),
        MPV("is.xyz.mpv", "mpv"),
        MX("com.mxtech.videoplayer.ad", "MX Player"),
        MX_PRO("com.mxtech.videoplayer.pro", "MX Player Pro"),
        JUST("com.brouken.player", "Just Player"),
    }

    data class Sub(val url: String, val name: String)
    data class Watched(val positionMs: Long, val durationMs: Long)

    /** Apps with a documented header extra; for the others Yumeiro warns that protected links may not play. */
    fun passesHeaders(app: App?): Boolean = app == null || app == App.MX || app == App.MX_PRO || app == App.JUST

    private fun cleanHeaders(h: Map<String, String>): Array<String> =
        h.filter { (k, v) -> k.isNotBlank() && v.isNotBlank() && '\n' !in k && '\r' !in k && '\n' !in v && '\r' !in v }
            .flatMap { listOf(it.key, it.value) }.toTypedArray()

    /** Extras as plain values (subtitle lists are URL strings; [toIntent] turns them into Uri arrays). */
    fun extras(app: App?, title: String, positionMs: Long, headers: Map<String, String>, subs: List<Sub>, selectedSub: Int?): Map<String, Any> {
        val pos = positionMs.coerceAtLeast(0L)
        val out = LinkedHashMap<String, Any>()
        out["title"] = title
        val h = cleanHeaders(headers)
        val picked = selectedSub?.let { subs.getOrNull(it) }
        when (app) {
            App.VLC -> {
                out["position"] = pos
                out["from_start"] = pos <= 0L
                (picked ?: subs.firstOrNull())?.let { out["subtitles_location"] = it.url }
            }
            App.MPV -> {
                out["position"] = pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (subs.isNotEmpty()) out["subs"] = subs.map { it.url }
                picked?.let { out["subs.enable"] = listOf(it.url) }
            }
            App.MX, App.MX_PRO, App.JUST, null -> {
                out["position"] = pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (h.isNotEmpty()) out["headers"] = h
                if (subs.isNotEmpty()) {
                    out["subs"] = subs.map { it.url }
                    out["subs.name"] = subs.map { it.name }.toTypedArray()
                    picked?.let { out["subs.enable"] = listOf(it.url) }
                }
                // F-07-RES: also for the chooser ("Choose each time"): MX / Just Player return the position only when
                // asked, and the user may pick them there. Other players ignore the extra.
                out["return_result"] = true
            }
        }
        return out
    }

    /** Position / duration the other app reported back, or null when it said nothing usable. */
    fun result(extras: Map<String, Any?>): Watched? {
        fun num(vararg keys: String): Long? = keys.firstNotNullOfOrNull { k -> (extras[k] as? Number)?.toLong() }
        val dur = num("duration", "extra_duration")?.takeIf { it > 0 }
        val completed = extras["end_by"] == "playback_completion"
        val pos = num("position", "extra_position")?.takeIf { it >= 0 } ?: if (completed && dur != null) dur else null
        return if (pos == null) null else Watched(pos, dur ?: 0L)
    }

    /** Installed apps from the table, for Settings → Default player. */
    fun installed(context: Context): List<App> = App.entries.filter { a ->
        runCatching { context.packageManager.getLaunchIntentForPackage(a.pkg) != null }.getOrDefault(false)
    }

    fun toIntent(app: App?, uri: Uri, mime: String, extras: Map<String, Any>): Intent {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (app != null) i.setPackage(app.pkg)
        if (app == App.VLC) i.component = android.content.ComponentName(app.pkg, "org.videolan.vlc.gui.video.VideoPlayerActivity")
        extras.forEach { (k, v) ->
            when (v) {
                is String -> i.putExtra(k, v)
                is Int -> i.putExtra(k, v)
                is Long -> i.putExtra(k, v)
                is Boolean -> i.putExtra(k, v)
                is Array<*> -> i.putExtra(k, v.map { it.toString() }.toTypedArray())
                is List<*> -> i.putExtra(k, v.map { Uri.parse(it.toString()) as Parcelable }.toTypedArray())
            }
        }
        return i
    }

    fun bundleToMap(b: android.os.Bundle?): Map<String, Any?> =
        b?.keySet()?.associateWith { k -> @Suppress("DEPRECATION") b.get(k) } ?: emptyMap()
}

/**
 * F-07: the activity-result bridge. [MainActivity] registers the launcher once; the player hands off through
 * [launch] and is closed right away, so the result is delivered to [pending] (set by the player) even after
 * the player is gone. One hand-off at a time; a newer one replaces an older pending callback.
 */
object ExternalPlayerHost {
    @Volatile var launcher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
    @Volatile private var pending: ((ExternalPlayers.Watched) -> Unit)? = null

    /** Returns false when nothing could be started (no app, or the launcher is not ready). */
    fun launch(context: Context, intent: Intent, chooserTitle: String?, onResult: (ExternalPlayers.Watched) -> Unit): Boolean {
        val l = launcher
        val target = if (chooserTitle != null) Intent.createChooser(intent, chooserTitle) else intent
        if (l != null && runCatching { l.launch(target) }.isSuccess) { pending = onResult; return true }
        return runCatching { context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun onResult(data: Intent?) {
        val cb = pending ?: return
        pending = null
        ExternalPlayers.result(ExternalPlayers.bundleToMap(data?.extras))?.let { w -> runCatching { cb(w) } }
    }
}
