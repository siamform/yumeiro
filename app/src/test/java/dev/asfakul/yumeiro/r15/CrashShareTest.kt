/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.screens.settings.CRASH_PREVIEW_CHARS
import dev.asfakul.yumeiro.ui.screens.settings.crashPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** R15 audit: a large crash report is previewed in bounded form and shared as a file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashShareTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun largeReportPreviewIsBoundedSmallOneIsWhole() {
        val dir = File(ctx.filesDir, "crash").apply { mkdirs() }
        val big = File(dir, "big.txt").apply { writeText("x".repeat(6_000_000)) }
        val p = crashPreview(big)
        assertTrue(p.length < CRASH_PREVIEW_CHARS + 100)
        assertTrue(p.endsWith("Share sends the whole file)"))
        val small = File(dir, "small.txt").apply { writeText("Kind: Crash\nError: boom\n") }
        assertEquals("Kind: Crash\nError: boom\n", crashPreview(small))
    }

    @Test fun reportFolderIsShareableThroughTheFileProvider() {
        val f = File(File(ctx.filesDir, "crash").apply { mkdirs() }, "r.txt").apply { writeText("hi") }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        assertEquals("content", uri.scheme)
        assertEquals(ctx.packageName + ".files", uri.authority)
        assertEquals("hi", ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText())
    }
}
