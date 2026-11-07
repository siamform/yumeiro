@file:Suppress("unused")

package com.lagradost.cloudstream3.utils.downloader

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType

/** Download / resume records shared with extensions (same JSON shape as CloudStream). */
object DownloadObjects {
    interface DownloadCached {
        val id: Int
    }

    data class DownloadHeaderCached(
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("type") val type: TvType,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        @JsonProperty("id") override val id: Int,
    ) : DownloadCached

    data class ResumeWatching(
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("updateTime") val updateTime: Long,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
    )
}
