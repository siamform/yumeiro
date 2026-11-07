package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/** Layout flags (CloudStream-compatible): phone, TV or emulator. */
object Globals {
    var beneneCount = 0
    const val PHONE: Int = 0b00001
    const val TV: Int = 0b00010
    const val EMULATOR: Int = 0b00100
    private var layoutId = PHONE

    fun Context.updateTv() {
        val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        layoutId = if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature("android.software.leanback")) TV else PHONE
    }

    fun isLandscape(): Boolean = isLayout(TV or EMULATOR) ||
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun isLayout(flags: Int): Boolean = (layoutId and flags) != 0
}
