package dev.asfakul.yumeiro.startup

import android.app.Application
import android.content.SharedPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Records which preference files were opened through the application context. */
class RecordingApp : Application() {
    val opened = mutableListOf<String?>()
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        opened += name
        return super.getSharedPreferences(name, mode)
    }
}

/**
 * G12 X2-3: PrefsWarmup loads files through the application context; an Activity has its own
 * name-to-file map, so its first lookup hit the disk on main (StrictMode, device run 43763558754).
 * The base activity must hand lookups to the application context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingApp::class)
class ActivityPrefsRouteTest {
    @Test
    fun activityPrefsGoThroughTheApplicationContext() {
        val activity = Robolectric.buildActivity(com.lagradost.cloudstream3.MainActivity::class.java).get()
        val app = activity.application as RecordingApp
        activity.getSharedPreferences("mt_subtitle_style", 0)
        assertTrue("activity lookup did not reach the application context: ${app.opened}", "mt_subtitle_style" in app.opened)
    }
}
