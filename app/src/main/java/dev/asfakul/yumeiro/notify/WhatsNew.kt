/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.util.Log

/**
 * FINAL-47: one Inbox note after updating to 1.9, saying where the new features live. The owner
 * could not find Profiles, Telegram or Statistics after updating; they were in the build but not
 * on any page he looked at. Only updaters get it (a fresh install has the setup tour instead).
 */
object WhatsNew {
    private const val TAG = "WhatsNew"
    const val ID = "whatsnew_1.9"

    const val BODY = "Settings is now grouped by subject: You, Video, Music, Downloads & library, Sources & accounts, System. " +
        "Profiles: Settings → Profiles. Each person gets their own history, Continue watching and lists, with an optional PIN or fingerprint and their own 18+ switch. " +
        "Private downloads: the lock at the top of Downloads, or Settings → Downloads. " +
        "Statistics: Settings → Your statistics, or the Statistics card in Library music. " +
        "Telegram channels (no login): Settings → Telegram & web sites, then type @channel or a t.me link. TeraBox and DiskWala links work there too. " +
        "Script repos (Vega, Nuvio, SkyStream): the Script repos tab in Sources. " +
        "Video quality, Anime4K and torrents: Settings → Video quality & playback. Subtitles and skip intro: Settings → Subtitles & intros. " +
        "Hi-Res audio, equalizer and lyrics: Settings → Music & sound. DNS fallback and proxy: Settings → Network. " +
        "Hold the video for 2x speed."

    fun shouldPost(firstInstall: Long, lastUpdate: Long, version: String): Boolean =
        // 2.0.0 is the 1.9.0 feature set with the player crash fixed; most users skip 1.9.0, so the
        // note is posted for 2.0.x too (same ID: anyone who saw it on 1.9.0 does not get it again).
        (version.startsWith("1.9.") || version.startsWith("2.0.")) && lastUpdate > firstInstall

    /** Call off the main thread (it reads the inbox file). */
    fun postOnce(c: Context) {
        runCatching {
            val pi = c.packageManager.getPackageInfo(c.packageName, 0)
            if (!shouldPost(pi.firstInstallTime, pi.lastUpdateTime, pi.versionName.orEmpty())) return
            val added = Inbox.add(c, Inbox.Item(ID, Inbox.Kind.NEWS, "New in ${pi.versionName}: where to find it", BODY, System.currentTimeMillis()))
            if (added) Log.i(TAG, "posted")
        }.onFailure { Log.w(TAG, "skipped: ${it.javaClass.simpleName}") }
    }
}
