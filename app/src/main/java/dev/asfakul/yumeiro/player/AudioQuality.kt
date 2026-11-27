/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings → Audio quality: the highest quality Yumeiro asks for, with separate limits for Wi-Fi
 * and mobile data.
 *
 * Honest by design: a tier is a ceiling, never a promise. Your own files (and home servers) play
 * exactly as they are stored, so a 24-bit FLAC stays 24-bit and an Atmos track is handed to the
 * phone's decoder. YouTube Music streams top out at about 256 kbps AAC / 160 kbps Opus, so any
 * tier above that simply means "the best YouTube offers"; Data saver asks for the smallest stream.
 */
object AudioQuality {

    enum class Tier(val label: String, val spec: String, val detail: String, val kana: String, val streamCapKbps: Int) {
        ATMOS(tr(R.string.dolby_atmos, "Dolby Atmos"), tr(R.string.spatial_immersive_audio, "Spatial · immersive audio"), tr(R.string.atmos_tracks_e_ac_3_joc, "Atmos tracks (E-AC-3 JOC / AC-4) play with spatial sound on phones and headphones that support it"), "空間", Int.MAX_VALUE),
        HIRES_MAX(tr(R.string.hi_res_max, "Hi-Res Max"), tr(R.string.up_to_24_bit_192_khz, "Up to 24-bit / 192 kHz · FLAC"), tr(R.string.studio_master_lossless_files_play_bit, "Studio-master lossless files play bit for bit"), "極", Int.MAX_VALUE),
        HIRES("Hi-Res", "24-bit / 96 kHz · FLAC", tr(R.string.high_resolution_lossless_larger_files, "High-resolution lossless; larger files"), "高", Int.MAX_VALUE),
        CD(tr(R.string.cd_lossless, "CD Lossless"), "16-bit / 44.1 kHz · FLAC", tr(R.string.the_same_quality_as_an_audio, "The same quality as an audio CD"), "盤", Int.MAX_VALUE),
        HIGH("High", "320 kbps · MP3 / AAC", tr(R.string.near_transparent_compressed_audio, "Near-transparent compressed audio"), "良", 320),
        YOUTUBE(tr(R.string.youtube_music, "YouTube Music"), "128–256 kbps · AAC / Opus", tr(R.string.what_youtube_music_streams_the_best, "What YouTube Music streams; the best it offers is picked"), "標", 256),
        SAVER(tr(R.string.data_saver, "Data saver"), "96 kbps · HE-AAC / Opus", tr(R.string.smallest_streams_for_mobile_data, "Smallest streams, for mobile data"), "節", 96),
        ;
        val lossless get() = this == HIRES_MAX || this == HIRES || this == CD
    }

    private const val PREFS = "yu_audio_quality"
    private val _wifi = MutableStateFlow(Tier.HIRES_MAX)
    val wifi: StateFlow<Tier> = _wifi.asStateFlow()
    private val _mobile = MutableStateFlow(Tier.YOUTUBE)
    val mobile: StateFlow<Tier> = _mobile.asStateFlow()
    @Volatile private var app: Context? = null

    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _wifi.value = of(p.getString("wifi", null)) ?: Tier.HIRES_MAX
        _mobile.value = of(p.getString("mobile", null)) ?: Tier.YOUTUBE
        app = c
    }

    fun of(name: String?): Tier? = Tier.entries.firstOrNull { it.name == name }

    fun setWifi(context: Context, t: Tier) { _wifi.value = t; save(context, "wifi", t) }
    fun setMobile(context: Context, t: Tier) { _mobile.value = t; save(context, "mobile", t) }

    private fun save(context: Context, key: String, t: Tier) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, t.name).apply()
    }

    /** The tier that applies right now (mobile data / metered Wi-Fi use the mobile limit). */
    fun current(): Tier {
        val c = app ?: return _wifi.value
        val metered = runCatching { c.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }.getOrDefault(false)
        return if (metered) _mobile.value else _wifi.value
    }

    /**
     * Picks a bitrate (kbps) from [available] for the [tier]: the best stream at or under the cap
     * (with a little slack, YouTube's "128" is often 130), or the smallest one when every stream is
     * above it. Pure so it is unit-tested.
     */
    fun choose(available: List<Int>, tier: Tier): Int? {
        if (available.isEmpty()) return null
        if (tier.streamCapKbps == Int.MAX_VALUE) return available.max()
        return available.filter { it <= tier.streamCapKbps + 8 }.maxOrNull() ?: available.min()
    }
}
