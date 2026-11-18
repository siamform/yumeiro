/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

/**
 * How a manga page is decoded (G7-MR1). Webtoon pages are tall strips: decoded as one bitmap,
 * a 1440 × 18500 page is 107 MB, over Android's 100 MB draw limit (the app crashes), and far over
 * the GPU's texture size. The page is therefore decoded as horizontal strips of at most
 * [STRIP_HEIGHT] decoded rows, each drawn on its own, and the whole page is kept under a memory
 * budget by sampling (powers of two, as BitmapFactory does). Ordinary pages stay one bitmap, as
 * before. Pure arithmetic, so it is unit tested.
 */
object MangaPagePlan {
    /** Widest decoded page, as before (wider sources are halved until they fit). */
    const val MAX_WIDTH = 1600
    /** Tallest decoded strip; well under every GPU's texture limit (2048 is the safe floor). */
    const val STRIP_HEIGHT = 2048
    /** One page's decoded bytes: below the 100 MB draw limit on normal phones, half on low-RAM ones. */
    const val PAGE_BUDGET = 96L * 1024 * 1024
    const val PAGE_BUDGET_LOW_RAM = 48L * 1024 * 1024

    /** A source band [top, bottom) of the image, in source pixels. */
    data class Band(val top: Int, val bottom: Int)
    data class Plan(val sample: Int, val outWidth: Int, val outHeight: Int, val bands: List<Band>) {
        val bytes: Long get() = outWidth.toLong() * outHeight * 4
    }

    private fun out(px: Int, sample: Int) = (px + sample - 1) / sample

    fun plan(srcWidth: Int, srcHeight: Int, budgetBytes: Long): Plan {
        require(srcWidth > 0 && srcHeight > 0) { "This page isn't an image" }
        var sample = 1
        while (srcWidth / sample > MAX_WIDTH) sample *= 2
        while (out(srcWidth, sample).toLong() * out(srcHeight, sample) * 4 > budgetBytes && sample < 256) sample *= 2
        val outH = out(srcHeight, sample)
        val bands = if (outH <= STRIP_HEIGHT) listOf(Band(0, srcHeight)) else {
            val step = STRIP_HEIGHT * sample
            (0 until srcHeight step step).map { Band(it, minOf(srcHeight, it + step)) }
        }
        return Plan(sample, out(srcWidth, sample), outH, bands)
    }
}
