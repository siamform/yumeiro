/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Offline / download preferences (Settings → Downloads). */
object DownloadSettings {
    /** Auto-delete choices in days; 0 = keep until deleted. */
    val AUTO_DELETE_CHOICES = listOf(0, 1, 3, 7, 14, 30)
    /** Episode quality choices (max height); 0 = best available. */
    val QUALITY_CHOICES = listOf(0, 1080, 720, 480, 360)

    private var prefs: SharedPreferences? = null

    private val _autoDeleteDays = MutableStateFlow(0)
    /** Days after a download finishes before it is removed (pinned "Keep forever" items stay). */
    val autoDeleteDays: StateFlow<Int> = _autoDeleteDays.asStateFlow()

    private val _quality = MutableStateFlow(0)
    /** Highest episode quality to download (0 = best). Smaller saves space and data. */
    val quality: StateFlow<Int> = _quality.asStateFlow()

    private val _parallel = MutableStateFlow(2)
    /** Downloads at the same time (one per extension at a time, like Aniyomi, to avoid IP bans). */
    val parallel: StateFlow<Int> = _parallel.asStateFlow()

    private val _keepByDefault = MutableStateFlow(false)
    /** New episode downloads start as "Keep forever" (not auto-deleted). */
    val keepByDefault: StateFlow<Boolean> = _keepByDefault.asStateFlow()

    private val _saveYouTubeToPhone = MutableStateFlow(true)
    /** YouTube videos / music also go to the phone's Movies / Music folder (Android 10+). */
    val saveYouTubeToPhone: StateFlow<Boolean> = _saveYouTubeToPhone.asStateFlow()

    private val _askFirst = MutableStateFlow(true)
    /** Show the "where and how" confirmation before a download starts. */
    val askFirst: StateFlow<Boolean> = _askFirst.asStateFlow()

    private val _showTips = MutableStateFlow(true)
    /** The "how downloads work" card on the Downloads page. */
    val showTips: StateFlow<Boolean> = _showTips.asStateFlow()

    private val _downloader = MutableStateFlow(ExternalDownloader.IN_APP)
    /** Who saves "Download this video": "" = Yumeiro, a package name (1DM, ADM…), or "chooser". */
    val downloader: StateFlow<String> = _downloader.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("download_settings", Context.MODE_PRIVATE).also { prefs = it }
        _autoDeleteDays.value = p.getInt("auto_delete_days", 0).takeIf { it in AUTO_DELETE_CHOICES } ?: 0
        _quality.value = p.getInt("quality", 0).takeIf { it in QUALITY_CHOICES } ?: 0
        _parallel.value = p.getInt("parallel", 2).coerceIn(1, 3)
        _keepByDefault.value = p.getBoolean("keep_default", false)
        _saveYouTubeToPhone.value = p.getBoolean("yt_to_phone", true)
        _askFirst.value = p.getBoolean("ask_first", true)
        _showTips.value = p.getBoolean("show_tips", true)
        _downloader.value = p.getString("downloader", null).orEmpty()
    }

    fun setAutoDeleteDays(context: Context, days: Int) { init(context); _autoDeleteDays.value = days; prefs?.edit()?.putInt("auto_delete_days", days)?.apply() }
    fun setQuality(context: Context, height: Int) { init(context); _quality.value = height; prefs?.edit()?.putInt("quality", height)?.apply() }
    fun setParallel(context: Context, n: Int) { init(context); _parallel.value = n.coerceIn(1, 3); prefs?.edit()?.putInt("parallel", _parallel.value)?.apply() }
    fun setKeepByDefault(context: Context, on: Boolean) { init(context); _keepByDefault.value = on; prefs?.edit()?.putBoolean("keep_default", on)?.apply() }
    fun setSaveYouTubeToPhone(context: Context, on: Boolean) { init(context); _saveYouTubeToPhone.value = on; prefs?.edit()?.putBoolean("yt_to_phone", on)?.apply() }

    fun setAskFirst(context: Context, on: Boolean) { init(context); _askFirst.value = on; prefs?.edit()?.putBoolean("ask_first", on)?.apply() }
    fun setShowTips(context: Context, on: Boolean) { init(context); _showTips.value = on; prefs?.edit()?.putBoolean("show_tips", on)?.apply() }

    fun setDownloader(context: Context, pkg: String) { init(context); _downloader.value = pkg; prefs?.edit()?.putString("downloader", pkg)?.apply() }

    fun autoDeleteLabel(days: Int) = when (days) { 0 -> "Never"; 1 -> "1 day"; else -> "$days days" }
    fun qualityLabel(h: Int) = if (h == 0) "Best" else "${h}p"
}
