/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * FINAL-8: the app's start-up init and a sign-in link that cold-starts the app ran OnlineSettings.init
 * at the same time. The second caller returned early (prefs already set) and its token write was
 * dropped because the secrets file was not open yet. The first init is held right after prefs is set,
 * where the race was; the second caller's token must still be saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineSettingsInitRaceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @After fun tearDown() {
        OnlineSettings.initPauseForTests = null
        OnlineSettings.resetForTests()
    }

    @Test fun aTokenWrittenWhileStartUpInitRunsIsKept() {
        OnlineSettings.resetForTests()
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        OnlineSettings.initPauseForTests = { inside.countDown(); release.await(5, TimeUnit.SECONDS) }
        val startUp = Thread { OnlineSettings.init(ctx) }.apply { start() }
        check(inside.await(5, TimeUnit.SECONDS)) { "start-up init never reached the pause" }
        OnlineSettings.initPauseForTests = null
        val signIn = Thread { OnlineSettings.setSecret(ctx, "anilist_token", "new-token-abcdef0123456789") }.apply { start() }
        signIn.join(300) // before the fix it returned at once and dropped the token
        release.countDown()
        startUp.join(5_000); signIn.join(5_000)
        assertEquals("new-token-abcdef0123456789", OnlineSettings.secret("anilist_token"))
    }
}
