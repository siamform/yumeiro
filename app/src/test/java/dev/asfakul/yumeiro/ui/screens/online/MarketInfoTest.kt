/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.asfakul.yumeiro.online.RemoteExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-MARKET-1 (R19): the market has a way to an extension's full details and its repository. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MarketInfoTest {
    @get:Rule val rule = createComposeRule()

    @Test fun repositoryPageFromRepoAddress() {
        assertEquals("https://github.com/phisher98/cloudstream-extensions-phisher",
            repoPage("https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json"))
        assertEquals("https://github.com/a/b", repoPage("https://cdn.jsdelivr.net/gh/a/b@builds/plugins.json"))
        assertEquals("https://github.com/a/b", repoPage("https://github.com/a/b/raw/main/repo.json"))
        assertNull(repoPage("https://example.com/repo.json"))
        assertNull(repoPage("http://raw.githubusercontent.com/a/b/x.json"))
        assertNull(repoPage(null))
    }

    @Test fun sheetShowsFullDetails() {
        val ext = RemoteExtension(
            internalName = "HindiSubAnime", name = "HindiSubAnime", url = "https://x/y.cs3", version = 73,
            description = "A long description that the card cuts to two lines. ".repeat(4).trim(),
            authors = listOf("phisher98"), tvTypes = listOf("Anime"), language = "hi",
            repoUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json", repoName = "Phisher",
        )
        rule.setContent { dev.asfakul.yumeiro.ui.theme.MTPlayerTheme { MarketInfoSheet(ext, installedVersion = null, hasUpdate = false, onInstall = {}, onManage = {}, onDismiss = {}) } }
        rule.waitForIdle()
        rule.onNodeWithTag("market_info_HindiSubAnime").assertExists()
        rule.onNodeWithText("phisher98").assertExists()
        rule.onNodeWithText("Anime").assertExists()
        rule.onNodeWithTag("market_info_repo").assertExists()
        rule.onNodeWithTag("market_info_install").assertExists()
    }
}
