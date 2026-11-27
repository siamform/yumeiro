/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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

    val BODY: String get() = tr(R.string.settings_is_now_grouped_by_subject, "Settings is now grouped by subject: You, Video, Music, Downloads & library, Sources & accounts, System. ") +
        tr(R.string.profiles_settings_profiles_each_person_gets, "Profiles: Settings → Profiles. Each person gets their own history, Continue watching and lists, with an optional PIN or fingerprint and their own 18+ switch. ") +
        tr(R.string.private_downloads_the_lock_at_the, "Private downloads: the lock at the top of Downloads, or Settings → Downloads. ") +
        tr(R.string.statistics_settings_your_statistics_or_the, "Statistics: Settings → Your statistics, or the Statistics card in Library music. ") +
        tr(R.string.telegram_channels_no_login_settings_telegram, "Telegram channels (no login): Settings → Telegram, then type @channel or a t.me link. TeraBox and DiskWala links work there too. ") +
        tr(R.string.script_repos_vega_nuvio_skystream_the, "Script repos (Vega, Nuvio, SkyStream): the Script repos tab in Sources. ") +
        tr(R.string.video_quality_anime4k_and_torrents_settings, "Video quality, Anime4K and torrents: Settings → Video quality & playback. Subtitles and skip intro: Settings → Subtitles & intros. ") +
        tr(R.string.hi_res_audio_equalizer_and_lyrics, "Hi-Res audio, equalizer and lyrics: Settings → Music & sound. DNS fallback and proxy: Settings → Network. ") +
        tr(R.string.hold_the_video_for_2x_speed, "Hold the video for 2x speed.")

    fun shouldPost(firstInstall: Long, lastUpdate: Long, version: String): Boolean =
        // 2.0.0 is the 1.9.0 feature set with the player crash fixed; most users skip 1.9.0, so the
        // note is posted for 2.0.x too (same ID: anyone who saw it on 1.9.0 does not get it again).
        // 2.1.x: same note for people who update straight from 1.8.x (release checklist W8).
        (version.startsWith("1.9.") || version.startsWith("2.0.") || version.startsWith("2.1.")) && lastUpdate > firstInstall

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
