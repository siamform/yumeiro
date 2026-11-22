package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FE-S4 (SpeedDeviceTest 44884969625: search_all_answered_live 25.1 s on both phones): a site's search may take 4x its
 * own measured time (8..25 s), a quarantined site 8 s; unknown sites and Retry keep the full 25 s.
 */
class SearchTimeoutTest {
    private val now = 1_000_000_000L
    private fun t(s: ExtensionSpeed.Stat?, retry: Boolean = false) = ExtensionSpeed.searchTimeoutMs(s, retry, now)

    @Test fun timeoutFollowsTheSitesOwnSpeed() {
        assertEquals(25_000L, t(null))
        assertEquals(8_000L, t(ExtensionSpeed.Stat(900, 0, 0)))          // fast site: 4x = 3.6 s -> floor 8 s
        assertEquals(12_000L, t(ExtensionSpeed.Stat(3_000, 0, 0)))       // 4x its average
        assertEquals(25_000L, t(ExtensionSpeed.Stat(9_000, 0, 0)))       // slow site keeps the full time
        assertEquals(8_000L, t(ExtensionSpeed.Stat(2_000, 3, now - 60_000))) // quarantined
        assertEquals(25_000L, t(ExtensionSpeed.Stat(900, 0, 0), retry = true)) // Retry waits the full time
        assertEquals(25_000L, t(ExtensionSpeed.Stat(0, 1, now)))         // never answered yet: unknown speed
    }
}
