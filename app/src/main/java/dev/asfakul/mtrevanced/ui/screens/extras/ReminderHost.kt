/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.screens.extras

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.asfakul.mtrevanced.notify.Reminders
import dev.asfakul.mtrevanced.ui.theme.OnAccent
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary
import kotlinx.coroutines.delay

private const val GATE_REMINDER = "reminder"
private const val GATE_NOTIF = "notif_prompt"

/**
 * In-app reminder cards (follow / star / support). Only one ever shows at a time, never over a
 * video, the update dialog, PiP or another popup, and each kind is spaced out by [Reminders].
 */
@Composable
fun ReminderHost(blocked: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showing by remember { mutableStateOf<Reminders.Kind?>(null) }

    LaunchedEffect(blocked) {
        if (blocked || Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        delay(20_000)
        while (true) {
            if (showing == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val kind = Reminders.due(context)
                if (kind != null && PopupGate.claim(GATE_REMINDER)) {
                    Reminders.markShown(context, kind)
                    showing = kind
                }
            }
            delay(60_000)
        }
    }
    val close = { showing = null; PopupGate.release(GATE_REMINDER) }
    // A video/update/PiP taking over closes the card instead of drawing on top of it.
    LaunchedEffect(blocked) { if (blocked && showing != null) close() }

    when (showing) {
        Reminders.Kind.FOLLOW -> FollowCard(onClose = close)
        Reminders.Kind.STAR -> ReminderCard(
            icon = Icons.Default.Star, tag = "reminder_star",
            title = "Enjoying Yumeiro?",
            body = "A star on GitHub helps more people find the app. It takes one tap.",
            action = "Star on GitHub",
            onAction = { if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context); close() },
            onClose = close,
        )
        Reminders.Kind.SUPPORT -> ReminderCard(
            icon = Icons.Default.Favorite, tag = "reminder_support",
            title = "Keep Yumeiro free",
            body = "No ads, no tracking. If Yumeiro helps you, a small coffee keeps it going.",
            action = "Buy me a coffee",
            onAction = { close(); AppPages.open(AppPages.Page.SUPPORT) },
            onClose = close,
        )
        null -> Unit
    }
}

@Composable
private fun CardShell(tag: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = dev.asfakul.mtrevanced.ui.theme.LocalMtPalette.current.surface,
            modifier = Modifier.padding(horizontal = 28.dp).fillMaxWidth().testTag(tag),
        ) { Box(Modifier.padding(22.dp)) { content() } }
    }
}

@Composable
private fun ReminderCard(icon: ImageVector, tag: String, title: String, body: String, action: String, onAction: () -> Unit, onClose: () -> Unit) {
    CardShell(tag, onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction, modifier = Modifier.fillMaxWidth().testTag("${tag}_action"),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
            ) { Text(action, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onClose, modifier = Modifier.testTag("${tag}_later")) { Text("Maybe later", color = TextSecondary) }
        }
    }
}

@Composable
private fun FollowCard(onClose: () -> Unit) {
    val context = LocalContext.current
    val total = Reminders.FOLLOW_COUNTDOWN_S
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1_000); left-- }; onClose() }
    CardShell("reminder_follow", onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                CircularProgressIndicator(progress = { left / total.toFloat() }, color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                Icon(Icons.Default.People, null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Follow the developer", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text("Get Yumeiro news first, share ideas and say hi.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Reminders.Social.entries.forEach { s ->
                    OutlinedButton(
                        onClick = { if (Links.open(context, s.url)) Reminders.recordFollow(context, s); onClose() },
                        modifier = Modifier.fillMaxWidth().testTag("follow_${s.name.lowercase()}"),
                    ) { Text(s.label, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Closes in ${left}s", color = TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.testTag("reminder_follow_later")) { Text("Later", color = TextSecondary) }
            }
        }
    }
}

/**
 * Asks to turn notifications on when they are off: once per app version (users who installed an
 * old build were never asked after notifications were added). Uses the system prompt when Android
 * still allows it, otherwise opens the app's notification settings.
 */
@Composable
fun NotificationPromptHost(blocked: Boolean) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !PermissionCenter.notificationsAllowed(context)) PermissionCenter.openNotificationSettings(context)
    }
    LaunchedEffect(blocked) {
        if (blocked || Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        delay(4_000)
        if (PermissionCenter.shouldPromptNotifications(context) && PopupGate.claim(GATE_NOTIF)) {
            PermissionCenter.markNotificationsPrompted(context)
            show = true
        }
    }
    val close = { show = false; PopupGate.release(GATE_NOTIF) }
    if (show) ReminderCard(
        icon = Icons.Default.NotificationsActive, tag = "notif_prompt",
        title = "Turn on notifications",
        body = "Hear about new episodes, extensions and app updates. You choose exactly which ones in Settings.",
        action = "Turn on",
        onAction = {
            close()
            val canAsk = Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else PermissionCenter.openNotificationSettings(context)
        },
        onClose = close,
    )
}
