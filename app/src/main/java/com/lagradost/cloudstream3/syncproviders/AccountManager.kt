@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.KitsuApi
import com.lagradost.cloudstream3.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi

/** Entry point extensions use to reach tracker APIs (`AccountManager.simklApi`, `aniListApi`, ...). */
abstract class AccountManager {
    companion object {
        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"
        const val APP_STRING_SEARCH = "cloudstreamsearch"
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"
        const val APP_STRING_SHARE = "csshare"

        val malApi = MALApi()
        val kitsuApi = KitsuApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()
        val openSubtitlesApi = OpenSubtitlesApi()

        val allApis: Array<AuthRepo> get() = arrayOf(SyncRepo(malApi), SyncRepo(aniListApi), SyncRepo(simklApi), SyncRepo(localListApi))
        val syncApis: Array<SyncRepo> get() = arrayOf(SyncRepo(malApi), SyncRepo(aniListApi), SyncRepo(simklApi), SyncRepo(localListApi))
        val subtitleProviders: Array<AuthRepo> get() = emptyArray()

        fun accounts(prefix: String): Array<AuthData> = emptyArray()
    }
}
