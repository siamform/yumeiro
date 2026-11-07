@file:Suppress("unused")

package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import dev.asfakul.yumeiro.online.OnlineHistory

class HomeViewModel : ViewModel() {
    companion object {
        /**
         * "Continue watching" for extensions (Ultima, StreamPlay home rows, ...).
         * Unlike CloudStream's per-provider store, this returns what you are actually watching in
         * Yumeiro, across every extension, most recent first.
         */
        suspend fun getResumeWatching(): List<DataStoreHelper.ResumeWatchingResult>? = runCatching {
            OnlineHistory.entries.value.filter { it.positionMs > 0 }.map { e ->
                val parentId = e.url.hashCode()
                DataStoreHelper.ResumeWatchingResult(
                    name = e.name,
                    url = e.url,
                    apiName = e.api,
                    type = e.type?.let { t -> TvType.entries.firstOrNull { it.name.equals(t, true) } },
                    posterUrl = e.posterUrl,
                    watchPos = DataStoreHelper.PosDur(e.positionMs, e.durationMs),
                    id = (e.episodeData ?: e.url).hashCode(),
                    parentId = parentId,
                    episode = e.episodeLabel?.let { Regex("""(?i)\bE(?:p(?:isode)?)?\s*(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
                    season = e.episodeLabel?.let { Regex("""(?i)\bS(?:eason)?\s*(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
                    isFromDownload = false,
                    posterHeaders = e.posterHeaders.ifEmpty { null },
                )
            }
        }.getOrNull()
    }
}
