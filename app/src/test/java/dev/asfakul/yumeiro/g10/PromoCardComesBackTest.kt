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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FINAL-13 (final test, api36 P1, run 44617327290): on a fresh install the extension's quiet card
 * ("<extension> has a message · Show") was on screen when the app posted "Ready to watch: … installed".
 * That message replaced the card, and the card never came back, so the user lost its Show action.
 * Old code: after the app's message closes, nothing shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromoCardComesBackTest {
    @Before fun host() { AppMessages.hosts = 1; repeat(3) { AppMessages.current.value?.let { AppMessages.dismiss(it.id) } } }
    @After fun noHost() { repeat(3) { AppMessages.current.value?.let { AppMessages.dismiss(it.id) } }; AppMessages.hosts = 0 }

    @Test fun aQuietCardPushedOffByAnAppMessageShowsAgain() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ExtensionPopups.hold("ext.f13", "Promo Ext", ExtensionPopups.Snapshot(listOf("Support the repo"), false, false), View(ctx), deferredCard = false)
        val card = AppMessages.current.value
        assertTrue("the quiet card did not show: $card", card != null && "Promo Ext" in card.text && card.actionLabel != null)
        AppMessages.success(ctx, "Ready to watch: A, B installed")
        val app = AppMessages.current.value!!
        assertEquals("the app's own message shows at once", "Ready to watch: A, B installed", app.text)
        AppMessages.dismiss(app.id)
        val back = AppMessages.current.value
        assertTrue("FINAL-13: the extension's card did not come back: $back", back != null && "Promo Ext" in back.text && back.actionLabel == card!!.actionLabel)
        // Its action still works, and it does not come back twice.
        AppMessages.dismiss(back!!.id)
        assertNull(AppMessages.current.value)
    }

    @Test fun plainMessagesStillReplaceEachOther() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppMessages.info(ctx, "One")
        AppMessages.info(ctx, "Two")
        val two = AppMessages.current.value!!
        assertEquals("Two", two.text)
        AppMessages.dismiss(two.id)
        assertNull("a plain message must not come back", AppMessages.current.value)
    }
}
