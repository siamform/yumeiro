/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tiny started-activity counter (no extra dependency). Lets the updater install a silently
 * downloaded update only when the user has left the app, the way Messenger / Facebook do.
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    @Volatile private var installed = false

    val isForeground: Boolean get() = started > 0

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started == 1) {
            UpdateManager.get(activity).onAppForegrounded()
            // Catch up on notifications that a battery manager may have held back.
            runCatching { dev.asfakul.yumeiro.notify.BackgroundWork.catchUp(activity.applicationContext) }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        // Configuration changes stop + restart the activity; the manager's grace delay covers it.
        if (started == 0 && !activity.isChangingConfigurations) UpdateManager.get(activity).onAppBackgrounded()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
