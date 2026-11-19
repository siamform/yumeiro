/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.update.ApkAsset
import dev.asfakul.yumeiro.update.ReleaseInfo
import dev.asfakul.yumeiro.update.UpdateSource
import dev.asfakul.yumeiro.update.UpdateState
import dev.asfakul.yumeiro.update.ui.UpdateDialog
import dev.asfakul.yumeiro.update.ui.UpdateDialogCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * G10 ER-23: the "Update available" card is header + meta + "What's new" + notes (up to 260 dp) +
 * buttons, about 620 dp tall, with no scroll and no room rule. On a 1080p TV (960 × 540 dp) and a
 * phone in landscape (~411 dp) the Download and Remind me later buttons were below the card's clip,
 * so a TV user could not start the update at all. Material3 AlertDialog gives its text slot
 * `weight(1f, fill = false)` so the buttons always fit (androidx-main AlertDialog.kt line 358);
 * Mihon v0.19.1 NewUpdateScreen keeps accept/reject in the InfoScreen bottom bar while notes scroll.
 *
 * G10 ER-24: while "Installing…" the dialog ignored Back and outside taps (dismissAction = {}). If the
 * system installer never reports (Home on the confirm screen, installer killed), the modal covered the
 * whole app until a restart.
 *
 * Old code: the buttons are clipped at 540 and 400 dp; Back in Installing does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class UpdateDialogFitTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val notes = "## Yumeiro v1.9.0\n### New\n" + (1..40).joinToString("\n") { "- Change number $it with a long enough line to wrap on a small card" }
    private val release = ReleaseInfo(
        tagName = "v1.9.0", versionName = "1.9.0", versionCode = 20, title = "Yumeiro v1.9.0", notes = notes,
        publishedAt = "2026-11-19T00:00:00Z", htmlUrl = "",
        asset = ApkAsset(1, "release-Yumeiro-v1.9.0.apk", 25_000_000, "", "", null), source = UpdateSource.PUBLIC,
    )

    private fun buttonsFit(heightDp: Int, widthDp: Int) {
        rule.setContent {
            MTPlayerTheme {
                Box(Modifier.size(widthDp.dp, heightDp.dp), contentAlignment = Alignment.Center) {
                    UpdateDialogCard(UpdateState.Available(release), "1.8.2", false)
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        listOf("update_download", "update_remind_later").forEach { tag ->
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            val b = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val card = rule.onNodeWithTag("update_dialog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag is cut at ${heightDp}dp: button bottom ${b.bottom} > card bottom ${card.bottom}", b.bottom <= card.bottom + 0.5f)
            assertTrue("$tag has no height at ${heightDp}dp (clipped): $b", b.height > 40f)
        }
    }

    @Test fun tv1080pButtonsVisible() = buttonsFit(540, 960)

    @Test fun phoneLandscapeButtonsVisible() = buttonsFit(400, 891)

    @Test fun installingCanBeLeftWithBack() {
        var dismissed = false
        rule.setContent {
            MTPlayerTheme {
                UpdateDialog(
                    state = UpdateState.Installing(release), currentVersion = "1.8.2", needsInstallPermission = false,
                    onDownload = {}, onRemindLater = {}, onCancelDownload = {}, onContinueInBackground = {},
                    onInstall = {}, onRetry = {}, onDismiss = { dismissed = true },
                )
            }
        }
        rule.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
        rule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue("ER-24: Back on \"Installing…\" does nothing; the dialog can't be left", dismissed)
    }
}
