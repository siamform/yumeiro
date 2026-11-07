@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.syncproviders.SyncAPI

/** Simkl tracker. Extensions use [Companion.getPosterUrl] and the public media models. */
open class SimklApi : SyncAPI() {
    override val name = "Simkl"
    override val idPrefix = "simkl"
    override val mainUrl = "https://api.simkl.com"
    override val syncIdName = "Simkl"
    override val hasOAuth2 = true

    companion object {
        const val SIMKL_CACHE_KEY = "SIMKL_API_CACHE"

        /** Same poster CDN rule as CloudStream: "_m" medium posters on simkl.in. */
        fun getPosterUrl(poster: String): String = "https://wsrv.nl/?url=https://simkl.in/posters/${poster}_m.webp"

        data class MediaObject(
            @JsonProperty("title") val title: String?,
            @JsonProperty("year") val year: Int?,
            @JsonProperty("ids") val ids: Map<String, Any?>?,
            @JsonProperty("total_episodes") val totalEpisodes: Int? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("seasons") val seasons: List<Any?>? = null,
            @JsonProperty("episodes") val episodes: List<Any?>? = null,
        )
    }
}
