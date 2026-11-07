@file:Suppress("unused")

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

object UIHelper {
    /** Resolves a theme colour attribute; falls back to white when the theme lacks it. */
    @ColorInt
    fun Context.colorFromAttribute(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) runCatching { getColor(tv.resourceId) }.getOrDefault(tv.data) else tv.data
        } else android.graphics.Color.WHITE
    }

    val Int.toPx: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    val Int.toDp: Int get() = (this / android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
