/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import java.net.URI

/**
 * Streaming platforms that need special handling. Yumeiro never extracts streams from services
 * whose terms or DRM only allow their own player; those links are handed to Android, which opens
 * the official app (App Links) or the browser.
 */
object PlatformRules {

    sealed interface Rule {
        val platform: String
    }

    /** Only the platform's own player may play this content: open the official app / website. */
    data class Handoff(override val platform: String, val reason: String) : Rule

    /** A web player that loads authorised streams at runtime; resolve through the page (and the user's session). */
    data class WebSession(override val platform: String, val note: String) : Rule

    private val YOUTUBE = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val YOUTUBE_MUSIC = setOf("music.youtube.com")

    private val DRM_PLATFORMS: Map<String, String> = mapOf(
        "netflix.com" to "Netflix",
        "primevideo.com" to "Prime Video",
        "disneyplus.com" to "Disney+",
        "hotstar.com" to "JioHotstar",
        "jiocinema.com" to "JioCinema",
        "hulu.com" to "Hulu",
        "max.com" to "Max",
        "spotify.com" to "Spotify",
        "hoichoi.tv" to "hoichoi",
        "chorki.com" to "Chorki",
        "bioscopelive.com" to "Bioscope",
        "binge.buzz" to "Binge",
        "zee5.com" to "ZEE5",
        "sonyliv.com" to "SonyLIV",
    )

    private val WEB_SESSION = mapOf(
        "toffeelive.com" to ("Toffee" to "Toffee's web player loads each channel's stream after it checks your session and region " +
            "(Toffee is available in Bangladesh only). Open the page in Yumeiro's page browser, sign in if Toffee asks, " +
            "and start the channel there — the authorised stream it plays is picked up with its cookies and headers. " +
            "DRM-protected channels and anything outside your region stay unavailable."),
    )

    fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") }.getOrNull()

    private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun ruleFor(url: String): Rule? {
        val host = hostOf(url) ?: return null
        if (YOUTUBE_MUSIC.any { matches(host, it) }) return Handoff("YouTube Music", youTubeReason)
        if (YOUTUBE.any { matches(host, it) }) return Handoff("YouTube", youTubeReason)
        DRM_PLATFORMS.entries.firstOrNull { matches(host, it.key) }?.let { (_, name) ->
            return Handoff(name, "$name content is DRM-protected and licensed only to $name's own apps and website, so Yumeiro can't play it.")
        }
        WEB_SESSION.entries.firstOrNull { matches(host, it.key) }?.let { (_, v) -> return WebSession(v.first, v.second) }
        return null
    }

    private const val youTubeReason = "YouTube videos can only be played through YouTube's own player (its terms don't allow other players to fetch the streams). Open it in the YouTube app instead."

    /** True for YouTube links found inside pages (embedded players). */
    fun isYouTube(url: String): Boolean = hostOf(url)?.let { h -> (YOUTUBE + YOUTUBE_MUSIC).any { matches(h, it) } } == true
}
