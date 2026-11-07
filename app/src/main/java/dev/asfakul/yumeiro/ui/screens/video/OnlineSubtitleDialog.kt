/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.player.PlayerSettings
import dev.asfakul.yumeiro.subtitles.LocalSubtitle
import dev.asfakul.yumeiro.subtitles.MovieHash
import dev.asfakul.yumeiro.subtitles.MovieHasher
import dev.asfakul.yumeiro.subtitles.OnlineSubtitle
import dev.asfakul.yumeiro.subtitles.OpenSubtitlesProvider
import dev.asfakul.yumeiro.subtitles.SUBTITLE_LANGUAGES
import dev.asfakul.yumeiro.subtitles.cleanQuery
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Search & download subtitles from OpenSubtitles (title + file-hash matching). */
@Composable
fun OnlineSubtitleDialog(
    videoTitle: String,
    videoUri: String,
    onLoaded: (LocalSubtitle) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { OpenSubtitlesProvider() }
    remember(context) { PlayerSettings.init(context); PlayerSettings }
    val savedLang by PlayerSettings.subtitleLanguage.collectAsState()
    var language by remember { mutableStateOf(savedLang) }
    var query by remember { mutableStateOf(cleanQuery(videoTitle)) }
    var hash by remember { mutableStateOf<MovieHash?>(null) }
    var loading by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<OnlineSubtitle>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    fun search() {
        if (loading) return
        loading = true; error = null
        scope.launch {
            runCatching {
                if (hash == null) hash = MovieHasher.compute(context, Uri.parse(videoUri))
                provider.search(query, language, hash)
            }.onSuccess { results = it }.onFailure { error = it.message ?: "Search failed" }
            loading = false; searched = true
        }
    }

    LaunchedEffect(Unit) { search() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Online subtitles", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Movie or episode name") },
                    trailingIcon = {
                        IconButton(onClick = { search() }) { Icon(Icons.Default.Search, "Search", tint = SpotifyGreen) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen, focusedLabelColor = SpotifyGreen),
                    modifier = Modifier.fillMaxWidth().testTag("subtitle_query"),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SUBTITLE_LANGUAGES, key = { it.code3 }) { lang ->
                        FilterChip(
                            selected = lang.code3 == language,
                            onClick = {
                                language = lang.code3
                                PlayerSettings.setSubtitleLanguage(context, lang.code3)
                                search()
                            },
                            label = { Text(lang.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent,
                            ),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp)) {
                    when {
                        loading -> CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.align(Alignment.Center))
                        error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                        searched && results.isEmpty() -> Text(
                            "No subtitles found. Try a shorter title or another language.",
                            color = TextSecondary, modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(results, key = { "${it.provider}_${it.id}" }) { sub ->
                                SubtitleResultRow(sub, downloading = downloadingId == sub.id) {
                                    if (downloadingId != null) return@SubtitleResultRow
                                    downloadingId = sub.id
                                    scope.launch {
                                        runCatching { provider.download(context, sub) }
                                            .onSuccess { onLoaded(it) }
                                            .onFailure { error = it.message ?: "Download failed" }
                                        downloadingId = null
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Source: OpenSubtitles.org", color = TextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = SpotifyGreen) } },
    )
}

@Composable
private fun SubtitleResultRow(sub: OnlineSubtitle, downloading: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceContainerHigh,
        border = if (sub.hashMatch) BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.7f)) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.release.ifBlank { sub.fileName }, color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sub.hashMatch) {
                        Icon(Icons.Default.Verified, null, tint = SpotifyGreen, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Exact match · ", color = SpotifyGreen, fontSize = 11.sp)
                    }
                    Text("${sub.language} · ${sub.format.uppercase()} · ${sub.downloads} downloads", color = TextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (downloading) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Icon(Icons.Default.CloudDownload, "Download", tint = SpotifyGreen, modifier = Modifier.size(22.dp))
        }
    }
}
