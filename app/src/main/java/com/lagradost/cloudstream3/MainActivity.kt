package com.lagradost.cloudstream3

import android.content.SharedPreferences
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
        // Must be set before super.onCreate restores saved fragments: extension dialogs live in the
        // extension's class loader, not the app's (G12 X8-3, crash in device run 43762090588).
        supportFragmentManager.fragmentFactory = dev.asfakul.yumeiro.online.ExtensionFragmentFactory
        super.onCreate(savedInstanceState)
    }

    /**
     * G12 X2-3: every Context keeps its own name-to-file map for preferences, so the first
     * `getSharedPreferences(name)` on this Activity checked the disk on the main thread even when
     * [dev.asfakul.yumeiro.startup.PrefsWarmup] had already loaded the file through the application
     * context (StrictMode DiskReadViolation in `SubtitleStyleSettings.init`, device run 43763558754).
     * Code in composition gets this Activity from `LocalContext`, so hand every lookup to the
     * application context, which shares the warmed cache. Same files, same instances.
     */
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        val app = if (baseContext != null) applicationContext else null
        val prefs = if (app != null && app !== this) app.getSharedPreferences(name, mode)
        else super.getSharedPreferences(name, mode)
        // ST-20: plugins get this Activity as their Context; a file extension code opens gets a
        // main-thread-safe commit() (ExtensionPrefs). Yumeiro's own files are returned as they are.
        return dev.asfakul.yumeiro.online.ExtensionPrefs.wrapIfExtension(name, prefs, MainActivity::class.java.classLoader!!)
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
