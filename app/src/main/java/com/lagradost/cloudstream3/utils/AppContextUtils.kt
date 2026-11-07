package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.settings.Globals

/** Navigation helpers extensions call (CloudStream-compatible); the host app installs [opener]. */
object AppContextUtils {
    /** Opens a title page in the host app: (url, apiName, name). */
    @Volatile var opener: ((String, String, String) -> Unit)? = null

    fun loadResult(url: String, apiName: String, name: String, startAction: Int = 0, startValue: Int = 0) {
        (CommonActivity.activity as? FragmentActivity)?.loadResult(url, apiName, name, startAction, startValue)
            ?: opener?.invoke(url, apiName, name)
    }

    fun FragmentActivity.loadResult(url: String, apiName: String, name: String, startAction: Int = 0, startValue: Int = 0) {
        runOnUiThread { opener?.invoke(url, apiName, name) }
    }

    fun loadSearchResult(card: SearchResponse, startAction: Int = 0, startValue: Int? = null) {
        CommonActivity.activity.loadSearchResult(card, startAction, startValue)
    }

    fun Activity?.loadSearchResult(card: SearchResponse, startAction: Int = 0, startValue: Int? = null) {
        val run = Runnable { opener?.invoke(card.url, card.apiName, card.name) }
        if (this != null) runOnUiThread(run) else run.run()
    }

    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        if (!Globals.isLayout(Globals.TV or Globals.EMULATOR)) return
        runCatching { getButton(buttonFocus)?.run { isFocusableInTouchMode = true; requestFocus() } }
    }
}
