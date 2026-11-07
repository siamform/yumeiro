@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI

open class KitsuApi : SyncAPI() {
    override val name = "Kitsu"
    override val idPrefix = "kitsu"
    override val mainUrl = "https://kitsu.app"
    override val syncIdName = "Kitsu"
}

/** CloudStream's on-device bookmark list. */
open class LocalList : SyncAPI() {
    override val name = "Local"
    override val idPrefix = "local"
    override val requiresLogin = false
}

open class OpenSubtitlesApi : SubtitleAPI() {
    override val name = "OpenSubtitles"
    override val idPrefix = "opensubtitles"

    companion object {
        const val HOST = "https://api.opensubtitles.com/api/v1"
        const val API_KEY = ""
        /** Headers OpenSubtitles requires; extensions add their own Api-Key. */
        const val userAgent = "Cloudstream3 v0.2"
        val headers: Map<String, String> = mapOf("user-agent" to userAgent, "Api-Key" to API_KEY)
    }
}
