/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.downloads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ST-19: where downloads go and how much room is left, read off the main thread.
 * `OnlineDownloads.dir` calls `getExternalFilesDir` + `mkdirs` and `freeBytes` runs `StatFs`; they
 * were called during composition (Settings › Downloads on every recomposition, the download prompt,
 * the Downloads screen): StrictMode DiskReadViolation ×6 in 43767144680 (api36, api33-lowram).
 */
@Composable
internal fun rememberStoragePath(context: Context, folder: String): State<String?> =
    produceState<String?>(null, folder) { value = withContext(Dispatchers.IO) { OnlineDownloads.storagePath(context, folder) } }

/** Free bytes where downloads are saved; -1 while unknown (callers already hide it then). */
@Composable
internal fun rememberFreeBytes(context: Context, key: Any?): State<Long> =
    produceState(-1L, key) { value = withContext(Dispatchers.IO) { OnlineDownloads.freeBytes(context) } }
