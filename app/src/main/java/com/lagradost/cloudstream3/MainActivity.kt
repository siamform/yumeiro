package com.lagradost.cloudstream3

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Event

/**
 * CloudStream-compatible base activity. The app's real activity extends this, so extensions that
 * cast the current activity (`activity as MainActivity` / `AppCompatActivity` / `FragmentActivity`)
 * get a working FragmentManager for their dialogs and settings screens.
 */
open class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MAINACT"
        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent = Event<Boolean>()
        val afterRepositoryLoadedEvent = Event<Boolean>()
        val bookmarksUpdatedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
        val reloadLibraryEvent = Event<Boolean>()
        val reloadAccountEvent = Event<Boolean>()
        var lastError: String? = null
        var isLocalList: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.setActivityInstance(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val listener = CommonActivity.keyEventListener
        if (listener != null && event.action == KeyEvent.ACTION_DOWN &&
            runCatching { listener(Pair(event, false)) }.getOrDefault(false)
        ) return true
        return super.dispatchKeyEvent(event)
    }
}
