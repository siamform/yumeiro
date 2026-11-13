/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import dev.asfakul.yumeiro.ui.theme.BrandGold
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
import dev.asfakul.yumeiro.notify.Reminders
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
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

    // Background checks stopped running (the phone's battery manager kills them): say so once per launch.
    var stalledShown by remember { mutableStateOf(false) }
    LaunchedEffect(blocked) {
        if (blocked || Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        delay(8_000)
        if (dev.asfakul.yumeiro.notify.BackgroundHealth.stalledAtLaunch && !PermissionCenter.batteryUnrestricted(context) &&
            showing == null && PopupGate.claim(GATE_REMINDER)) {
            stalledShown = true
        }
    }
    if (stalledShown) {
        val done = { stalledShown = false; dev.asfakul.yumeiro.notify.BackgroundHealth.acknowledge(); PopupGate.release(GATE_REMINDER) }
        ReminderCard(
            icon = Icons.Default.NotificationsActive, tag = "reminder_battery",
            title = "Notifications are being held back",
            body = "Your phone stopped Yumeiro's background checks to save battery, so new-episode alerts arrive late. Let Yumeiro run in the background to get them on time.",
            action = "Allow background",
            onAction = { PermissionCenter.openBatterySettings(context); done() },
            onClose = done,
        )
    }

    when (showing) {
        Reminders.Kind.FOLLOW -> FollowCard(onClose = close)
        Reminders.Kind.STAR -> ReminderCard(
            icon = Icons.Default.Star, tag = "reminder_star",
            title = "Enjoying Yumeiro?",
            body = SupportMessage.STAR,
            action = "Star on GitHub",
            onAction = { if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context); close() },
            onClose = close,
        )
        Reminders.Kind.SUPPORT -> ReminderCard(
            icon = Icons.Default.Favorite, tag = "reminder_support",
            title = "Help keep Yumeiro alive",
            body = SupportMessage.SHORT,
            action = "Buy me a coffee",
            onAction = { close(); AppPages.open(AppPages.Page.SUPPORT) },
            onClose = close,
        )
        null -> Unit
    }
}

@Composable
private fun CardShell(tag: String, onClose: () -> Unit, inline: Boolean = false, content: @Composable () -> Unit) {
    // inline: drawn in place (screenshot tests); the app always shows it as a dialog.
    if (inline || dev.asfakul.yumeiro.ui.components.LocalPopupPreview.current) { ReminderSurface(tag, Modifier.padding(horizontal = 28.dp), content); return }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReminderSurface(tag, Modifier.padding(horizontal = 28.dp), content)
    }
}

/**
 * The card itself. The old shell painted a gradient on a *transparent* Surface with a 20dp shadow,
 * so the shadow was drawn over the card body: grey smudged edges and a muddy middle in light theme.
 * Now the Surface is opaque in the theme colour, the wash sits inside it and the border is a real
 * border, matching [dev.asfakul.yumeiro.ui.components.TsukiDialog].
 */
@Composable
internal fun ReminderSurface(tag: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val glow = dev.asfakul.yumeiro.ui.theme.LocalAmbientColor.current?.value ?: palette.accent
    val shape = RoundedCornerShape(28.dp)
    val base = palette.surfaceContainer
    val wash = remember(base, glow, palette.isDark) {
        Brush.verticalGradient(
            listOf(
                androidx.compose.ui.graphics.lerp(base, glow, if (palette.isDark) 0.20f else 0.08f),
                base,
                if (palette.isDark) palette.surface else base,
            ),
        )
    }
    val hairline = remember(glow, palette.isDark) {
        Brush.linearGradient(listOf(BrandGold.copy(alpha = if (palette.isDark) 0.55f else 0.7f), glow.copy(alpha = 0.2f), BrandGold.copy(alpha = 0.15f)))
    }
    val scale = remember { androidx.compose.animation.core.Animatable(0.94f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) }
    Surface(
        shape = shape,
        color = base,
        contentColor = palette.textPrimary,
        shadowElevation = if (palette.isDark) 20.dp else 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, hairline),
        modifier = modifier.fillMaxWidth().widthIn(max = 460.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .testTag(tag),
    ) { Box(Modifier.background(wash).padding(horizontal = 22.dp, vertical = 24.dp)) { content() } }
}

/** Round gold-rimmed badge carrying the card's icon. */
@Composable
private fun IconBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 60.dp) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val glow = dev.asfakul.yumeiro.ui.theme.LocalAmbientColor.current?.value ?: p.accent
    // Light theme: a solid accent disc with a light glyph (pale gold on cream was unreadable).
    val fill = if (p.isDark) Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), BrandGold.copy(alpha = 0.08f)))
    else Brush.linearGradient(listOf(p.accentBright, p.accent))
    val tint = if (p.isDark) BrandGold else p.onAccent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(fill)
            .border(1.dp, if (p.isDark) BrandGold.copy(alpha = 0.5f) else p.accent.copy(alpha = 0.35f), CircleShape),
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

/** Brand colour of each social network (their own guidelines), so the buttons are recognisable at a glance. */
private fun brandColor(s: Reminders.Social) = when (s) {
    Reminders.Social.TELEGRAM_CHANNEL -> androidx.compose.ui.graphics.Color(0xFF229ED9)
    Reminders.Social.TELEGRAM_GROUP -> androidx.compose.ui.graphics.Color(0xFF1C7FB0)
}

@Composable
internal fun ReminderCard(icon: ImageVector, tag: String, title: String, body: String, action: String, onAction: () -> Unit, onClose: () -> Unit, inline: Boolean = false) {
    CardShell(tag, onClose, inline) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconBadge(icon)
            Spacer(Modifier.height(14.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("${tag}_action"),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
            ) { Text(action, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            TextButton(onClick = onClose, modifier = Modifier.testTag("${tag}_later")) { Text("Maybe later", color = TextSecondary) }
        }
    }
}

@Composable
internal fun FollowCard(inline: Boolean = false, onClose: () -> Unit) {
    val context = LocalContext.current
    val total = Reminders.FOLLOW_COUNTDOWN_S
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1_000); left-- }; onClose() }
    CardShell("reminder_follow", onClose, inline) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                IconBadge(Icons.Default.People, 52.dp)
                CircularProgressIndicator(progress = { left / total.toFloat() }, color = if (dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.isDark) BrandGold else dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent, trackColor = androidx.compose.ui.graphics.Color.Transparent, strokeWidth = 3.dp, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Join the Yumeiro community", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text("Get Yumeiro news first on Telegram, ask for help and share ideas.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Reminders.Social.entries.forEach { s ->
                    Button(
                        onClick = { if (Links.open(context, s.url)) Reminders.recordFollow(context, s); onClose() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor(s), contentColor = androidx.compose.ui.graphics.Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("follow_${s.name.lowercase()}"),
                    ) { Text(s.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
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
