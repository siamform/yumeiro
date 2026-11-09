/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

/** One listen, in the shape Last.fm (and any later scrobbler, e.g. ListenBrainz) needs. */
data class ScrobblePlay(val artist: String, val track: String, val album: String, val durationSec: Int, val startedAtSec: Long)

/**
 * Cleans the names music sources hand us so lookups and scrobbles match the real song:
 * YouTube's auto-generated "Artist - Topic" channels, "VEVO" channels, "(Official Video)",
 * "[MV]", "(Lyrics)", "| 4K" and the like. Local tags pass through almost unchanged.
 */
object TrackNames {
    private val unknown = setOf("", "<unknown>", "unknown", "unknown artist", "various artists")
    private val noise = Regex(
        """\s*[(\[](?:official\s*)?(?:music\s*)?(?:video|audio|lyric(?:s)?(?:\s*video)?|visuali[sz]er|mv|m/v|hd|hq|4k|live|explicit|color coded lyrics)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val pipeTail = Regex("""\s*[|｜].*$""")

    fun isUnknown(s: String?): Boolean = s == null || s.trim().lowercase() in unknown

    fun artist(raw: String): String = raw.trim()
        .removeSuffix(" - Topic").replace(Regex("""(?i)\s*VEVO$"""), "").replace(Regex("""(?i)\s*official$"""), "").trim()

    /** Title without video noise. "Artist - Song" titles from video uploads keep only the song when [artist] matches. */
    fun title(raw: String, artist: String = ""): String {
        var t = raw.replace(noise, "").replace(pipeTail, "").trim()
        val a = artist(artist)
        if (a.isNotBlank()) {
            val dash = Regex("""^\s*${Regex.escape(a)}\s*[-–—]\s*""", RegexOption.IGNORE_CASE)
            t = t.replace(dash, "")
        }
        return t.trim().ifBlank { raw.trim() }
    }
}

/**
 * Pure scrobble state machine, fed with player snapshots (about every 300 ms while playing).
 * Last.fm's rules (https://www.last.fm/api/scrobbling): only tracks longer than 30 s; scrobble once
 * the track has been listened to for half its length or 4 minutes, whichever comes first. Listening
 * time is wall-clock time while playing, so seeking ahead doesn't count. A restart of the same
 * track (repeat one, or "previous" back to 0) is a new listen.
 */
class ScrobbleTracker(private val minTrackMs: Long = 30_000, private val maxNeededMs: Long = 240_000, private val maxTickMs: Long = 2_000) {
    sealed interface Event {
        data class NowPlaying(val play: ScrobblePlay) : Event
        data class Scrobble(val play: ScrobblePlay) : Event
    }

    private var key: String? = null
    private var play: ScrobblePlay? = null
    private var listenedMs = 0L
    private var lastTick = 0L
    private var lastPosition = 0L
    private var announced = false
    private var scrobbled = false

    internal fun neededMs(durationMs: Long): Long = minOf(durationMs / 2, maxNeededMs)

    /**
     * [trackKey] identifies the queue entry (null when nothing plays). Returns what to send, if anything.
     */
    fun feed(trackKey: String?, artist: String, title: String, album: String, isPlaying: Boolean, positionMs: Long, durationMs: Long, nowMs: Long): List<Event> {
        val out = ArrayList<Event>(2)
        val restarted = trackKey == key && positionMs + 3_000 < lastPosition && positionMs < 5_000
        if (trackKey != key || restarted) {
            key = trackKey; listenedMs = 0; announced = false; scrobbled = false; lastTick = nowMs
            play = if (trackKey == null || TrackNames.isUnknown(artist) || title.isBlank()) null else {
                val a = TrackNames.artist(artist)
                ScrobblePlay(a, TrackNames.title(title, a), album.takeUnless { TrackNames.isUnknown(it) }.orEmpty(), (durationMs / 1000).toInt(), nowMs / 1000)
            }
        }
        lastPosition = positionMs
        val p = play ?: return out
        if (isPlaying) {
            listenedMs += (nowMs - lastTick).coerceIn(0, maxTickMs)
            if (!announced) { announced = true; out += Event.NowPlaying(p) }
        }
        lastTick = nowMs
        if (durationMs > 0 && p.durationSec <= 0) play = p.copy(durationSec = (durationMs / 1000).toInt())
        val dur = durationMs.takeIf { it > 0 } ?: (p.durationSec * 1000L)
        if (!scrobbled && dur > minTrackMs && listenedMs >= neededMs(dur)) {
            scrobbled = true
            out += Event.Scrobble(play!!)
        }
        return out
    }
}
