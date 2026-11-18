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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * G9-9: below Android 9 the backdrop import decodes with BitmapFactory, which fills the missing part of a
 * cut-off JPEG or PNG with grey and returns a bitmap, so a damaged file was saved as the backdrop
 * (tv-api24 run 44368883691: `corrupt.jpg: accepted`). API 27 = the BitmapFactory path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
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

    @Test fun g9_9_cutOffJpegIsRefusedBelowApi28() = runBlocking {
        BackdropSettings.init(ctx)
        val full = bytes(Bitmap.CompressFormat.JPEG)
        assertNull("control: the whole JPEG imports", BackdropSettings.importPhoto(ctx, file("whole.jpg", full)))
        val err = BackdropSettings.importPhoto(ctx, file("cut.jpg", full.copyOf(full.size * 6 / 10)))
        assertTrue("a JPEG cut at 60 % must be refused as damaged, got: $err", err != null && "damaged or incomplete" in err)
    }

    @Test fun g9_9_cutOffPngIsRefusedBelowApi28() = runBlocking {
        BackdropSettings.init(ctx)
        val full = bytes(Bitmap.CompressFormat.PNG)
        assertNull("control: the whole PNG imports", BackdropSettings.importPhoto(ctx, file("whole.png", full)))
        val err = BackdropSettings.importPhoto(ctx, file("cut.png", full.copyOf(full.size * 6 / 10)))
        assertTrue("a PNG cut at 60 % must be refused, got: $err", err != null && ("damaged or incomplete" in err || "couldn't be opened" in err || "isn't supported" in err))
    }
}
