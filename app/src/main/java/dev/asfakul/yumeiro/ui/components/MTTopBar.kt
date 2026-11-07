/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.glass
import java.util.Calendar

/** Time-aware greeting used as the Home headline ("Good evening"). */
fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/** 44dp round glass icon button (prototype header style). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    size: Int = 44,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .glass(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MTTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    /** Opens the notification inbox (bell). */
    onInboxClick: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    overline: String? = null,
    modifier: Modifier = Modifier,
    /** Typeahead rows for what is typed so far (recent searches, songs, artists, albums, videos). */
    suggestions: List<Suggestion> = emptyList(),
    /** Called when a search is committed (keyboard Search or a picked suggestion), to remember it. */
    onSubmit: (String) -> Unit = {},
    /** Unread notifications, shown as a badge on the bell. */
    unreadCount: Int = 0,
    /** False on tabs that have their own search box (Videos, Stream, online Music). */
    showSearch: Boolean = true,
) {
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    // The text last committed; suggestions hide until the user types something new.
    var committed by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(showSearch) {
        if (!showSearch && isSearchExpanded) { isSearchExpanded = false; committed = ""; onSearchQueryChange("") }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
    ) {
        if (isSearchExpanded && showSearch) {
            Column(Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        committed = searchQuery; onSubmit(searchQuery); focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("topbar_search_input"),
                    placeholder = {
                        Text("Search songs, videos, artists", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SpotifyGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clickable(role = Role.Button) { onSearchQueryChange("") }
                                    .testTag("search_clear_button"),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary) }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(26.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GlassFill,
                        unfocusedContainerColor = GlassFill,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = SpotifyGreen,
                    )
                )
                Spacer(Modifier.width(10.dp))
                GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close search",
                    onClick = { isSearchExpanded = false; committed = ""; onSearchQueryChange("") },
                    modifier = Modifier.testTag("search_close_button")
                )
            }
            if (searchQuery.isNotBlank() && searchQuery != committed) {
                SuggestionList(
                    query = searchQuery,
                    suggestions = suggestions,
                    onPick = { s -> committed = s.text; onSearchQueryChange(s.text); onSubmit(s.text); focusManager.clearFocus() },
                    onFill = { s -> onSearchQueryChange(s.text) },
                    modifier = Modifier.padding(top = 6.dp),
                    tag = "topbar_suggestions",
                )
            }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBackButton) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackClick,
                        modifier = Modifier.testTag("topbar_back_button")
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (overline != null) {
                        Text("✦ " + overline.uppercase(), style = OverlineStyle, color = SpotifyGreen, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                    }
                    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
                    val titleBrush = androidx.compose.runtime.remember(p.textPrimary, p.accentBright) {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.textPrimary, p.textPrimary, p.accentBright))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(brush = titleBrush),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showSearch) GlassIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search your library",
                    onClick = { isSearchExpanded = true },
                    modifier = Modifier.testTag("topbar_search_action")
                )
                Box {
                    GlassIconButton(
                        icon = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = if (unreadCount > 0) "Notifications, $unreadCount new" else "Notifications",
                        onClick = onInboxClick,
                        modifier = Modifier.testTag("topbar_inbox_action")
                    )
                    if (unreadCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)
                                .size(if (unreadCount > 9) 20.dp else 17.dp)
                                .background(SpotifyGreen, CircleShape)
                                .testTag("topbar_inbox_badge"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unreadCount > 9) "9+" else unreadCount.toString(),
                                color = dev.asfakul.yumeiro.ui.theme.OnAccent,
                                fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
                            )
                        }
                    }
                }
                if (!showBackButton) {
                    GlassIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "Open Settings",
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("topbar_settings_action")
                    )
                }
            }
        }
    }
}
