/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CI-DNS-RACE: unit run 44894173529 failed Nbug6FallbackTest.concurrentRequestsNeverWalkTheChainInParallel
 * (no chain walk at all): a background NetworkSettings.init read the stored (default) settings while the test set
 * fallback = true, then wrote the stored ones over it. In the app the same race drops a DNS choice saved while the
 * settings load at start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DnsSettingsRaceTest {
    @After fun tearDown() { NetworkSettings.afterPrefsReadForTests = null; NetworkSettings.resetForTests() }

    @Test fun aSettingSavedWhileTheStoredOnesLoadIsKept() {
        val app = RuntimeEnvironment.getApplication()
        NetworkSettings.resetForTests()
        val chosen = DnsConfig(fallback = true, auto = false)
        NetworkSettings.afterPrefsReadForTests = { NetworkSettings.setForTests(chosen) }
        NetworkSettings.init(app)
        assertEquals(chosen, NetworkSettings.dns.value)
    }

    @Test fun theUserChoiceViaSetDnsIsKeptToo() {
        val app = RuntimeEnvironment.getApplication()
        NetworkSettings.resetForTests()
        val chosen = DnsConfig(provider = DnsProvider.entries.last(), mode = DnsMode.entries.last())
        NetworkSettings.afterPrefsReadForTests = { NetworkSettings.setDns(app, chosen) }
        NetworkSettings.init(app)
        assertEquals(chosen, NetworkSettings.dns.value)
    }

    @Test fun withNoWriteTheStoredSettingsLoad() {
        val app = RuntimeEnvironment.getApplication()
        NetworkSettings.resetForTests()
        NetworkSettings.init(app)
        assertEquals(DnsConfig(), NetworkSettings.dns.value)
    }
}
