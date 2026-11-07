/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.update.ApkAsset
import dev.asfakul.yumeiro.update.ReleaseInfo
import dev.asfakul.yumeiro.update.UpdateSource
import dev.asfakul.yumeiro.update.UpdateState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class UpdateDialogScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val release = ReleaseInfo(
        tagName = "v1.1.0", versionName = "1.1.0", versionCode = 2, title = "Yumeiro v1.1.0",
        notes = "## Yumeiro v1.1.0\n### New features\n- In-app updater with background downloads (a1b2c3d) — siamform\n- Release notes popup (d4e5f6a) — siamform\n### Bug fixes\n- Fix equalizer reset on track change (9f8e7d6) — zihadexplor\n### Build & CI\n- Official Gradle 9.8.2 wrapper (1a2b3c4) — siamform",
        publishedAt = "2026-11-03T07:00:00Z", htmlUrl = "",
        asset = ApkAsset(1, "release-Yumeiro-v1.1.0.apk", 5_726_136, "", "", null),
        source = UpdateSource.PUBLIC,
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground).padding(vertical = 24.dp), contentAlignment = Alignment.Center) { content() } } }
        rule.mainClock.advanceTimeBy(900)
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable private fun dialog(state: UpdateState, perm: Boolean = false) = UpdateDialogCard(state, "1.0.0", perm)

    @Test fun available() = shot("1_available") { dialog(UpdateState.Available(release)) }
    @Test fun downloading() = shot("2_downloading") { dialog(UpdateState.Downloading(release, 3_400_000, 5_726_136, 1_250_000)) }
    @Test fun ready() = shot("3_ready") { dialog(UpdateState.ReadyToInstall(release, ""), perm = true) }
    @Test fun failed() = shot("4_failed") { dialog(UpdateState.Failed("Download failed (connection lost).", release)) }
}
