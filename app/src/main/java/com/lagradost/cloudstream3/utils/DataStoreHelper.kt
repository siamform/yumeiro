@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.lagradost.cloudstream3.utils

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeys
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects

/**
 * CloudStream's per-profile user data store (bookmarks, resume points, watch states, preferences).
 *
 * Extensions read and write it directly, so Yumeiro keeps the same keys and JSON shapes in its own
 * preferences. MT has a single profile ("0"); MT's own library and history are exposed to
 * extensions through [com.lagradost.cloudstream3.ui.home.HomeViewModel.getResumeWatching].
 */
object DataStoreHelper {
    private const val TAG = "data_store_helper"
    const val RESULT_WATCH_STATE = "result_watch_state"
    const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
    const val RESULT_SUBSCRIBED_STATE_DATA = "result_subscribed_state_data"
    const val RESULT_FAVORITES_STATE_DATA = "result_favorites_state_data"
    const val RESULT_RESUME_WATCHING = "result_resume_watching_2"
    const val RESULT_EPISODE = "result_episode"
    const val VIDEO_POS_DUR = "video_pos_dur"
    const val VIDEO_WATCH_STATE = "video_watch_state"
    const val USER_SELECTED_HOMEPAGE_API = "home_api_used"

    data class PosDur(
        @JsonProperty("position") val position: Long,
        @JsonProperty("duration") val duration: Long,
    ) {
        fun fixVisual(): PosDur = if (duration <= 0) PosDur(0, duration) else this
        fun toPercentage(): Int = if (duration <= 0) 0 else ((position * 100) / duration).toInt().coerceIn(0, 100)
    }

    data class Account(
        @JsonProperty("keyIndex") val keyIndex: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("customImage") val customImage: String? = null,
        @JsonProperty("defaultImageIndex") val defaultImageIndex: Int,
        @JsonProperty("lockPin") val lockPin: String? = null,
    )

    abstract class LibrarySearchResponse(
        override var id: Int? = null,
        open val latestUpdatedTime: Long = 0L,
        override val name: String = "",
        override val url: String = "",
        override val apiName: String = "",
        override var type: TvType? = null,
        override var posterUrl: String? = null,
        open val year: Int? = null,
        open val syncData: Map<String, String>? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        open val plot: String? = null,
        override var score: Score? = null,
        open val tags: List<String>? = null,
    ) : SearchResponse

    data class SubscribedData(
        @JsonProperty("subscribedTime") val subscribedTime: Long,
        @JsonProperty("lastSeenEpisodeCount") val lastSeenEpisodeCount: Map<DubStatus, Int?>,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("latestUpdatedTime") override val latestUpdatedTime: Long,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType?,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") override val year: Int?,
        @JsonProperty("syncData") override val syncData: Map<String, String>? = null,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("plot") override val plot: String? = null,
        @JsonProperty("score") override var score: Score? = null,
        @JsonProperty("tags") override val tags: List<String>? = null,
    ) : LibrarySearchResponse()

    data class BookmarkedData(
        @JsonProperty("bookmarkedTime") val bookmarkedTime: Long,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("latestUpdatedTime") override val latestUpdatedTime: Long,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType?,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") override val year: Int?,
        @JsonProperty("syncData") override val syncData: Map<String, String>? = null,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("plot") override val plot: String? = null,
        @JsonProperty("score") override var score: Score? = null,
        @JsonProperty("tags") override val tags: List<String>? = null,
    ) : LibrarySearchResponse()

    data class FavoritesData(
        @JsonProperty("favoritesTime") val favoritesTime: Long,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("latestUpdatedTime") override val latestUpdatedTime: Long,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType?,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") override val year: Int?,
        @JsonProperty("syncData") override val syncData: Map<String, String>? = null,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("plot") override val plot: String? = null,
        @JsonProperty("score") override var score: Score? = null,
        @JsonProperty("tags") override val tags: List<String>? = null,
    ) : LibrarySearchResponse()

    data class ResumeWatchingResult(
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("watchPos") val watchPos: PosDur?,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("parentId") val parentId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("score") override var score: Score? = null,
    ) : SearchResponse

    // ---- profiles (MT has one) ----
    var accounts: Array<Account>
        get() = getKey<Array<Account>>("$TAG/account") ?: arrayOf()
        set(value) { setKey("$TAG/account", value) }
    var selectedKeyIndex: Int
        get() = getKey<Int>("$TAG/account_key_index") ?: 0
        set(value) { setKey("$TAG/account_key_index", value) }
    val currentAccount: String get() = selectedKeyIndex.toString()

    fun setAccount(account: Account) { selectedKeyIndex = account.keyIndex }
    fun setAccount(account: Account, showToast: Boolean) = setAccount(account)
    fun getDefaultAccount(context: Context): Account =
        accounts.firstOrNull { it.keyIndex == 0 } ?: Account(keyIndex = 0, name = "Yumeiro", defaultImageIndex = 0)
    fun getAccounts(context: Context): List<Account> = accounts.toMutableList().apply {
        if (none { it.keyIndex == 0 }) add(0, getDefaultAccount(context))
    }
    fun getCurrentAccount(): Account? = accounts.firstOrNull { it.keyIndex == selectedKeyIndex }

