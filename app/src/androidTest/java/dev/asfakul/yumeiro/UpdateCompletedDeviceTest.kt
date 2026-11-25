/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.update.PackageReplacedReceiver
import dev.asfakul.yumeiro.update.UpdatePreferences
import dev.asfakul.yumeiro.update.UpdateStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UPD-MSG-1 (owner item 21): after an install that Yumeiro started, the new version says "Update completed!".
 * The install itself is Android's; this checks the app's half on a real emulator: the version written at install
 * time + Android's MY_PACKAGE_REPLACED broadcast (handed to the manifest receiver) → a real system notification
 * with the title and body, posted once. Log tag YuUpdateTest.
 */
@RunWith(AndroidJUnit4::class)
class UpdateCompletedDeviceTest {
    @Test fun replacedAppSaysUpdateCompletedOnce() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
        val prefs = UpdatePreferences(ctx)
        prefs.pendingUpdatedVersion = "9.9.9"
        PackageReplacedReceiver().onReceive(ctx, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        Thread.sleep(1500)
        val n = nm.activeNotifications.firstOrNull { it.notification.extras.getString("android.title") == UpdateStatusText.COMPLETED_TITLE }
        assertNotNull("no \"Update completed!\" notification", n)
        val body = n!!.notification.extras.getCharSequence("android.text")?.toString()
        Log.w("YuUpdateTest", "UPD_RESULT title=${UpdateStatusText.COMPLETED_TITLE} body=$body")
        assertEquals(UpdateStatusText.completedBody("9.9.9"), body)
        assertNull(prefs.pendingUpdatedVersion)
        // A plain reinstall (no install started by Yumeiro) says nothing.
        nm.cancelAll()
        PackageReplacedReceiver().onReceive(ctx, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        Thread.sleep(800)
        assertNull(nm.activeNotifications.firstOrNull { it.notification.extras.getString("android.title") == UpdateStatusText.COMPLETED_TITLE })
    }
}
