/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * AUD-UI-1 (final code audit): a clickable drawn smaller than 48 dp reserves 48 dp with
 * `minimumInteractiveComponentSize()`. AccessibilityDeviceTest (ATF) only sees the screens it opens;
 * this guard covers the rest of the sources (Now Playing error close, search fill, Home setup hide, …).
 */
class TouchTargetTest {
    @Test fun smallClickablesReserveFortyEightDp() {
        val size = Regex("""\.size\((\d+)\.dp\)""")
        val click = Regex("""\.(clickable|combinedClickable|toggleable|selectable)\s*[({]""")
        val hits = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.indices.mapNotNull { i ->
                val l = lines[i]
                val next = lines.getOrElse(i + 1) { "" }
                val small = size.findAll(l).firstOrNull { it.groupValues[1].toInt() < 48 } ?: return@mapNotNull null
                val after = l.substring(small.range.last + 1) + next
                if (click.containsMatchIn(after) && !(l + next).contains("minimumInteractiveComponentSize")) "${f.path}:${i + 1}" else null
            }
        }.toList()
        assertEquals("clickables under 48 dp without minimumInteractiveComponentSize(): $hits", emptyList<String>(), hits)
    }
}
