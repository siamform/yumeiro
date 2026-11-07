/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

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
        MINIMIZE("Floating player", "Shrink into a small player over the app so you can keep browsing (like YouTube). Leaving the app turns it into picture-in-picture"),
        PIP("Picture-in-picture", "Send the whole app into Android's picture-in-picture window right away"),
        CLOSE("Close", "Stop the video and close the player"),
    }
    private val _backAction = MutableStateFlow(BackAction.MINIMIZE)
    val backAction: StateFlow<BackAction> = _backAction.asStateFlow()
    private val _captionsDefault = MutableStateFlow(true)
    val captionsDefault: StateFlow<Boolean> = _captionsDefault.asStateFlow()
    private val _subtitleLanguage = MutableStateFlow("eng")
    val subtitleLanguage: StateFlow<String> = _subtitleLanguage.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _backgroundPlay.value = p.getBoolean("background_play", true)
        _autoPip.value = p.getBoolean("auto_pip", true)
        _captionsDefault.value = p.getBoolean("captions_default", true)
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
    fun setSubtitleLanguage(c: Context, v: String) { _subtitleLanguage.value = v; put(c, "subtitle_language", v) }
}
