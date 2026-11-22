/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.ExtensionPopups.Action
import dev.asfakul.yumeiro.online.ExtensionPopups.Kind
import dev.asfakul.yumeiro.online.ExtensionPopups.Mode
import dev.asfakul.yumeiro.online.ExtensionPopups.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NBUG10-IME (device run 44899145489, api36): AnimePahe's "Quick security check" window opened by itself
 * during a background load while the user typed in the filter sheet; it took the focus and the keyboard
 * closed 2 s after it opened (server HIDE_UNSPECIFIED_WINDOW). While the user types it must wait behind a card.
 */
class ExtHoldWhileTypingTest {
    private val cloudflare = Snapshot(listOf("🛡️", "Quick security check", "AnimePahe · protected by Cloudflare"), hasInput = false, hasWeb = true)

    @Test fun aSecurityCheckThatOpensWhileTheUserTypesWaitsBehindACard() {
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(cloudflare))
        assertEquals(Action.HOLD_AND_CARD, ExtensionPopups.decide(Kind.FUNCTIONAL, Mode.QUIET, playerOnScreen = false, rateOk = true, userTyping = true))
        assertEquals(Action.HOLD_AND_CARD, ExtensionPopups.decide(Kind.FUNCTIONAL, Mode.BLOCK_PROMOTIONAL, playerOnScreen = true, rateOk = false, userTyping = true))
        assertEquals("AnimePahe needs a quick check", ExtensionPopups.cardText("AnimePahe", functional = true))
    }

    @Test fun otherwiseNothingChanges() {
        for (m in Mode.entries) for (p in listOf(false, true)) for (r in listOf(false, true))
            assertEquals(Action.SHOW_FRAMED, ExtensionPopups.decide(Kind.FUNCTIONAL, m, p, r, userTyping = false))
        assertEquals(Action.SHOW_FRAMED, ExtensionPopups.decide(Kind.FUNCTIONAL, Mode.SHOW, false, true, userTyping = true)) // the user asked to see all
        assertEquals(Action.HOLD_AND_CARD, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.QUIET, false, true, userTyping = true))
        assertEquals("AnimePahe has a message", ExtensionPopups.cardText("AnimePahe", functional = false))
    }
}
