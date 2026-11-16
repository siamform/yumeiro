/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ST-20: an extension's main-thread SharedPreferences.commit() runs as apply(); ours are untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtensionPrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val appLoader = ExtensionPrefsTest::class.java.classLoader!!

    @After fun reset() = ExtensionPrefs.resetForTest()

    /** Records which of commit/apply the wrapper really called. */
    private class Recording(val real: SharedPreferences) : SharedPreferences by real {
        val calls = mutableListOf<String>()
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by real.edit() {
            private val e = real.edit()
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { e.putLong(key, value); return this }
            override fun commit(): Boolean { calls += "commit"; return e.commit() }
            override fun apply() { calls += "apply"; e.apply() }
        }
    }

    @Test fun mainThreadCommitBecomesApplyAndTheValueIsReadAtOnce() {
        val rec = Recording(ctx.getSharedPreferences("ext_test_a", Context.MODE_PRIVATE))
        val p = MainSafePrefs(rec) { true }
        assertTrue(p.edit().putLong("shown", 42L).commit())
        assertEquals(listOf("apply"), rec.calls)
        assertEquals(42L, p.getLong("shown", 0L))
        assertEquals(1, ExtensionPrefs.converted)
    }

    @Test fun backgroundCommitStaysACommit() {
        val rec = Recording(ctx.getSharedPreferences("ext_test_b", Context.MODE_PRIVATE))
        val p = MainSafePrefs(rec) { false }
        assertTrue(p.edit().putLong("shown", 7L).commit())
        assertEquals(listOf("commit"), rec.calls)
        assertEquals(0, ExtensionPrefs.converted)
    }

    @Test fun chainedBuilderCallsKeepTheWrapper() {
        val rec = Recording(ctx.getSharedPreferences("ext_test_c", Context.MODE_PRIVATE))
        val p = MainSafePrefs(rec) { true }
        val ed = p.edit()
        assertSame(ed, ed.putString("a", "b").putInt("i", 1).putBoolean("z", true).remove("x"))
    }

    @Test fun ownerIsTheFirstCallerOutsideThePlatform() {
        val frames = listOf(
            "dalvik.system.VMStack", "java.lang.Thread", "dev.asfakul.yumeiro.online.ExtensionPrefs",
            "com.lagradost.cloudstream3.MainActivity", "android.content.ContextWrapper",
            "com.phisher98.donation.DonationManager", "dev.asfakul.yumeiro.online.ExtensionGuard",
        )
        assertEquals("com.phisher98.donation.DonationManager", ExtensionPrefs.firstCaller(frames))
        assertNull(ExtensionPrefs.firstCaller(listOf("java.lang.Thread", "androidx.preference.PreferenceManager")))
        // The Application and Activity routing frames are skipped (43825449454: the lookup ends in MTApplication).
        assertEquals("com.phisher98.donation.DonationManager", ExtensionPrefs.firstCaller(listOf(
            "dalvik.system.VMStack", "dev.asfakul.yumeiro.online.ExtensionPrefs", "dev.asfakul.yumeiro.MTApplication",
            "android.content.ContextWrapper", "com.lagradost.cloudstream3.MainActivity", "com.phisher98.donation.DonationManager")))
    }

    @Test fun onlyClassesTheAppCannotLoadAreExtensions() {
        assertTrue(ExtensionPrefs.loadable("dev.asfakul.yumeiro.online.ExtensionGuard", appLoader))
        assertTrue(ExtensionPrefs.loadable("dev.asfakul.yumeiro.online.ExtensionGuard$\$ExternalSyntheticLambda0", appLoader))
        assertFalse(ExtensionPrefs.loadable("com.phisher98.donation.DonationManager", appLoader))
    }

    @Test fun ourOwnFilesAreReturnedUnwrapped() {
        val prefs = ctx.getSharedPreferences("yu_nav", Context.MODE_PRIVATE)
        // Called from this test class (loadable by the app loader): Yumeiro's own file.
        assertSame(prefs, ExtensionPrefs.wrapIfExtension("yu_nav", prefs, appLoader))
        assertSame(prefs, ExtensionPrefs.wrapIfExtension(null, prefs, appLoader))
    }
}
