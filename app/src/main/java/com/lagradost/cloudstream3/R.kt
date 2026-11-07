@file:Suppress("unused", "ClassName", "ObjectPropertyName")

package com.lagradost.cloudstream3

/**
 * CloudStream resource ids a few extensions read at runtime. They point at Android's own resources
 * so lookups succeed with sensible values instead of NoClassDefFoundError.
 */
object R {
    object attr { @JvmField val white: Int = android.R.attr.colorForeground }
    object color { @JvmField val white: Int = android.R.color.white }
    object id {
        @JvmField val nav_host_fragment: Int = android.R.id.content
        @JvmField val navigation_player: Int = android.R.id.content
    }
    object string { @JvmField val legal_notice_text: Int = android.R.string.unknownName }
    object style { @JvmField val ResultInfoText: Int = android.R.style.TextAppearance_Small }
}
