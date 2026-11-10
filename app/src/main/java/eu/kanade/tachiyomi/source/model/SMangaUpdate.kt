/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package eu.kanade.tachiyomi.source.model

/**
 * Result of [eu.kanade.tachiyomi.source.Source.getMangaUpdate]: the manga details and its chapters.
 *
 * @since extensions-lib 1.6
 */
class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
