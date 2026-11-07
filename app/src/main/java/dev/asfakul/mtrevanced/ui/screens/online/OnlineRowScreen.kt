/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.mtrevanced.online.ExtensionManager
import dev.asfakul.mtrevanced.online.OnlineErrors
import dev.asfakul.mtrevanced.online.OnlineHistory
import dev.asfakul.mtrevanced.online.OnlineItem
import dev.asfakul.mtrevanced.online.OnlineRepository
import dev.asfakul.mtrevanced.ui.components.StateMessage
import dev.asfakul.mtrevanced.ui.components.SubPageHeader
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary

/** "See all" for one home row: an endless poster grid that keeps loading pages as you scroll. */
@Composable
fun OnlineRowScreen(api: String, title: String, pageName: String, pageData: String, onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit, onOpenExtensions: () -> Unit) {
    val context = LocalContext.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val items = remember(api, pageData) { mutableStateListOf<OnlineItem>() }
    var page by remember(api, pageData) { mutableIntStateOf(0) }
    var hasNext by remember(api, pageData) { mutableStateOf(true) }
    var loading by remember(api, pageData) { mutableStateOf(false) }
    var error by remember(api, pageData) { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()

    // Start with what the home row already showed, so the page is never blank.
    LaunchedEffect(api, pageData) {
        if (items.isEmpty()) OnlineRepository.cachedRow(api, pageData)?.let { items.addAll(it); page = 1 }
    }
    LaunchedEffect(api, pageData, request, engine.ready) {
        if (!engine.ready || loading || !hasNext) return@LaunchedEffect
        val provider = ExtensionManager.provider(api)
        if (provider == null) { missing = true; return@LaunchedEffect }
        loading = true; error = null
        runCatching { OnlineRepository.page(provider, pageName, pageData, page + 1) }
            .onSuccess { r ->
                val known = items.map { it.url }.toHashSet()
                val fresh = r.items.filter { known.add(it.url) }
                items.addAll(fresh)
                page++
                // Stop when the source has no next page or keeps sending the same titles.
                hasNext = r.hasNext && fresh.isNotEmpty()
            }
            .onFailure { error = OnlineErrors.friendly(it, "Couldn't load more") }
        loading = false
    }
    val nearEnd by remember { derivedStateOf { val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= grid.layoutInfo.totalItemsCount - 9 } }
    LaunchedEffect(nearEnd, loading, hasNext, error) { if (nearEnd && !loading && hasNext && error == null && page > 0) request++ }

    Column(Modifier.fillMaxSize().testTag("online_row_screen")) {
        SubPageHeader(title = title, subtitle = api, onBack = onBack)
        when {
            missing -> StateMessage(Icons.Default.Extension, "Source not available", "The extension that provides “$api” isn't installed or is turned off.",
                primary = "Manage extensions" to onOpenExtensions, secondary = "Go back" to onBack, testTag = "online_row_missing")
            items.isEmpty() && loading -> PosterGridSkeleton(rows = 4)
            items.isEmpty() && error != null -> StateMessage(Icons.Default.ErrorOutline, "Couldn't load", error.orEmpty(), tint = ErrorColor,
                primary = "Retry" to { error = null; request++ }, secondary = "Go back" to onBack, testTag = "online_row_error")
            items.isEmpty() && !hasNext -> StateMessage(Icons.Default.ErrorOutline, "Nothing here", "This list is empty right now.", tint = TextSecondary, testTag = "online_row_empty")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()), state = grid,
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().testTag("online_row_grid"),
            ) {
                items(items, key = { it.url }) { item ->
                    PosterCard(item, onClick = { OnlineRepository.previews[item.key] = item; onOpenTitle(item) }, modifier = Modifier.fillMaxWidth())
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> PosterRowSkeleton(count = gridColumns())
                            error != null -> TextButton(onClick = { error = null; request++ }) { Text("Couldn't load more · Retry", color = SpotifyGreen) }
                            !hasNext -> Text("That's everything", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** "See all" for Continue watching. */
@Composable
fun OnlineHistoryScreen(onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OnlineHistory.ensureLoaded(context) }
    val all by OnlineHistory.entries.collectAsState()
    val adultOn by dev.asfakul.mtrevanced.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.mtrevanced.online.OnlineSettings.adultPrivate.collectAsState()
    // 18+ titles are kept quietly: hidden here unless the user asks to see them.
    var showAdult by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val adultCount = androidx.compose.runtime.remember(all) { all.count { OnlineHistory.isAdult(it) } }
    val entries = androidx.compose.runtime.remember(all, adultOn, adultPrivate, showAdult) {
        if (showAdult) all.filter { OnlineHistory.isAdult(it) } else OnlineHistory.forFeed(all, adultOn, adultPrivate)
    }
    var confirmClear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(if (showAdult) "Clear 18+ history?" else "Clear watch history?") },
        text = { Text(if (showAdult) "Removes every 18+ title and its resume position from this device." else "Removes every title and resume position from this device. This can't be undone.") },
        confirmButton = { TextButton(onClick = { if (showAdult) OnlineHistory.clearAdult(context) else OnlineHistory.clear(context); confirmClear = false; showAdult = false }, modifier = Modifier.testTag("online_history_clear_confirm")) { Text("Clear", color = ErrorColor) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    Column(Modifier.fillMaxSize().testTag("online_history_screen")) {
        SubPageHeader(title = if (showAdult) "18+ history" else "Continue watching", subtitle = "${entries.size} title${if (entries.size == 1) "" else "s"}", onBack = { if (showAdult) showAdult = false else onBack() }) {
            if (adultCount > 0 && adultOn) TextButton(onClick = { showAdult = !showAdult }, modifier = Modifier.testTag("online_history_adult")) {
                Text(if (showAdult) "All" else "18+ ($adultCount)", color = TextSecondary, fontSize = 12.sp)
            }
            if (entries.isNotEmpty()) IconButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("online_history_clear")) {
                Icon(Icons.Default.DeleteSweep, "Clear history", tint = TextPrimary)
            }
        }
        if (entries.isEmpty()) StateMessage(Icons.Default.History, "Nothing yet", "Titles you watch online show up here so you can pick up where you left off.", tint = TextSecondary, testTag = "online_history_empty")
        else LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns()), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = { it.key }) { e ->
                Column {
                    PosterCard(e.toItem(), onClick = { onOpenTitle(e.toItem()) }, modifier = Modifier.fillMaxWidth(), progress = e.fraction, showSource = true)
                    e.episodeLabel?.let { Text(it, color = TextTertiary, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
                    TextButton(onClick = { OnlineHistory.remove(context, e.api, e.url) }, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(0.dp)) { Text("Remove", color = TextSecondary, fontSize = 11.sp) }
                }
            }
        }
    }
}
