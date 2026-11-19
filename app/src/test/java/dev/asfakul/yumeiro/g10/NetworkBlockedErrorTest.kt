/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import dev.asfakul.yumeiro.network.Connectivity
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.ui.components.ErrorHelp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-21: when Android or a firewall app blocks the app's network (per-app "Wi-Fi / mobile data"
 * switch on MIUI/ColorOS, NetGuard, a work profile without network), the socket call fails with
 * EPERM/EACCES ("socket failed: EPERM (Operation not permitted)", "android_getaddrinfo failed:
 * EACCES (Permission denied)"). The shared mapper matched those errno words to its storage rule and
 * told the user "The app couldn't use its storage on this device". Chromium maps the same errnos on a
 * socket to ERR_ACCESS_DENIED (network access denied), not to a file error.
 * Old code: every case below gets the storage sentence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkBlockedErrorTest {

    @After fun online() = Connectivity.overrideForTests(true)

    private val blocked = listOf(
        "socket EPERM" to java.net.SocketException("socket failed: EPERM (Operation not permitted)"),
        "connect EACCES" to java.io.IOException("call failed", java.net.ConnectException("failed to connect to /1.1.1.1 (port 443): connect failed: EACCES (Permission denied)")),
        "dns EACCES" to java.net.UnknownHostException("Unable to resolve host \"example.org\": No address associated with hostname").also {
            it.initCause(java.io.IOException("android_getaddrinfo failed: EACCES (Permission denied)"))
        },
    )

    @Test fun aBlockedNetworkIsNotCalledAStorageProblem() {
        Connectivity.overrideForTests(true)
        blocked.forEach { (name, e) ->
            val text = OnlineErrors.friendly(e)
            assertFalse("$name: a blocked network is reported as storage: \"$text\"", "storage" in text.lowercase())
            assertTrue("$name: the text should say the app's internet is blocked: \"$text\"", "blocking yumeiro's internet" in text.lowercase())
            val tips = ErrorHelp.suggestionsFor(text).joinToString(" | ")
            assertTrue("$name: the tips should point to the app's Wi-Fi / mobile data setting: $tips", "mobile data" in tips)
            assertFalse("$name: storage tips for a network block: $tips", "storage space" in tips)
        }
    }

    @Test fun realStorageErrorsKeepTheStorageSentence() {
        val text = OnlineErrors.friendly(java.io.FileNotFoundException("/data/user/0/dev.asfakul.yumeiro/files/x.tmp: open failed: EACCES (Permission denied)"))
        assertEquals("The app couldn't use its storage on this device. Restart the app, then try again", text)
    }
}
