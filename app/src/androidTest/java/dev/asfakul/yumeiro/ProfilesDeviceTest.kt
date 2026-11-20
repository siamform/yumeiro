/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.DownloadGroups
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen
import dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession
import dev.asfakul.yumeiro.ui.screens.profiles.WhoIsWatching
import dev.asfakul.yumeiro.ui.screens.settings.AppearanceSettingsCard
import dev.asfakul.yumeiro.ui.theme.AccentTone
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PR-1 / DL-H / TH-1 on phones and TV images, driven the way a user does (taps; remote number keys on TV).
 * Who's watching -> locked profile -> PIN pad: a wrong PIN shows an error and clears the pad, twice in a
 * row; the right PIN opens the profile. Downloads: a hidden item is not in the list, Private asks for
 * the PIN and then shows it. Accent: tapping a colour changes the theme. Log tag YuProfiles.
 * Old code: none of these tags exist.
 */
@RunWith(AndroidJUnit4::class)
class ProfilesDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext.applicationContext
    private val tv get() = Edition.isTv
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 10_000) = runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun log(s: String) = Log.i("YuProfiles", s)
    private var added: String? = null
    private var itemsBefore: List<Item> = emptyList()
    private var accentBefore = AccentTone.GOLD

    @Before fun setUp() {
        SystemClock.sleep(2000)
        Profiles.init(ctx)
        ThemeSettings.init(ctx); accentBefore = ThemeSettings.accent.value
        OnlineDownloads.ensureLoaded(ctx); itemsBefore = OnlineDownloads.items.value
    }

    @After fun tearDown() {
        Profiles.setPin(ctx, Profiles.DEFAULT, null)
        added?.let { Profiles.remove(ctx, it) }
        ThemeSettings.setAccent(ctx, accentBefore)
        OnlineDownloads.setItemsForTest(ctx, itemsBefore)
        ProfileSession.chosen.value = true
    }

    /** Types [pin] with the on-screen keys (phone) or the remote's number keys (TV), then OK. */
    private fun type(pin: String) {
        if (tv) {
            pin.forEach { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_0 + (it - '0')) }
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_ENTER)
        } else {
            pin.forEach { compose.onNodeWithTag("pin_key_$it", true).performClick() }
            compose.onNodeWithTag("pin_key_ok", true).performClick()
        }
        compose.waitForIdle()
    }

    @Test fun lockedProfileNeedsItsPinAndWrongTriesClearThePad() {
        added = Profiles.add(ctx, "Kid", AccentTone.SAKURA).id
        assertTrue(Profiles.setPin(ctx, Profiles.DEFAULT, "2468"))
        ProfileSession.chosen.value = false
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { WhoIsWatching(androidx.activity.compose.LocalActivity.current!!, onManage = null) } } }
        assertTrue("gate shown", waitTag("profile_gate"))
        compose.waitForIdle(); FirstRun.shot("profiles_who_is_watching")
        assertTrue("both profiles", has("profile_pick_${Profiles.DEFAULT}") && has("profile_pick_$added"))
        compose.onNodeWithTag("profile_pick_${Profiles.DEFAULT}", true).performClick()
        assertTrue("pin pad", waitTag("pin_pad"))
        FirstRun.shot("profiles_pin_pad")
        repeat(2) { n ->
            type("1111")
            compose.onNodeWithTag("pin_error", true).assertTextContains("Wrong PIN")
            compose.onNodeWithTag("pin_dots", true).assertTextContainsDescription("0 digits")
            assertFalse("still locked after wrong try ${n + 1}", ProfileSession.chosen.value)
        }
        type("2468")
        compose.waitUntil(5_000) { ProfileSession.chosen.value }
        log("PROFILES_RESULT gate=ok tv=$tv")
    }

    @Test fun hiddenDownloadOnlyInPrivateAfterThePin() {
        assertTrue(Profiles.setPin(ctx, Profiles.DEFAULT, "2468"))
        val now = System.currentTimeMillis()
        fun item(id: String, title: String) = Item(
            id, title, "PrApi", "u-$id", title, null, emptyMap(), "", false, emptyMap(), "pr-$id.mp4", status = Status.DONE,
            bytes = 4096, total = 4096, kind = OnlineDownloads.Kind.LINK, finishedAt = now - 60_000, quality = "720p",
        )
        val shown = item("pv1", "Open Movie PR"); val secret = item("pv2", "Secret Movie PR")
        listOf(shown, secret).forEach { OnlineDownloads.fileOf(ctx, it).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(4096)) }
        OnlineDownloads.setItemsForTest(ctx, listOf(shown, secret))
        assertTrue("hide", dev.asfakul.yumeiro.online.download.PrivateDownloads.hide(ctx, secret.id))
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { DownloadsScreen(onBack = {}, onPlayVideo = {}, onBrowse = {}) } } }
        // Finished downloads sit in folders, closed by default (run 44509508921 waited for the title on
        // the closed list: test error). Open "Other downloads", then look for both titles.
        assertTrue("list", waitTag("downloads_list"))
        compose.onNodeWithTag("downloads_list", true).performScrollToNode(hasText(DownloadGroups.LOOSE))
        compose.onAllNodesWithText(DownloadGroups.LOOSE, true)[0].performClick()
        compose.waitForIdle()
        assertTrue("one item in the folder", compose.onAllNodesWithText("1 item", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("downloads_list", true).performScrollToNode(hasText("Open Movie PR"))
        assertTrue("hidden item not in the list", compose.onAllNodesWithText("Secret Movie PR", true).fetchSemanticsNodes().isEmpty())
        assertTrue("Private entry", waitTag("downloads_private"))
        compose.onNodeWithTag("downloads_private", true).performClick()
        // A phone with a screen lock asks with the system prompt first; the emulators have none.
        assertTrue("pin pad", waitTag("pin_pad"))
        type("2468")
        assertTrue("vault", waitTag("downloads_vault"))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Secret Movie PR", true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle(); FirstRun.shot("private_downloads_vault")
        log("PROFILES_RESULT vault=ok tv=$tv")
    }

    @Test fun accentTapChangesTheTheme() {
        ThemeSettings.setAccent(ctx, AccentTone.GOLD)
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AppearanceSettingsCard() } } } }
        assertTrue("swatch", waitTag("accent_ao"))
        compose.onNodeWithTag("accent_ao", true).performScrollTo().performClick()
        compose.waitUntil(5_000) { ThemeSettings.accent.value == AccentTone.AO }
        assertEquals(AccentTone.AO, ThemeSettings.accent.value)
        compose.waitForIdle(); FirstRun.shot("accent_ao")
        log("PROFILES_RESULT accent=ok tv=$tv")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextContainsDescription(part: String) =
    assert(SemanticsMatcher("description has $part") { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains(part) }
    })

