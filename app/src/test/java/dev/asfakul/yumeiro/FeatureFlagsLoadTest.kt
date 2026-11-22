/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FF-LOAD: a flag read without a context ([FeatureFlags.isOn], 48 calls, all five GATE-1 fixes among them)
 * follows the saved feature_flags file, not the built-in default, even when nothing called [FeatureFlags.on]
 * before it. Without this, switching a flag off (support, adb, the GATE-1 A/B walk) did nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureFlagsLoadTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val flag = FeatureFlags.Flag.POSTER_PLAIN_IMAGE

    @After fun restore() {
        ctx.getSharedPreferences("feature_flags", Context.MODE_PRIVATE).edit().clear().commit()
        FeatureFlags.set(ctx, flag, flag.default)
    }

    @Test fun aFlagSavedOffIsOffWithoutAContext() {
        assumeTrue(flag.default)
        FeatureFlags.attach(ctx)
        ctx.getSharedPreferences("feature_flags", Context.MODE_PRIVATE).edit().putBoolean(flag.key, false).commit()
        FeatureFlags.set(ctx, flag, false) // drops any loaded map, like a support flip at run time
        assertFalse("isOn must read the saved file, not the default", FeatureFlags.isOn(flag))
    }

    @Test fun isOnFollowsEachSet() {
        FeatureFlags.attach(ctx)
        FeatureFlags.set(ctx, flag, false)
        assertFalse(FeatureFlags.isOn(flag))
        FeatureFlags.set(ctx, flag, true)
        assertTrue(FeatureFlags.isOn(flag))
    }

    @Test fun warmLoadsTheSavedFile() {
        FeatureFlags.attach(ctx)
        FeatureFlags.set(ctx, flag, false)
        FeatureFlags.warm(ctx)
        assertFalse(FeatureFlags.isOn(flag))
    }

    @Test fun theAppAttachesAtStart() {
        assumeTrue("needs the app's own Application", ctx is MTApplication)
        FeatureFlags.set(ctx, flag, false)
        assertFalse("MTApplication.onCreate must call FeatureFlags.attach", FeatureFlags.isOn(flag))
    }

    @Test fun firstFrameWarmupIncludesTheFlagFile() {
        assertTrue("feature_flags" in dev.asfakul.yumeiro.startup.PrefsWarmup.FIRST_FRAME)
    }
}
