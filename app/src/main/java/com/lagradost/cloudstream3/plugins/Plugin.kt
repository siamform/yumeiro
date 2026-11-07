package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

/**
 * Android entry point that CloudStream extensions (.cs3) extend.
 *
 * Extensions are compiled against CloudStream's `com.lagradost.cloudstream3.plugins.Plugin`; the
 * cross-platform part ([BasePlugin], MainAPI, extractors, networking) comes from the official
 * CloudStream library dependency, and this class provides the small Android-only surface so that
 * existing extensions load unchanged. Keep the package, name and members binary compatible.
 */
abstract class Plugin : BasePlugin() {
    /** Called once when the extension is loaded. Register providers and extractors here. */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /** Resources packed inside the extension (only when its manifest sets requiresResources). */
    var resources: Resources? = null

    /** Optional settings screen of the extension, opened from the extension's Settings action. */
    var openSettings: ((context: Context) -> Unit)? = null

    /** Registers an "open with" action for video links (external players, casting, ...). */
    fun registerVideoClickAction(element: VideoClickAction) {
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }
}
