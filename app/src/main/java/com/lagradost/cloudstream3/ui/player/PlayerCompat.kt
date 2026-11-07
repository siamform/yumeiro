@file:Suppress("unused", "UNUSED_PARAMETER")

package com.lagradost.cloudstream3.ui.player

import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.utils.ExtractorLink

/*
 * Player surface used by companion extensions (watch-party, sleep timers, sync plugins).
 * Yumeiro has its own player and never creates these fragments, so lookups made by such
 * extensions find nothing and they stay idle instead of crashing the extension load.
 */

enum class CSPlayerEvent(val value: Int) {
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PrevEpisode(6),
    PlayPauseToggle(7),
    ToggleMute(8),
    Restart(9),
    PlayAsAudio(10),
}

enum class CSPlayerLoading { IsPaused, IsPlaying, IsBuffering, IsEnded }

enum class PlayerEventSource { UI, Player, Sync }

enum class SubtitleOrigin { URL, DOWNLOADED_FILE, EMBEDDED_IN_VIDEO }

data class SubtitleData(
    val originalName: String,
    val nameSuffix: String,
    val url: String,
    val origin: SubtitleOrigin,
    val mimeType: String,
    val headers: Map<String, String>,
    val languageCode: String?,
) {
    val name: String get() = "$originalName${if (nameSuffix.isNotBlank()) " $nameSuffix" else ""}"
    fun getId(): String = if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url else "$url|$name"
}

interface IPlayer {
    fun getIsPlaying(): Boolean = false
    fun getPosition(): Long? = null
    fun getDuration(): Long? = null
    fun isActive(): Boolean = false
    fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource = PlayerEventSource.UI) {}
    fun seekTo(time: Long, source: PlayerEventSource = PlayerEventSource.UI) {}
    fun getCurrentPreferredSubtitle(): SubtitleData? = null
    fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean = false
}

private object IdlePlayer : IPlayer

abstract class AbstractPlayerFragment : Fragment() {
    var player: IPlayer = IdlePlayer
}

open class FullScreenPlayer : AbstractPlayerFragment()

open class GeneratorPlayer : FullScreenPlayer()

data class Cache(
    val linkCache: MutableSet<ExtractorLink>,
    val subtitleCache: MutableSet<SubtitleData>,
    var lastCachedTimestamp: Long = System.currentTimeMillis() / 1000L,
    var saturated: Boolean,
)

abstract class VideoGenerator<T>

class RepoLinkGenerator : VideoGenerator<Any>() {
    companion object {
        const val TAG = "RepoLink"
        val cache: HashMap<Pair<String, Int>, Cache> = hashMapOf()
    }
}
