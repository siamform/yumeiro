@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders

/** Tracker repository. Without a signed-in account every call returns an empty / null result. */
class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName get() = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) { api.requireLibraryRefresh = value }
    val mainUrl get() = api.mainUrl

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching {
        authData() ?: return@runCatching null
        api.library(authData())
    }

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching { null }
    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = runCatching { null }
    suspend fun search(query: String): Result<List<SyncAPI.SyncSearchResult>?> = runCatching { null }
}
