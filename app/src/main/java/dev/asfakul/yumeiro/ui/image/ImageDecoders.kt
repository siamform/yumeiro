package dev.asfakul.yumeiro.ui.image

import coil.decode.Decoder
import coil.decode.SvgDecoder

/**
 * NB14-OB1: extra decoders for the app's single Coil ImageLoader. Live TV playlists (M3U tvg-logo) often point at
 * SVG logos, which Coil cannot decode without its SVG module; they showed as blank tiles. SvgDecoder only claims
 * image/svg+xml (or .svg) responses, so other images keep the default decoders and cost nothing extra.
 */
object ImageDecoders {
    fun factories(): List<Decoder.Factory> = listOf(SvgDecoder.Factory())
}
