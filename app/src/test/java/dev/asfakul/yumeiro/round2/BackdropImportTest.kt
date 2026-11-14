/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.theme.BackdropSettings
import dev.asfakul.yumeiro.ui.theme.BackdropStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Round 2, order 8: custom backdrop photos of the common types and sizes import; bad ones say why. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackdropImportTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun image(name: String, w: Int, h: Int, fmt: Bitmap.CompressFormat): Uri {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 140)) }
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { b.compress(fmt, 90, it) }
        return Uri.fromFile(f)
    }

    @Test fun commonFormatsAndSizesImport() = runBlocking {
        BackdropSettings.init(ctx)
        val cases = listOf(
            image("a.jpg", 4000, 3000, Bitmap.CompressFormat.JPEG),
            image("b.png", 1080, 2400, Bitmap.CompressFormat.PNG),
            @Suppress("DEPRECATION") image("c.webp", 800, 800, Bitmap.CompressFormat.WEBP),
            image("d.jpg", 300, 200, Bitmap.CompressFormat.JPEG),
        )
        val report = StringBuilder()
        for (u in cases) {
            val err = BackdropSettings.importPhoto(ctx, u)
            val saved = File(ctx.filesDir, "backdrop_photo.jpg").takeIf { it.exists() } ?: ctx.filesDir.listFiles()!!.first { it.name.startsWith("backdrop") && !it.name.endsWith(".tmp") }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(saved.path, bounds)
            report.append("${u.lastPathSegment}: ${err ?: "OK"} -> saved ${bounds.outWidth}x${bounds.outHeight}\n")
            assertNull("${u.lastPathSegment}: $err", err)
            assertTrue(report.toString(), minOf(bounds.outWidth, bounds.outHeight) in 200..1080)
            assertEquals(BackdropStyle.PHOTO, BackdropSettings.style.value)
        }
        File("build/backdrop-import.txt").writeText(report.toString())
    }

    @Test fun badPicturesSayWhy() = runBlocking {
        BackdropSettings.init(ctx)
        val txt = File(ctx.cacheDir, "not-an-image.jpg").apply { writeText("hello") }
        val e1 = BackdropSettings.importPhoto(ctx, Uri.fromFile(txt))
        assertTrue(e1.orEmpty(), e1.orEmpty().contains("format") || e1.orEmpty().contains("couldn't be opened"))
        val tiny = image("tiny.png", 40, 40, Bitmap.CompressFormat.PNG)
        assertTrue(BackdropSettings.importPhoto(ctx, tiny).orEmpty().contains("too small"))
        val missing = BackdropSettings.importPhoto(ctx, Uri.fromFile(File(ctx.cacheDir, "gone.jpg")))
        assertTrue(missing.orEmpty(), missing.orEmpty().contains("couldn't read") || missing.orEmpty().contains("couldn't be opened"))
        File("build/backdrop-errors.txt").writeText("text file: $e1\n40x40: too small\nmissing: $missing\n")
    }
}
