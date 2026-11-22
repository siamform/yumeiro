/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.telegram.td.TgKeys
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OWN-3 (owner 2026-11-22): Telegram sign-in with the user's own API ID / API hash, like Hikari. */
class Own3TelegramKeysTest {
    @Test fun goodKeysPass() {
        assertNull(TgKeys.problem("1234567", "0123456789abcdef0123456789ABCDEF"))
        assertNull(TgKeys.problem(" 94575 ", " a3406de8d171bb422bb6ddf3bbd800e2 "))
    }

    @Test fun badKeysSayWhatToFix() {
        assertNotNull(TgKeys.problem("", "0123456789abcdef0123456789abcdef"))
        assertNotNull(TgKeys.problem("12a4", "0123456789abcdef0123456789abcdef"))
        assertNotNull(TgKeys.problem("0", "0123456789abcdef0123456789abcdef"))
        assertNotNull(TgKeys.problem("99999999999", "0123456789abcdef0123456789abcdef"))
        assertNotNull(TgKeys.problem("1234567", "short"))
        assertNotNull(TgKeys.problem("1234567", "0123456789abcdef0123456789abcdeg"))
    }

    @Test fun signInIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.TELEGRAM_LOGIN.default)
    }
}
