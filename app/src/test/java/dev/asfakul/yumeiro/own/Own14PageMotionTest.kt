/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import dev.asfakul.yumeiro.ui.components.PageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** OWN-14: a page closes the way it opened (same length and curve, reversed). */
class Own14PageMotionTest {
    private fun src(path: String) = File("src/main/java/dev/asfakul/yumeiro/$path").readText()

    @Test fun backIsThePushReversed() {
        assertTrue(PageMotion.isSymmetric())
        assertEquals(PageMotion.PUSH_MS, PageMotion.POP_MS)
        val motion = src("ui/components/PageSwap.kt")
        val popExit = motion.substringAfter("fun popExit(").substringBefore("\n\n")
        assertFalse("the closing page must not use another curve than the opening one", "EmphasizedAccelerate" in popExit)
    }

    @Test fun overlayPagesAndSettingsCategoriesUseTheMirroredMotion() {
        val app = src("ui/screens/MTPlayerApp.kt")
        assertTrue(app.contains("PageMotion.pageRiseIn(reducedMotionOn, NAV_ANIM_MS)") && app.contains("PageMotion.pageRiseOut(reducedMotionOn, NAV_ANIM_MS)"))
        assertFalse("Inbox / Support used to blink out", app.contains("exit = fadeOut(tween(160)),"))
        val settings = src("ui/screens/settings/SettingsScreen.kt")
        assertTrue(settings.contains("PageMotion.pushOrPop("))
    }
}
