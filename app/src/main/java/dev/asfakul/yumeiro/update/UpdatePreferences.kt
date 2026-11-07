/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import android.content.Context
import androidx.core.content.edit

/** Small persisted state for the updater (survives process death). */
internal class UpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mt_updater", Context.MODE_PRIVATE)

    var autoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }

    /**
     * Silent background installs were removed: every update is shown to the user, who decides
     * whether to install it. Always false; the old stored value is cleared.
     */
    var seamlessUpdates: Boolean
        get() { if (prefs.contains(KEY_SEAMLESS)) prefs.edit { remove(KEY_SEAMLESS) }; return false }
        set(@Suppress("UNUSED_PARAMETER") value) { prefs.edit { remove(KEY_SEAMLESS) } }

    /** Version installed by a seamless update, used to post "Updated to vX" once. */
    var pendingUpdatedVersion: String?
        get() = prefs.getString(KEY_PENDING_UPDATED, null)
        set(value) = prefs.edit { putString(KEY_PENDING_UPDATED, value) }

    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    /** "Remind me later" snooze: the popup stays hidden for this version until the time passes. */
    fun snooze(versionName: String, untilMillis: Long) = prefs.edit {
        putString(KEY_SNOOZE_VERSION, versionName)
        putLong(KEY_SNOOZE_UNTIL, untilMillis)
    }

    fun isSnoozed(versionName: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getString(KEY_SNOOZE_VERSION, null) == versionName && now < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)

    fun clearSnooze() = prefs.edit { remove(KEY_SNOOZE_VERSION); remove(KEY_SNOOZE_UNTIL) }

    /** Active / finished DownloadManager job, plus the release JSON it belongs to. */
    var downloadId: Long
        get() = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        set(value) = prefs.edit { putLong(KEY_DOWNLOAD_ID, value) }

    var downloadRelease: String?
        get() = prefs.getString(KEY_DOWNLOAD_RELEASE, null)
        set(value) = prefs.edit { putString(KEY_DOWNLOAD_RELEASE, value) }

    var downloadSilent: Boolean
        get() = prefs.getBoolean(KEY_DOWNLOAD_SILENT, false)
        set(value) = prefs.edit { putBoolean(KEY_DOWNLOAD_SILENT, value) }

    fun clearDownload() = prefs.edit {
        remove(KEY_DOWNLOAD_ID); remove(KEY_DOWNLOAD_RELEASE); remove(KEY_DOWNLOAD_SILENT)
    }

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_LAST_CHECK = "last_check"
        // v2: seamless is opt-in again (the popup is the default), so old "on" values are ignored.
        const val KEY_SEAMLESS = "seamless_updates_v2"
        const val KEY_DOWNLOAD_SILENT = "download_silent"
        const val KEY_PENDING_UPDATED = "pending_updated_version"
        const val KEY_SNOOZE_VERSION = "snooze_version"
        const val KEY_SNOOZE_UNTIL = "snooze_until"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_RELEASE = "download_release"
    }
}
