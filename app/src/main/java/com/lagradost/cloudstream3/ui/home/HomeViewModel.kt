@file:Suppress("unused")

package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import dev.asfakul.yumeiro.online.OnlineHistory

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_HOMEVIEWMODEL_1 = Regex("""(?i)\bE(?:p(?:isode)?)?\s*(\d+)""")
private val RX_HOMEVIEWMODEL_2 = Regex("""(?i)\bS(?:eason)?\s*(\d+)""")

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
                    episode = e.episodeLabel?.let { RX_HOMEVIEWMODEL_1.find(it)?.groupValues?.get(1)?.toIntOrNull() },
                    season = e.episodeLabel?.let { RX_HOMEVIEWMODEL_2.find(it)?.groupValues?.get(1)?.toIntOrNull() },
                    isFromDownload = false,
                    posterHeaders = e.posterHeaders.ifEmpty { null },
                )
            }
        }.getOrNull()
    }
}
