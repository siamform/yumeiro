/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TG-PAGE-1 (R19). The Telegram page tells three cases apart: a public channel (saved and read from Telegram's
 * public preview), a channel without a public preview (members open it from their own signed-in account) and a
 * private chat / invite link (membership needed; never opened or joined without the user's own account).
 * Fail-before: an invite link got "Type @channel or a t.me link" on the Telegram page and was saved as a website on
 * the Websites page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelegramAccessTest {
    @Test fun publicChannelIsAccepted() {
        assertNull(SavedSites.wrongPage("@durov", SavedSites.Kind.TELEGRAM))
        assertNull(SavedSites.wrongPage("https://t.me/s/durov", SavedSites.Kind.TELEGRAM))
    }

    @Test fun privateChatsAndInviteLinksSayMembershipAndSignInAreNeeded() {
        for (link in listOf("https://t.me/+AbCdEf123", "t.me/joinchat/AbCdEf", "https://t.me/c/123456/7", "tg://join?invite=AbCd")) {
            assertEquals(link, SavedSites.PRIVATE_LINK, SavedSites.wrongPage(link, SavedSites.Kind.TELEGRAM))
            assertEquals(link, SavedSites.PRIVATE_LINK, SavedSites.wrongPage(link, SavedSites.Kind.WEBSITE))
        }
        assertTrue("members only" , "Only members" in SavedSites.PRIVATE_LINK)
        assertTrue("never joins", "never joins" in SavedSites.PRIVATE_LINK)
    }

    @Test fun channelWithoutPublicPreviewPointsToTheAccount() {
        val m = TelegramPublic.Messages.noPosts("somechannel")
        assertTrue(m, "signed-in Telegram account" in m && "member" in m)
    }
}
