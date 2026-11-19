/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * G10 ER-28 (NB15-OB2), every sheet: a non-null Material drag handle is a focusable button on TV
 * (first remote key lands on it, "Drag handle" tooltip, OK dismisses the sheet). The device test
 * `SheetFocusTvDeviceTest` proves it on the Live TV Tools sheet with real key events; this scan proves
 * every `ModalBottomSheet(` call in the app goes through the one TV-aware handle,
 * `sheetDragHandle()` (null on TV, the moon handle elsewhere), so no sheet keeps the default or a
 * fixed handle. Old code: 10 calls, 8 pass `MoonSheetHandle()` directly and 2 use Material's default.
 */
class SheetHandleScanTest {
    @Test fun everyBottomSheetUsesTheTvAwareHandle() {
        val root = File(System.getProperty("user.dir"), "src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        val calls = mutableListOf<String>()
        val bad = mutableListOf<String>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            Regex("""(?<![\w.])(?:androidx\.compose\.material3\.)?ModalBottomSheet\(""").findAll(src).forEach { m ->
                // The call's own arguments: up to the matching ')' of ModalBottomSheet(.
                var depth = 0; var i = m.range.last; val start = i
                while (i < src.length) { val c = src[i]; if (c == '(') depth++ else if (c == ')') { depth--; if (depth == 0) break }; i++ }
                val args = src.substring(start, (i + 1).coerceAtMost(src.length))
                val where = "${f.name}:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
                calls += where
                if (!Regex("""dragHandle\s*=\s*(?:[\w.]+\.)?sheetDragHandle\(\)""").containsMatchIn(args)) bad += where
            }
        }
        assertTrue("no ModalBottomSheet calls found under $root", calls.size >= 10)
        assertTrue("ER-28: sheets without the TV-aware handle (sheetDragHandle()): $bad", bad.isEmpty())
    }
}
