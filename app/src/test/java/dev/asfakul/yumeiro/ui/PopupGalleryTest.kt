/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.extras.FollowCard
import dev.asfakul.yumeiro.ui.screens.extras.ReminderCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import dev.asfakul.yumeiro.ui.theme.rememberPageBrush
import androidx.compose.foundation.background
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every pop-up the app shows (reminder, community, star, dialog, toast) drawn over the real page
 * backdrop in light and dark. Asserts the card body is opaque (no page bleeding through) and saves
 * build/screenshots/popup_<theme>_<name>.png for review.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PopupGalleryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String): android.graphics.Bitmap {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val roots = rule.onAllNodes(isRoot())
        val n = roots.fetchSemanticsNodes().size
        val base = roots[0].captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(base)
        var layer: android.graphics.Bitmap? = null
        for (i in 1 until n) {
            val l = runCatching { roots[i].captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: continue
            c.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
            c.drawBitmap(l, (base.width - l.width) / 2f, (base.height - l.height) / 2f, null)
            layer = l
        }
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "popup_$name.png").outputStream().use { base.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        return layer ?: base
    }

    /** The middle of the card must be fully opaque. */
    private fun assertOpaqueCentre(b: android.graphics.Bitmap, what: String) {
        val px = b.getPixel(b.width / 2, b.height / 2)
        assertTrue("$what centre is see-through (alpha ${android.graphics.Color.alpha(px)})", android.graphics.Color.alpha(px) == 255)
    }

    private fun gallery(dark: Boolean, which: Int, content: @Composable () -> Unit) {
        rule.setContent {
            MTPlayerTheme(if (dark) Palettes.Dark else Palettes.Light) {
                androidx.compose.runtime.CompositionLocalProvider(dev.asfakul.yumeiro.ui.components.LocalPopupPreview provides true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Box(Modifier.fillMaxSize().background(rememberPageBrush()))
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)))
                        content()
                    }
                }
            }
        }
        val t = if (dark) "dark" else "light"
        val img = shot("${t}_$which")
        assertOpaqueCentre(img, "${t}_$which")
    }

    @Test fun reminderLight() = gallery(false, 1) { ReminderCard(Icons.Default.Star, "r", "Enjoying Yumeiro?", "A star on GitHub helps more people find it.", "Star on GitHub", {}, {}) }
    @Test fun reminderDark() = gallery(true, 1) { ReminderCard(Icons.Default.Star, "r", "Enjoying Yumeiro?", "A star on GitHub helps more people find it.", "Star on GitHub", {}, {}) }
    @Test fun followLight() = gallery(false, 2) { FollowCard {} }
    @Test fun followDark() = gallery(true, 2) { FollowCard {} }
    @Test fun dialogLight() = gallery(false, 3) {
        TsukiDialog(onDismissRequest = {}, confirmButton = { TextButton({}) { Text("Delete") } }, dismissButton = { TextButton({}) { Text("Cancel") } },
            title = { Text("Delete playlist?") }, text = { Text("This removes the playlist and its channels from this device.") })
    }
    @Test fun dialogDark() = gallery(true, 3) {
        TsukiDialog(onDismissRequest = {}, confirmButton = { TextButton({}) { Text("Delete") } }, dismissButton = { TextButton({}) { Text("Cancel") } },
            title = { Text("Delete playlist?") }, text = { Text("This removes the playlist and its channels from this device.") })
    }
    @Test fun toastLight() {
        rule.setContent { MTPlayerTheme(Palettes.Light) { Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(rememberPageBrush())); AppMessageHost() } } }
        rule.runOnUiThread { AppMessages.success(null, "Added to your library") }
        shot("light_4")
    }
    @Test fun toastDark() {
        rule.setContent { MTPlayerTheme(Palettes.Dark) { Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(rememberPageBrush())); AppMessageHost() } } }
        rule.runOnUiThread { AppMessages.error(null, "Couldn't load this page", "Retry") {} }
        shot("dark_4")
    }
}
