/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Restarts Yumeiro cleanly. The old `startActivity(); exit(0)` raced: the process often died
 * before the new launch was queued and the app simply closed. Here a tiny activity in its own
 * `:restart` process waits for the old process to die, then opens the app again.
 */
object Restart {
    const val EXTRA_PID = "pid"

    fun now(context: Context) {
        val app = context.applicationContext
        val i = Intent(app, RestartActivity::class.java).putExtra(EXTRA_PID, Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { app.startActivity(i) }.onFailure {
            // Last resort: plain relaunch.
            launchIntent(app)?.let { l ->
                runCatching { app.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
            }
        }
        (context as? Activity)?.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /** Phones have a launcher entry; TVs may only have the Leanback one. */
    fun launchIntent(c: Context): Intent? = c.packageManager.getLaunchIntentForPackage(c.packageName)
        ?: c.packageManager.getLeanbackLaunchIntentForPackage(c.packageName)

    /** True in the helper process (Application.onCreate skips all set-up there). */
    fun isRestartProcess(context: Context): Boolean = processName(context).endsWith(":restart")

    private fun processName(context: Context): String {
        if (android.os.Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName() ?: ""
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            ?: runCatching { java.io.File("/proc/$pid/cmdline").readText().trim('\u0000', ' ') }.getOrDefault("")
    }
}

class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(Restart.EXTRA_PID, -1)
        if (pid > 0 && pid != Process.myPid()) runCatching { Process.killProcess(pid) }
        // Give the old process a moment to go so the new one starts fresh.
        window.decorView.postDelayed({
            Restart.launchIntent(this)?.let { l ->
                startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            finish()
            Runtime.getRuntime().exit(0)
        }, 350)
    }
}
