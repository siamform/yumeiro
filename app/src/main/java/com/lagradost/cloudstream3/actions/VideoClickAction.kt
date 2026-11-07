@file:Suppress("unused", "UNUSED_PARAMETER")

package com.lagradost.cloudstream3.actions

import android.content.Context
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import java.util.concurrent.CopyOnWriteArrayList

/** "Open with" actions extensions can add for a video link (external players, casting, ...). */
abstract class VideoClickAction {
    abstract val name: UiText
    open val oneSource: Boolean = false
    open val isPlayer: Boolean = false
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()
    var sourcePlugin: String? = null
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean
    abstract suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)
}

object VideoClickActionHolder {
    val allVideoClickActions: MutableList<VideoClickAction> = CopyOnWriteArrayList()
}
