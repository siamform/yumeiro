/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.r14

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.player.QualityEngine
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.extras.ReliabilityGuideDialog
import dev.asfakul.yumeiro.ui.screens.video.QualityDialog
import dev.asfakul.yumeiro.ui.screens.video.QualityOption
import dev.asfakul.yumeiro.ui.screens.watch.WatchDetailsSkeleton
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild
import java.io.File

/** v1.8.1 mock images from the real composables. Output: app/build/screenshots/r14. Report texts are samples. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class R14MockShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun out(name: String) = File(File(System.getProperty("user.dir"), "build/screenshots/r14").apply { mkdirs() }, "$name.png")

    private fun set(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground)) { content() } } }
        rule.mainClock.advanceTimeBy(1_500)
    }

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(800)
        out(name).outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun reliabilityGuideXiaomi() {
        ShadowBuild.setManufacturer("Xiaomi"); ShadowBuild.setBrand("Redmi")
        NotifySettings.init(rule.activity); NotifySettings.set(rule.activity, NotifySettings.Key.BACKGROUND, true)
        set { Box(Modifier.padding(top = 40.dp)) { ReliabilityGuideDialog(onClose = {}, inline = true) } }
        save("r14_01_reliability_guide")
    }

    @Test fun reliabilityGuideSamsung() {
        ShadowBuild.setManufacturer("samsung"); ShadowBuild.setBrand("samsung")
        NotifySettings.init(rule.activity); NotifySettings.set(rule.activity, NotifySettings.Key.BACKGROUND, true)
        set { Box(Modifier.padding(top = 40.dp)) { ReliabilityGuideDialog(onClose = {}, inline = true) } }
        save("r14_02_reliability_guide_samsung")
    }

    @Test fun crashReports() {
        val d = File(rule.activity.filesDir, "crash")
        val now = System.currentTimeMillis()
        CrashLog.write(d, "Crash", "main", IllegalStateException("Sample: player released twice"), at = now - 3_600_000)
        CrashLog.write(d, "Freeze (ANR)", "system exit record", null, body = "Sample: input dispatching timed out", at = now - 86_400_000)
        CrashLog.write(d, "Handled", "extension Sample", RuntimeException("Sample: extension threw while loading links"), at = now - 2 * 86_400_000)
        set {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SubPageHeader(title = "Crash reports", onBack = {}, subtitle = "Saved on this phone", handleBack = false)
                Column(Modifier.padding(horizontal = 16.dp)) { dev.asfakul.yumeiro.ui.screens.settings.CrashReportsCard() }
            }
        }
        save("r14_03_crash_reports")
    }

    @Test fun qualityEngineLevels() {
        val alts = QualityEngine.engine(720, null)
        val opts = alts.map { a -> QualityOption(a.label, -1, -1, a.height, 0, alt = a, note = if (a.current) "Playing now" else a.note) }
        set {
            QualityDialog(opts, null, null, onSelect = {}, onDismiss = {},
                footer = "This source sends one quality. Yumeiro renders the other levels from it on your GPU.")
        }
        save("r14_04_quality_engine")
    }

    @Test fun neutralLoader() { set { WatchDetailsSkeleton(WatchKind.NEUTRAL) }; save("r14_05_neutral_loader") }

    @Test fun backdropAccepted() {
        dev.asfakul.yumeiro.ui.theme.BackdropSettings.init(rule.activity)
        set { Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) { dev.asfakul.yumeiro.ui.screens.settings.BackdropSettingsSection() } }
        save("r14_06_backdrop")
    }
}
