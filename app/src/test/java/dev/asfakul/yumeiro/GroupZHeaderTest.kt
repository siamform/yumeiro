package dev.asfakul.yumeiro

import dev.asfakul.yumeiro.ui.components.SubPageHeaderLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupZHeaderTest {
    @Test fun iconsWrapOnlyWhenTheTitleWouldBeTooNarrow() {
        // 411 dp phone at 1x: back 48, seven 48 dp icons = 336 → title 27 dp → wrap.
        assertTrue(SubPageHeaderLayout.wraps(411, 48, 336, 136))
        // Two icons leave 267 dp → stay on one line.
        assertFalse(SubPageHeaderLayout.wraps(411, 48, 96, 136))
        // No icons never wraps.
        assertFalse(SubPageHeaderLayout.wraps(100, 48, 0, 136))
    }
}
