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

/**
 * Round 13: Settings → Audio → "Quality and audio" and Settings → Player → "Player info overlay".
 * Every value is read by the video player when it opens a video, so a change applies to the next
 * video (and to the one playing when the player can apply it live).
 */
object VideoPrefs {
    private const val PREFS = "yu_video_prefs"

    /** Default video quality. [height] 0 = Auto (adaptive). */
    enum class Quality(val label: String, val height: Int) {
        AUTO("Auto", 0), Q2160("2160p (4K)", 2160), Q1440("1440p", 1440), Q1080("1080p", 1080),
        Q720("720p", 720), Q480("480p", 480), Q360("360p", 360),
    }

    enum class AudioDefault(val label: String, val detail: String) {
        SUB("Sub", tr(R.string.original_audio_with_subtitles, "Original audio with subtitles")),
        DUB("Dub", tr(R.string.dubbed_audio_when_a_source_has, "Dubbed audio when a source has it")),
    }

    enum class Decoder(val label: String, val detail: String) {
        // H-3 (owner item 3): Auto is the default. It is Hardware+ that also switches to the software decoder on its
        // own when a video fails to decode (DecoderAuto), so a video Hardware+ can't play still plays.
        AUTO("Auto", tr(R.string.recommended_phone_decoders_first_ffmpeg_for, "Recommended. Phone decoders first, FFmpeg for audio the phone can't play, and the software decoder on its own when a video won't decode")),
        HARDWARE_PLUS("Hardware+", tr(R.string.phone_decoders_first_ffmpeg_for_audio, "Phone decoders first, FFmpeg for audio the phone can't play (AC-3, E-AC-3, DTS, TrueHD)")),
        HARDWARE("Hardware", tr(R.string.only_the_phones_own_decoders, "Only the phone's own decoders")),
        SOFTWARE("Software", tr(R.string.ffmpeg_and_software_decoders_first_slower, "FFmpeg and software decoders first. Slower, but plays files the phone's decoders get wrong")),
    }

    enum class Renderer(val label: String, val detail: String) {
        AUTO("Auto", tr(R.string.recommended_picks_the_best_surface_for, "Recommended. Picks the best surface for this device and video")),
        SURFACE("SurfaceView", tr(R.string.lowest_battery_use_best_for_hdr, "Lowest battery use, best for HDR")),
        TEXTURE("TextureView", tr(R.string.works_with_every_animation_and_effect, "Works with every animation and effect, uses a little more power")),
    }

    enum class GpuTier(val label: String, val detail: String) {
        LOW("Low-end", tr(R.string.lightest_pass_for_older_phones, "Lightest pass, for older phones")),
        MID("Mid-range", tr(R.string.light_and_smooth_recommended, "Light and smooth. Recommended")),
        HIGH("High-end", tr(R.string.strongest_line_and_detail_restore, "Strongest line and detail restore")),
    }

    enum class DefaultPlayer(val label: String, val detail: String) {
        YUMEIRO("Yumeiro", tr(R.string.play_inside_yumeiro, "Play inside Yumeiro")),
        ASK(tr(R.string.ask_every_time, "Ask every time"), tr(R.string.choose_yumeiro_or_another_app_for, "Choose Yumeiro or another app for each video")),
        EXTERNAL(tr(R.string.another_app, "Another app"), tr(R.string.hand_videos_to_an_installed_player, "Hand videos to an installed player such as VLC or MX Player")),
    }

    /** Buttons in the player's top bar, in the user's order. */
    enum class Control(val label: String) {
        QUALITY("Quality"), AUDIO(tr(R.string.audio_track, "Audio track")), SUBTITLES("Subtitles"), SPEED("Speed"),
        ASPECT(tr(R.string.aspect_ratio, "Aspect ratio")), PIP("Picture-in-picture"), LOCK("Lock"), INFO(tr(R.string.video_info, "Video info")),
        // ROT-1: new entries go last, so a saved order keeps its bar and this one starts in More.
        ROTATE(tr(R.string.rotate_video, "Rotate video")),
    }

    /**
     * PC-1 (owner list 11): the player's other buttons, each can be switched off in Settings > Video player.
     * Off = not drawn; the gestures (double tap to seek, swipe) and the keys keep working.
     */
    enum class Extra(val label: String, val hint: String) {
        PREV_NEXT(tr(R.string.previous_and_next_video, "Previous and next video"), tr(R.string.the_skip_buttons_beside_rewind_and, "The skip buttons beside rewind and forward")),
        SEEK(tr(R.string.rewind_and_forward_10_s, "Rewind and forward 10 s"), tr(R.string.the_round_10_s_buttons_beside, "The round 10 s buttons beside play; double tap still seeks")),
        FULLSCREEN(tr(R.string.fullscreen_button, "Fullscreen button"), tr(R.string.the_button_at_the_end_of, "The button at the end of the seek bar; turning the phone still works")),
    }