    var currentHomePage: String?
        get() = getKey<String>("$currentAccount/$USER_SELECTED_HOMEPAGE_API")
        set(value) {
            if (value == null) removeKey(currentAccount, USER_SELECTED_HOMEPAGE_API) else setKey("$currentAccount/$USER_SELECTED_HOMEPAGE_API", value)
        }

    // ---- preferences ----
    private fun pref(key: String) = "$currentAccount/pref/$key"
    var pinnedProviders: Array<String>
        get() = getKey<Array<String>>(pref("pinned_providers")) ?: emptyArray()
        set(value) { setKey(pref("pinned_providers"), value) }
    var searchPreferenceProviders: List<String>
        get() = getKey<Array<String>>(pref("search_pref_providers"))?.toList() ?: emptyList()
        set(value) { setKey(pref("search_pref_providers"), value.toTypedArray()) }
    var searchPreferenceTags: List<TvType>
        get() = tvTypes(getKey<Array<String>>(pref("search_pref_tags")))
        set(value) { setKey(pref("search_pref_tags"), value.map { it.name }.toTypedArray()) }
    var homePreference: List<TvType>
        get() = tvTypes(getKey<Array<String>>(pref("home_pref_homepage")))
        set(value) { setKey(pref("home_pref_homepage"), value.map { it.name }.toTypedArray()) }
    var playBackSpeed: Float
        get() = getKey<Float>(pref("playback_speed")) ?: 1.0f
        set(value) { setKey(pref("playback_speed"), value) }
    var resizeMode: Int
        get() = getKey<Int>(pref("resize_mode")) ?: 0
        set(value) { setKey(pref("resize_mode"), value) }

    private fun tvTypes(names: Array<String>?): List<TvType> =
        names?.mapNotNull { n -> TvType.entries.firstOrNull { it.name == n } } ?: TvType.entries.toList()

    // ---- resume / positions ----
    fun getAllResumeStateIds(): List<Int>? = getKeys("$currentAccount/$RESULT_RESUME_WATCHING")?.mapNotNull {
        it.removePrefix("$currentAccount/$RESULT_RESUME_WATCHING/").toIntOrNull()
    }
    fun deleteAllResumeStateIds() { removeKeys("$currentAccount/$RESULT_RESUME_WATCHING") }
    fun setLastWatched(parentId: Int?, episodeId: Int?, episode: Int?, season: Int?, isFromDownload: Boolean = false, updateTime: Long? = null) {
        if (parentId == null) return
        setKey(
            "$currentAccount/$RESULT_RESUME_WATCHING", parentId.toString(),
            DownloadObjects.ResumeWatching(parentId, episodeId, episode, season, updateTime ?: System.currentTimeMillis(), isFromDownload),
        )
    }
    fun removeLastWatched(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING", parentId.toString())
    }
    fun getLastWatched(id: Int?): DownloadObjects.ResumeWatching? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_RESUME_WATCHING", id.toString())
    }

    fun setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null || dur < 30_000) return
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }
    fun getViewPos(id: Int?): PosDur? = if (id == null) null else getKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)

    fun setVideoWatchState(id: Int?, watchState: VideoWatchState) {
        if (id == null) return
        if (watchState == VideoWatchState.None) removeKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString())
        else setKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), watchState)
    }
    fun getVideoWatchState(id: Int?): VideoWatchState? =
        if (id == null) null else getKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), null)

    fun setResultWatchState(id: Int?, status: Int) {
        if (id == null) return
        if (status == WatchType.NONE.internalId) {
            removeKey("$currentAccount/$RESULT_WATCH_STATE", id.toString())
            removeKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
        } else setKey("$currentAccount/$RESULT_WATCH_STATE", id.toString(), status)
    }
    fun getResultWatchState(id: Int): WatchType =
        WatchType.fromInternalId(getKey<Int>("$currentAccount/$RESULT_WATCH_STATE", id.toString(), null))

    // ---- bookmarks / favourites / subscriptions ----
    fun setBookmarkedData(id: Int?, data: BookmarkedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString(), data)
    }
    fun getBookmarkedData(id: Int?): BookmarkedData? =
        if (id == null) null else getKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    fun getAllBookmarkedData(): List<BookmarkedData> = getKeys("$currentAccount/$RESULT_WATCH_STATE_DATA")?.mapNotNull { getKey<BookmarkedData>(it) } ?: emptyList()
    fun deleteBookmarkedData(id: Int?) {
        if (id == null) return
        removeKey("$currentAccount/$RESULT_WATCH_STATE", id.toString())
        removeKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun setSubscribedData(id: Int?, data: SubscribedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString(), data)
    }
    fun getSubscribedData(id: Int?): SubscribedData? =
        if (id == null) null else getKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    fun removeSubscribedData(id: Int?) {
        if (id == null) return
        removeKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    }
    fun getAllSubscriptions(): List<SubscribedData> = getKeys("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA")?.mapNotNull { getKey<SubscribedData>(it) } ?: emptyList()

    fun setFavoritesData(id: Int?, data: FavoritesData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString(), data)
    }
    fun getFavoritesData(id: Int?): FavoritesData? =
        if (id == null) null else getKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString())
    fun removeFavoritesData(id: Int?) {
        if (id == null) return
        removeKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString())
    }
    fun getAllFavorites(): List<FavoritesData> = getKeys("$currentAccount/$RESULT_FAVORITES_STATE_DATA")?.mapNotNull { getKey<FavoritesData>(it) } ?: emptyList()
}
