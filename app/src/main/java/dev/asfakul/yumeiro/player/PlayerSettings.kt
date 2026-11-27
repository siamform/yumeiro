/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted video-player preferences. */
object PlayerSettings {
    private const val PREFS = "mt_player"
    private val _backgroundPlay = MutableStateFlow(true)
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()
    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()
    /** What Back does while a video plays: shrink to the floating player, system PiP, or close. */
    enum class BackAction(val label: String, val detail: String) {
        MINIMIZE(tr(R.string.floating_player, "Floating player"), tr(R.string.shrink_into_a_small_player_over, "Shrink into a small player over the app so you can keep browsing (like YouTube). Leaving the app turns it into picture-in-picture")),
        PIP("Picture-in-picture", tr(R.string.send_the_whole_app_into_androids, "Send the whole app into Android's picture-in-picture window right away")),
        CLOSE("Close", tr(R.string.stop_the_video_and_close_the, "Stop the video and close the player")),
    }
    private val _backAction = MutableStateFlow(BackAction.MINIMIZE)
    val backAction: StateFlow<BackAction> = _backAction.asStateFlow()
    private val _captionsDefault = MutableStateFlow(true)
    val captionsDefault: StateFlow<Boolean> = _captionsDefault.asStateFlow()
    /** F-06: swipe past 100 % into a volume boost (up to 200 %). Phones only; TVs leave loudness to the TV / receiver. */
    private val _volumeBoost = MutableStateFlow(true)
    val volumeBoost: StateFlow<Boolean> = _volumeBoost.asStateFlow()
    private val _subtitleLanguage = MutableStateFlow("eng")
    val subtitleLanguage: StateFlow<String> = _subtitleLanguage.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _backgroundPlay.value = p.getBoolean("background_play", true)
        _autoPip.value = p.getBoolean("auto_pip", true)
        _captionsDefault.value = p.getBoolean("captions_default", true)
        _volumeBoost.value = p.getBoolean("volume_boost", true)
        _backAction.value = backActionFrom(p.getString(KEY_BACK_ACTION, null), p.getString("back_action", null))
        _subtitleLanguage.value = p.getString("subtitle_language", "eng") ?: "eng"
        loaded = true
    }

    private fun put(context: Context, key: String, value: Any) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        when (value) { is Boolean -> e.putBoolean(key, value); is String -> e.putString(key, value) }
        e.apply()
    }

    fun setBackgroundPlay(c: Context, v: Boolean) { _backgroundPlay.value = v; put(c, "background_play", v) }
    fun setAutoPip(c: Context, v: Boolean) { _autoPip.value = v; put(c, "auto_pip", v) }
    fun setBackAction(c: Context, v: BackAction) { _backAction.value = v; put(c, KEY_BACK_ACTION, v.name) }

    // v2: the default moved from system PiP (which shrank the whole app) to the in-app floating
    // player. Old saved values migrate: CLOSE stays, PIP / MINIMIZE become the floating player.
    private const val KEY_BACK_ACTION = "back_action_v2"

    internal fun backActionFrom(v2: String?, legacy: String?): BackAction {
        BackAction.entries.firstOrNull { it.name == v2 }?.let { return it }
        return if (legacy == BackAction.CLOSE.name) BackAction.CLOSE else BackAction.MINIMIZE
    }
    fun setCaptionsDefault(c: Context, v: Boolean) { _captionsDefault.value = v; put(c, "captions_default", v) }
    fun setVolumeBoost(c: Context, v: Boolean) { _volumeBoost.value = v; put(c, "volume_boost", v); if (!v) VolumeBoost.reset() }
    fun setSubtitleLanguage(c: Context, v: String) { _subtitleLanguage.value = v; put(c, "subtitle_language", v) }
}
