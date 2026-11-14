/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.text

/**
 * R15: the pattern tables live in objects, and an object's patterns compile on first touch. The
 * first touch used to be a card being composed (release tags under a poster, audio-language
 * chips, Yumix feed rules) — on the main thread, during pager prefetch. The ANR trace from the
 * Xiaomi showed exactly that (`Pattern.compile` under `LazyLayoutPinnableItem`). Touching them
 * here, on the startup background thread, moves that one-time cost off the UI.
 */
object RegexWarmup {
    @Volatile var done = false
        private set

    fun run() {
        if (done) return
        // Each reference forces the object's static initialiser (its Regex constants) to run now.
        listOf<Any>(
            dev.asfakul.yumeiro.online.ReleaseTags,
            dev.asfakul.yumeiro.online.AudioLanguages,
            dev.asfakul.yumeiro.online.SourceFacts,
            dev.asfakul.yumeiro.online.EpisodeMatcher,
            dev.asfakul.yumeiro.ui.screens.online.YumixFeed,
            dev.asfakul.yumeiro.stream.LinkHosts,
            dev.asfakul.yumeiro.stream.WebPageExtractor,
            dev.asfakul.yumeiro.ui.components.Kana,
        ).forEach { it.hashCode() }
        // A real parse, so lazily built members are ready too.
        runCatching { dev.asfakul.yumeiro.online.ReleaseTags.parse("Show S01E01 1080p WEB-DL HEVC x265 DDP5.1 Dual Audio") }
        runCatching { dev.asfakul.yumeiro.ui.components.Kana.forTitle("Home") }
        done = true
    }
}
