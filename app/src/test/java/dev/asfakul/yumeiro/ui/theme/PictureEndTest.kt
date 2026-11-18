/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** G9-9: the end-of-file check used before BitmapFactory, on hand-built byte streams (no false refusals). */
class PictureEndTest {
    private fun ok(vararg b: Int) = BackdropSettings.PictureEnd.complete(ByteArray(b.size) { b[it].toByte() }.inputStream())
    private val sos = intArrayOf(0xFF, 0xDA, 0x00, 0x04, 0x01, 0x02)
    private val scan = intArrayOf(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56) // stuffed FF 00 and a restart marker
    // APP1 (EXIF) holding a whole thumbnail that ends in FF D9: must not count as the end of the photo.
    private val app1 = intArrayOf(0xFF, 0xE1, 0x00, 0x08, 0xFF, 0xD8, 0x00, 0x00, 0xFF, 0xD9)

    @Test fun wholeJpegIsComplete() = assertTrue(ok(0xFF, 0xD8, *app1, *sos, *scan, 0xFF, 0xD9))
    @Test fun dataAfterTheEndIsFine() = assertTrue(ok(0xFF, 0xD8, *sos, *scan, 0xFF, 0xD9, 0x00, 0x11, 0x22))
    @Test fun cutInsideTheScanIsIncomplete() = assertFalse(ok(0xFF, 0xD8, *app1, *sos, *scan))
    @Test fun thumbnailEndDoesNotCount() = assertFalse(ok(0xFF, 0xD8, *app1, 0xFF, 0xDB, 0x00, 0x03, 0x00))
    @Test fun cutInsideAHeaderIsIncomplete() = assertFalse(ok(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A))
    @Test fun progressiveSecondScanStillEnds() = assertTrue(ok(0xFF, 0xD8, *sos, *scan, 0xFF, 0xC4, 0x00, 0x03, 0x00, *sos, *scan, 0xFF, 0xD9))

    private val pngSig = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private fun chunk(type: String, len: Int) = intArrayOf(0, 0, 0, len, *type.map { it.code }.toIntArray(), *IntArray(len), 0, 0, 0, 0)
    @Test fun wholePngIsComplete() = assertTrue(ok(*pngSig, *chunk("IHDR", 13), *chunk("IDAT", 5), *chunk("IEND", 0)))
    @Test fun pngWithoutIendIsIncomplete() = assertFalse(ok(*pngSig, *chunk("IHDR", 13), *chunk("IDAT", 5).copyOf(9)))
    @Test fun otherFormatsAreNotChecked() = assertTrue(ok(0x52, 0x49, 0x46, 0x46, 0x00))
}
