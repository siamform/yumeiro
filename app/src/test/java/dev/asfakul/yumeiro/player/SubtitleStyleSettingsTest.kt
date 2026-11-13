/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.media3.ui.CaptionStyleCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleStyleSettingsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun defaultFollowsAndroidCaptionSettings() {
        SubtitleStyleSettings.reset(ctx)
        assertTrue(SubtitleStyleSettings.style.value.followSystem)
    }

    @Test fun customisingSwitchesOffSystemAndPersists() {
        SubtitleStyleSettings.reset(ctx)
        SubtitleStyleSettings.customise(ctx) { it.copy(size = 1.5f, color = SubtitleStyle.TextColor.YELLOW, edge = SubtitleStyle.Edge.BOX, bold = true, lift = 0.1f) }
        val p = ctx.getSharedPreferences("mt_subtitle_style", Context.MODE_PRIVATE)
        assertFalse(p.getBoolean("system", true))
        assertEquals(1.5f, p.getFloat("size", 0f), 0f)
        assertEquals("YELLOW", p.getString("color", null))
        assertEquals("BOX", p.getString("edge", null))
        assertTrue(p.getBoolean("bold", false))
    }

    @Test fun corruptValuesAreClamped() {
        val s = SubtitleStyleSettings.sanitize(SubtitleStyle(size = 99f, lift = Float.NaN))
        assertEquals(2f, s.size, 0f); assertEquals(0f, s.lift, 0f)
        assertEquals(0.75f, SubtitleStyleSettings.sanitize(SubtitleStyle(size = 0f)).size, 0f)
    }

    @Test fun captionStyleMapsEdges() {
        val box = SubtitleStyleSettings.captionStyle(SubtitleStyle(edge = SubtitleStyle.Edge.BOX))
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, box.edgeType)
        assertTrue(box.backgroundColor != 0)
        val outline = SubtitleStyleSettings.captionStyle(SubtitleStyle(edge = SubtitleStyle.Edge.OUTLINE))
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, outline.edgeType)
        assertEquals(0, outline.backgroundColor)
    }
}
