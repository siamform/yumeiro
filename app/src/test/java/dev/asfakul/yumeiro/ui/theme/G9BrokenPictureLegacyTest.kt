/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * G9-9: below Android 9 (Yumeiro TV runs from API 24) the backdrop import decodes with BitmapFactory, which
 * fills the missing part of a cut-off JPEG or PNG with grey and returns a bitmap, so a damaged file was
 * saved as the backdrop (tv-api24 run 44368883691: `corrupt.jpg: accepted`). The phone build starts at
 * API 28, so this drives the same BitmapFactory path with `useDecoder = false` (also the path taken after
 * an ImageDecoder refusal).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class G9BrokenPictureLegacyTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun bytes(fmt: Bitmap.CompressFormat): ByteArray {
        val b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        // Not one flat colour, so the encoder writes real image data the cut goes through.
        for (y in 0 until 600 step 4) for (x in 0 until 800 step 4) b.setPixel(x, y, Color.rgb(x % 256, y % 256, (x + y) % 256))
        return java.io.ByteArrayOutputStream().also { b.compress(fmt, 90, it) }.toByteArray()
    }

    private fun file(name: String, data: ByteArray): Uri = Uri.fromFile(File(ctx.cacheDir, name).apply { writeBytes(data) })

    private fun check(ext: String, fmt: Bitmap.CompressFormat) {
        val full = bytes(fmt)
        assertNotNull("control: the whole $ext decodes", BackdropSettings.decodeScaled(ctx, file("whole.$ext", full), 540, useDecoder = false))
        val cut = runCatching { BackdropSettings.decodeScaled(ctx, file("cut.$ext", full.copyOf(full.size * 6 / 10)), 540, useDecoder = false) }
        val e = cut.exceptionOrNull()
        if (e !is BackdropSettings.BrokenPicture) fail("a $ext cut at 60 % must be refused as damaged; got ${cut.getOrNull()?.let { "a ${it.width}×${it.height} bitmap" } ?: e}")
    }

    @Test fun g9_9_cutOffJpegIsRefusedOnTheBitmapFactoryPath() = check("jpg", Bitmap.CompressFormat.JPEG)
    @Test fun g9_9_cutOffPngIsRefusedOnTheBitmapFactoryPath() = check("png", Bitmap.CompressFormat.PNG)
}
