@file:Suppress("unused", "UNUSED_PARAMETER")

package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode

enum class SkipType(val res: Int) {
    Opening(0), Ending(0), Recap(0), MixedOpening(0), MixedEnding(0), Credits(0), Intro(0), Preview(0),
}

data class SkipStamp(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
    val label: String? = null,
)

/** Intro / credits skip source. Extensions can provide their own. */
abstract class SkipAPI {
    open val name: String = "NONE"
    abstract val supportedTypes: Set<TvType>
    open suspend fun stamps(data: LoadResponse, episode: ResultEpisode, episodeDurationMs: Long): List<SkipStamp>? = throw NotImplementedError()
}
