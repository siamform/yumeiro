/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.asfakul.yumeiro.network.Connectivity
import dev.asfakul.yumeiro.notify.AppFeed
import dev.asfakul.yumeiro.notify.AppNotifier
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.pageBackground
import kotlinx.coroutines.launch

/** The bell page: everything Yumeiro told the user, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onBack: () -> Unit, onAction: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Inbox.ensureLoaded(context) }
    val items by Inbox.items.collectAsState()
    var block by remember { mutableStateOf(AppNotifier.block(context)) }
    LifecycleResumeEffect(Unit) { block = AppNotifier.block(context); onPauseOrDispose { } }
    var refreshing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // Leaving the page marks everything as seen.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Inbox.markAllRead(context) } }

    if (confirmClear) TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all notifications?") },
        text = { Text("They are removed from this page. Your settings stay the same.") },
        confirmButton = { TextButton(onClick = { Inbox.clear(context); confirmClear = false }, modifier = Modifier.testTag("inbox_clear_confirm")) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )

    Box(Modifier.fillMaxSize().pageBackground().testTag("inbox_screen")) {
        Column(Modifier.fillMaxSize()) {
            val unread = items.count { !it.read }
            SubPageHeader(
                title = "Notifications",
                subtitle = if (items.isEmpty()) "Nothing new yet" else if (unread > 0) "$unread new" else "${items.size} in your inbox",
                onBack = onBack,
            ) {
                if (unread > 0) IconButton(onClick = { Inbox.markAllRead(context) }, modifier = Modifier.testTag("inbox_mark_read")) {
                    Icon(Icons.Default.DoneAll, "Mark all as read", tint = TextPrimary)
                }
                if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("inbox_clear")) {
                    Icon(Icons.Default.DeleteSweep, "Clear all", tint = TextPrimary)
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        if (!Connectivity.isOnline(context)) AppMessages.show(context, "You're offline. Showing saved notifications")
                        else runCatching { AppFeed.sync(context) }.onFailure { AppMessages.error(context, "Couldn't check for news. Try again later") }
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("inbox_list"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("inbox"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (block != AppNotifier.Block.NONE) item("blocked") {
                        BlockedBanner(block) {
                            if (block == AppNotifier.Block.MASTER_OFF) dev.asfakul.yumeiro.notify.NotifySettings.set(context, dev.asfakul.yumeiro.notify.NotifySettings.Key.ENABLED, true)
                            else PermissionCenter.openNotificationSettings(context)
                            block = AppNotifier.block(context)
                        }
                    }
                    if (items.isEmpty()) item("empty") {
                        StateMessage(
                            Icons.Default.NotificationsNone, "You're all caught up",
                            "New episodes, extensions, streams and Yumeiro news show up here. Pull down to check now.",
                            tint = TextSecondary, testTag = "inbox_empty",
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        InboxRow(
                            item,
                            onClick = {
                                Inbox.markRead(context, item.id)
                                item.link?.let { l -> if (l.startsWith("https://")) Links.open(context, l) else onAction(l) }
                            },
                            onRemove = { Inbox.remove(context, item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedBanner(block: AppNotifier.Block, onFix: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = SpotifyGreen.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().testTag("inbox_blocked")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsOff, null, tint = SpotifyGreen)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(block.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${block.hint} Until then, news only shows up here.", color = TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onFix) { Text("Turn on", color = SpotifyGreen, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun iconFor(kind: Inbox.Kind): ImageVector = when (kind) {
    Inbox.Kind.EPISODE -> Icons.Default.PlayCircle
    Inbox.Kind.EXTENSION -> Icons.Default.Extension
    Inbox.Kind.PLAYLIST -> Icons.Default.LiveTv
    Inbox.Kind.UPDATE -> Icons.Default.SystemUpdate
    Inbox.Kind.NEWS -> Icons.Default.AutoAwesome
    Inbox.Kind.NUDGE -> Icons.Default.TipsAndUpdates
    Inbox.Kind.INFO -> Icons.Default.Notifications
    Inbox.Kind.ALERT -> Icons.Default.Campaign
}

@Composable
private fun InboxRow(item: Inbox.Item, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (item.read) GlassFill else SpotifyGreen.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("inbox_item"),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).background(SpotifyGreen.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(iconFor(item.kind), null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.kind.label.uppercase(), color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateUtils.getRelativeTimeSpanString(item.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        color = TextTertiary, fontSize = 11.sp, maxLines = 1,
                    )
                    if (!item.read) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(7.dp).background(SpotifyGreen, CircleShape)) }
                }
                Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.body.isNotBlank()) Text(item.body, color = TextSecondary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Remove", tint = TextTertiary, modifier = Modifier.size(16.dp)) }
        }
    }
}