    /** PC-1: how many top-bar buttons stay on the bar before the rest go into More (OWN-19 default 3). */
    const val BAR_MIN = 1
    const val BAR_MAX = 6
    const val BAR_DEFAULT = 3

    /** Fields the "Stats for nerds" style overlay can show. */
    enum class InfoField(val label: String) {
        RESOLUTION("Resolution"), SOURCE("Source"), PROVIDER("Provider"), QUALITY("Quality"),
        VIDEO_CODEC(tr(R.string.video_codec, "Video codec")), AUDIO_CODEC(tr(R.string.audio_codec, "Audio codec")), FRAME_RATE(tr(R.string.frame_rate, "Frame rate")),
        VIDEO_BITRATE(tr(R.string.video_bitrate, "Video bitrate")), BUFFER("Buffer"), DROPPED_FRAMES(tr(R.string.dropped_frames, "Dropped frames")),
        DECODER("Decoder"), SPEED("Speed"), AUDIO_TRACK(tr(R.string.audio_track, "Audio track")), SUBTITLE_TRACK(tr(R.string.subtitle_track, "Subtitle track")),
        AUDIO_BOOST(tr(R.string.audio_boost, "Audio boost")),
    }

    val DEFAULT_INFO_FIELDS: Set<InfoField> = setOf(
        InfoField.RESOLUTION, InfoField.SOURCE, InfoField.VIDEO_CODEC, InfoField.AUDIO_CODEC,
        InfoField.FRAME_RATE, InfoField.BUFFER, InfoField.DROPPED_FRAMES,
    )

    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val _quality = MutableStateFlow(Quality.AUTO); val quality: StateFlow<Quality> = _quality.asStateFlow()
    private val _audio = MutableStateFlow(AudioDefault.SUB); val audio: StateFlow<AudioDefault> = _audio.asStateFlow()
    private val _speed = MutableStateFlow(1f); val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _decoder = MutableStateFlow(Decoder.AUTO); val decoder: StateFlow<Decoder> = _decoder.asStateFlow()
    private val _renderer = MutableStateFlow(Renderer.AUTO); val renderer: StateFlow<Renderer> = _renderer.asStateFlow()
    private val _anime4k = MutableStateFlow(false); val anime4k: StateFlow<Boolean> = _anime4k.asStateFlow()
    private val _gpuTier = MutableStateFlow(GpuTier.MID); val gpuTier: StateFlow<GpuTier> = _gpuTier.asStateFlow()
    private val _defaultPlayer = MutableStateFlow(DefaultPlayer.YUMEIRO); val defaultPlayer: StateFlow<DefaultPlayer> = _defaultPlayer.asStateFlow()
    /** F-07: the other app to hand videos to ("" = Android's chooser each time). Package name of an [ExternalPlayers.App]. */
    private val _externalApp = MutableStateFlow(""); val externalApp: StateFlow<String> = _externalApp.asStateFlow()
    private val _controls = MutableStateFlow(Control.entries.toList()); val controls: StateFlow<List<Control>> = _controls.asStateFlow()
    private val _hidden = MutableStateFlow<Set<Control>>(emptySet()); val hidden: StateFlow<Set<Control>> = _hidden.asStateFlow()
    private val _extrasOff = MutableStateFlow<Set<Extra>>(emptySet()); val extrasOff: StateFlow<Set<Extra>> = _extrasOff.asStateFlow()
    private val _barCount = MutableStateFlow(BAR_DEFAULT); val barCount: StateFlow<Int> = _barCount.asStateFlow()
    private val _resume = MutableStateFlow(true); val resume: StateFlow<Boolean> = _resume.asStateFlow()
    private val _infoOverlay = MutableStateFlow(false); val infoOverlay: StateFlow<Boolean> = _infoOverlay.asStateFlow()
    private val _infoFields = MutableStateFlow(DEFAULT_INFO_FIELDS); val infoFields: StateFlow<Set<InfoField>> = _infoFields.asStateFlow()

    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _quality.value = enumOf(p.getString("quality", null), Quality.AUTO)
        _audio.value = enumOf(p.getString("audio", null), AudioDefault.SUB)
        _speed.value = p.getFloat("speed", 1f).takeIf { it in 0.25f..4f } ?: 1f
        _decoder.value = enumOf(p.getString("decoder", null), Decoder.AUTO)
        _renderer.value = enumOf(p.getString("renderer", null), Renderer.AUTO)
        _anime4k.value = p.getBoolean("anime4k", false)
        _gpuTier.value = enumOf(p.getString("gpu_tier", null), GpuTier.MID)
        _defaultPlayer.value = enumOf(p.getString("default_player", null), DefaultPlayer.YUMEIRO)
        _externalApp.value = p.getString("external_app", "").orEmpty()
        _controls.value = orderFrom(p.getString("controls", null))
        _hidden.value = p.getString("hidden_controls", null).orEmpty().split(',').mapNotNull { n -> Control.entries.firstOrNull { it.name == n } }.toSet()
        _extrasOff.value = p.getString("extras_off", null).orEmpty().split(',').mapNotNull { n -> Extra.entries.firstOrNull { it.name == n } }.toSet()
        _barCount.value = p.getInt("bar_count", BAR_DEFAULT).coerceIn(BAR_MIN, BAR_MAX)
        _resume.value = p.getBoolean("resume", true)
        _infoOverlay.value = p.getBoolean("info_overlay", false)
        _infoFields.value = p.getString("info_fields", null)?.let { s -> s.split(',').mapNotNull { n -> InfoField.entries.firstOrNull { it.name == n } }.toSet() } ?: DEFAULT_INFO_FIELDS
        loaded = true
    }

    /** Saved order, with any button added in a later version appended at the end. */
    internal fun orderFrom(saved: String?): List<Control> {
        val list = saved.orEmpty().split(',').mapNotNull { n -> Control.entries.firstOrNull { it.name == n } }.distinct()
        return list + Control.entries.filter { it !in list }
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun edit(c: Context, block: android.content.SharedPreferences.Editor.() -> Unit) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()

    fun setQuality(c: Context, v: Quality) { _quality.value = v; edit(c) { putString("quality", v.name) } }
    fun setAudio(c: Context, v: AudioDefault) {
        _audio.value = v; edit(c) { putString("audio", v.name) }
        // The source lists and the audio track picker follow the same default.
        dev.asfakul.yumeiro.online.AudioLanguages.setPreferred(c, if (v == AudioDefault.SUB) dev.asfakul.yumeiro.online.AudioLanguages.ORIG else dev.asfakul.yumeiro.online.AudioLanguages.DUB_OTHER)
    }
    fun setSpeed(c: Context, v: Float) { _speed.value = v; edit(c) { putFloat("speed", v) } }
    fun setDecoder(c: Context, v: Decoder) { _decoder.value = v; edit(c) { putString("decoder", v.name) } }
    fun setRenderer(c: Context, v: Renderer) { _renderer.value = v; edit(c) { putString("renderer", v.name) } }
    fun setAnime4k(c: Context, v: Boolean) { _anime4k.value = v; edit(c) { putBoolean("anime4k", v) } }
    fun setGpuTier(c: Context, v: GpuTier) { _gpuTier.value = v; edit(c) { putString("gpu_tier", v.name) } }
    fun setExternalApp(c: Context, pkg: String) { _externalApp.value = pkg; edit(c) { putString("external_app", pkg) } }
    fun setDefaultPlayer(c: Context, v: DefaultPlayer) { _defaultPlayer.value = v; edit(c) { putString("default_player", v.name) } }
    fun setControls(c: Context, order: List<Control>, hidden: Set<Control>) {
        _controls.value = order; _hidden.value = hidden
        edit(c) { putString("controls", order.joinToString(",") { it.name }); putString("hidden_controls", hidden.joinToString(",") { it.name }) }
    }
    fun setExtra(c: Context, e: Extra, on: Boolean) {
        val next = if (on) _extrasOff.value - e else _extrasOff.value + e
        _extrasOff.value = next; edit(c) { putString("extras_off", next.joinToString(",") { it.name }) }
    }
    fun setBarCount(c: Context, n: Int) { val v = n.coerceIn(BAR_MIN, BAR_MAX); _barCount.value = v; edit(c) { putInt("bar_count", v) } }

    /** PC-1: where each top-bar button shows: on the bar (first [barCount] shown), in More, or hidden. Pure. */
    enum class Place { BAR, MORE, HIDDEN }
    fun placeOf(order: List<Control>, hidden: Set<Control>, barCount: Int): Map<Control, Place> {
        var shown = 0
        return order.associateWith { c -> if (c in hidden) Place.HIDDEN else if (shown++ < barCount) Place.BAR else Place.MORE }
    }

    fun setResume(c: Context, v: Boolean) { _resume.value = v; edit(c) { putBoolean("resume", v) } }
    fun setInfoOverlay(c: Context, v: Boolean) { _infoOverlay.value = v; edit(c) { putBoolean("info_overlay", v) } }
    fun setInfoFields(c: Context, v: Set<InfoField>) { _infoFields.value = v; edit(c) { putString("info_fields", v.joinToString(",") { it.name }) } }

    /** Moves [control] one place up (-1) or down (+1) in [order]. Pure so it is unit-tested. */
    fun move(order: List<Control>, control: Control, by: Int): List<Control> {
        val i = order.indexOf(control); val j = i + by
        if (i < 0 || j !in order.indices) return order
        return order.toMutableList().apply { removeAt(i); add(j, control) }
    }
}
