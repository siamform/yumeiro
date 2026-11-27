/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.asfakul.yumeiro.sources.hub.GenreCatalog
import dev.asfakul.yumeiro.sources.hub.GenreMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Group W (B2): a source's genre list made tidy for display ([GenreCatalog.clean], pure and quick) and counted for
 * automatic genres off the main thread.
 */
@Composable
fun rememberCleanGenres(raw: List<String>?, max: Int = 12): List<String> {
    val context = LocalContext.current
    val list = remember(raw, max) { GenreCatalog.clean(raw.orEmpty(), max) }
    LaunchedEffect(raw) { if (!raw.isNullOrEmpty()) withContext(Dispatchers.IO) { GenreMemory.seen(context, raw) } }
    return list
}
