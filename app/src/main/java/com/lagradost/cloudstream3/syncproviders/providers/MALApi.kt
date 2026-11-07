@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.syncproviders.SyncAPI

/** MyAnimeList tracker. The public API response models are complete for extensions that parse them. */
open class MALApi : SyncAPI() {
    override val name = "MAL"
    override val idPrefix = "mal"
    override val mainUrl = "https://myanimelist.net"
    override val syncIdName = "MyAnimeList"
    override val hasOAuth2 = true

    data class MainPicture(
        @JsonProperty("medium") val medium: String,
        @JsonProperty("large") val large: String,
    )

    data class Genres(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
    )

    data class StartSeason(
        @JsonProperty("year") val year: Int,
        @JsonProperty("season") val season: String,
    )

    data class Node(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("main_picture") val mainPicture: MainPicture?,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("num_episodes") val numEpisodes: Int? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("start_date") val startDate: String? = null,
        @JsonProperty("end_date") val endDate: String? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("mean") val mean: Double? = null,
        @JsonProperty("genres") val genres: List<Genres>? = null,
        @JsonProperty("start_season") val startSeason: StartSeason? = null,
    )

    data class Recommendations(
        @JsonProperty("node") val node: Node? = null,
        @JsonProperty("num_recommendations") val numRecommendations: Int? = null,
    )

    data class MalAnime(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("main_picture") val mainPicture: MainPicture?,
        @JsonProperty("start_date") val startDate: String? = null,
        @JsonProperty("end_date") val endDate: String? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("mean") val mean: Double? = null,
        @JsonProperty("rank") val rank: Int? = null,
        @JsonProperty("popularity") val popularity: Int? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = null,
        @JsonProperty("num_episodes") val numEpisodes: Int? = null,
        @JsonProperty("start_season") val startSeason: StartSeason? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("average_episode_duration") val averageEpisodeDuration: Int? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("pictures") val pictures: ArrayList<MainPicture>? = null,
        @JsonProperty("background") val background: String? = null,
        @JsonProperty("recommendations") val recommendations: ArrayList<Recommendations>? = null,
    )
}
