/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import dev.asfakul.yumeiro.ui.theme.yuRound

/** F-09: Favorite heart colour (4.6:1 on the dark chip). */
internal val FavoritePink = Color(0xFFFF6B8B)

/** F-09 (ADR-033): the key a Bookmark uses, so a poster finds its mark without a lookup per card. */
internal fun posterMarkKey(item: OnlineItem) = "${item.api}|${item.url}"

/** F-09: the saved-state chip at the bottom right of a poster (heart wins the space when both). */
@Composable
internal fun PosterMark(mark: OnlineBookmarks.Mark?, modifier: Modifier = Modifier) {
    if (mark == null || (!mark.favorite && !mark.watchLater)) return
    val label = listOfNotNull(if (mark.favorite) "Favorite" else null, if (mark.watchLater) "Watch later" else null).joinToString(", ")
    Row(
        modifier.padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)).padding(3.dp)
            .testTag("poster_mark").semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (mark.watchLater) Icon(Icons.Default.Bookmark, null, tint = SpotifyGreen, modifier = Modifier.size(14.dp))
        if (mark.favorite) Icon(Icons.Default.Favorite, null, tint = FavoritePink, modifier = Modifier.size(14.dp))
    }
}

/**
 * F-09: Watch later and Favorite for one poster, without opening the title and without any network.
 * Phone: a short bottom sheet (opens fully, ADR-031). TV: a centred panel whose first focus is Watch later
 * (a sheet's drag handle took the first key before, SheetFocusTvDeviceTest). Each row acts and closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PosterActionsSheet(item: OnlineItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val marks by OnlineBookmarks.marks.collectAsState()
    val mark = marks[posterMarkKey(item)]
    val act: (Boolean) -> Unit = { favorite ->
        val c = if (favorite) OnlineBookmarks.toggleFavorite(context, item) else OnlineBookmarks.toggleWatchLater(context, item)
        val what = if (favorite) "Favorites" else "Watch later"
        AppMessages.show(context, if (c == OnlineBookmarks.Change.ADDED) "Added to $what" else "Removed from $what")
        onDismiss()
    }
    val body: @Composable (FocusRequester?) -> Unit = { first ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("poster_actions")) {
            Text(item.name.replace('\n', ' '), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.api, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(12.dp))
            ActionRow(
                if (mark?.watchLater == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                if (mark?.watchLater == true) "Remove from Watch later" else "Watch later",
                SpotifyGreen, "poster_actions_watch_later", first,
            ) { act(false) }
            ActionRow(
                if (mark?.favorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (mark?.favorite == true) "Remove from Favorites" else "Favorite",
                FavoritePink, "poster_actions_favorite", null,
            ) { act(true) }
        }
    }
    if (LocalTvMode.current) {
        val first = remember { FocusRequester() }
        Dialog(onDismissRequest = onDismiss) {
            // F-09 fix: the dialog window attaches a few frames after composition on slow phones (api33-lowram),
            // so a single requestFocus() from outside the dialog was lost and focus stayed on the poster.
            // Ask from inside the dialog, once per frame, until the panel really holds focus (max ~2.5 s).
            var held by remember { mutableStateOf(false) }
            Box(
                Modifier.widthIn(max = 420.dp).clip(yuRound(20)).background(Color(0xFF16161C))
                    .onFocusChanged { if (it.hasFocus) held = true }.padding(vertical = 12.dp),
            ) { body(first) }
            // The dialog's own window can start in touch mode (api33-lowram, device run 44502098420), where a
            // plain focus request is refused: leave touch mode from the dialog's view first (TVs have none).
            val dialogView = androidx.compose.ui.platform.LocalView.current
            LaunchedEffect(Unit) {
                repeat(40) {
                    if (held) return@LaunchedEffect
                    androidx.compose.runtime.withFrameNanos { }
                    if (dialogView.isInTouchMode || !dialogView.hasFocus()) runCatching { dialogView.requestFocusFromTouch() }
                    runCatching { first.requestFocus() }
                    kotlinx.coroutines.delay(50)
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle()) {
            Box(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { body(null) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color, tag: String, focus: FocusRequester?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(14))
            .then(if (focused) Modifier.border(2.dp, Color.White, yuRound(14)) else Modifier)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .testTag(tag).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * F-09-FEED: Watch later / Favorite on the cards that are not a [PosterCard] (Yumix feed cards, Top 10, Continue
 * watching). Before, they had a plain click only, so a long press there did nothing. On when both
 * poster_actions and f09_feed_actions are on.
 */
@Composable
internal fun feedPosterActionsOn(): Boolean {
    val ctx = LocalContext.current
    return remember {
        dev.asfakul.yumeiro.FeatureFlags.on(ctx, dev.asfakul.yumeiro.FeatureFlags.Flag.POSTER_ACTIONS) &&
            dev.asfakul.yumeiro.FeatureFlags.on(ctx, dev.asfakul.yumeiro.FeatureFlags.Flag.F09_FEED_ACTIONS)
    }
}

/** F-09-FEED: tap opens; long press (phone, or hold OK on TV) or the remote's Menu key calls [onActions]. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun Modifier.posterPress(onClick: () -> Unit, onActions: (() -> Unit)?): Modifier =
    if (onActions == null) clickable(onClick = onClick)
    else onPreviewKeyEvent { e ->
        if (e.key == androidx.compose.ui.input.key.Key.Menu && e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { onActions(); true }
        else e.key == androidx.compose.ui.input.key.Key.Menu
    }.combinedClickable(onClick = onClick, onLongClick = onActions, onLongClickLabel = "Watch later or Favorite")
