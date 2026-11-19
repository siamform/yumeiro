/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.ExtensionPopups
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-29 (found in the G10 group regression: MessagesDeviceTest failed on both phones in
 * 44452173594 and 44454531660, each time within a second of "held a promotional window … (card shown)").
 * When an extension's promotional window is held, its "<extension> has a message · Show" card is posted
 * with AppMessages.show and replaces whatever message is on screen — an error with Retry included —
 * although the user did nothing and the card can wait. Material's SnackbarHostState queues messages
 * instead of replacing them (material3 androidx-main SnackbarHost.kt, showSnackbar mutex).
 * Old code: the card replaces the error at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromoCardWaitsTest {
    @Before fun host() { AppMessages.hosts = 1; AppMessages.current.value?.let { AppMessages.dismiss(it.id) } }
    @After fun noHost() { AppMessages.current.value?.let { AppMessages.dismiss(it.id) }; AppMessages.hosts = 0 }

    @Test fun aPromoCardDoesNotReplaceTheMessageOnScreen() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppMessages.error(ctx, "Couldn't load the feed", "Retry") {}
        val error = AppMessages.current.value!!
        ExtensionPopups.hold("ext.k", "Promo Ext", ExtensionPopups.Snapshot(listOf("Support the repo"), false, false), View(ctx), deferredCard = false)
        assertEquals("ER-29: the promotional card replaced the message on screen", error.id, AppMessages.current.value?.id)
        // The card is not lost: it shows once the user's message is gone.
        AppMessages.dismiss(error.id)
        val next = AppMessages.current.value
        assertTrue("the waiting card should show after the message closes: $next", next != null && "Promo Ext" in next.text)
    }
}
