/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.PushWake
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.extras.Links
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * Settings → Notifications → On-time alerts: optional UnifiedPush (ntfy) wake-ups, so the
 * background checks run on time even when Android holds them back. Free; see [PushWake].
 */
@Composable
fun PushAlertsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember(context) { PushWake.init(context); 0 }
    val st by PushWake.state.collectAsState()
    val background by NotifySettings.flow(NotifySettings.Key.BACKGROUND).collectAsState()
    var distributors by remember { mutableStateOf(PushWake.distributors(context)) }
    var pick by remember { mutableStateOf(false) }
    var install by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { distributors = PushWake.distributors(context); onPauseOrDispose { } }

    fun turnOn() {
        distributors = PushWake.distributors(context)
        when {
            distributors.isEmpty() -> install = true
            distributors.size == 1 -> PushWake.enable(context, distributors.first())
            else -> pick = true
        }
    }

    SettingsCard {
        SettingsToggleRow(
            "Reliable alerts (UnifiedPush)",
            if (st.enabled) "A free push app wakes Yumeiro every few hours, so new-episode and news alerts aren't held back"
            else "Free and optional. Uses the open-source ntfy app instead of Google services. Best if alerts arrive late",
            st.enabled,
            { on -> if (on) turnOn() else { PushWake.disable(context); AppMessages.success(context, "Reliable alerts are off") } },
            "toggle_push_alerts",
        )
        if (st.enabled) {
            SettingsDivider()
            val app = st.distributor?.let { PushWake.appLabel(context, it) } ?: "push app"
            val (status, ok) = when {
                st.error != null -> st.error!! to false
                !st.hasEndpoint -> "Waiting for $app to hand out a push address. Open $app once if this stays" to null
                st.schedule == PushWake.Schedule.NO -> "Connected through ${st.endpointHost}, but this server can't schedule wake-ups. Use ntfy (ntfy.sh or your own server)" to false
                else -> buildString {
                    append("Connected via $app")
                    st.endpointHost?.let { append(" · $it") }
                    if (st.pending > 0) append(" · ${st.pending} wake-ups planned")
                    append(". Last wake-up: ")
                    append(if (st.lastWakeAt > 0) DateUtils.getRelativeTimeSpanString(st.lastWakeAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "not yet")
                } to true
            }
            SettingsActionRow("Status", status, "push_status", status = if (ok == true) "On" else if (ok == false) "Check" else "Setting up", ok = ok) {
                distributors = PushWake.distributors(context)
                if (distributors.size > 1) pick = true else PushWake.refillIfDue(context)
            }
            SettingsDivider()
            SettingsActionRow("Send test", if (testing) "Sending…" else "Sends one wake-up through the push server. The last wake-up time updates within seconds", "push_test") {
                if (testing) return@SettingsActionRow
                testing = true
                scope.launch {
                    val err = PushWake.sendTest(context)
                    testing = false
                    if (err == null) AppMessages.success(context, "Sent. Watch the last wake-up time") else AppMessages.error(context, err)
                }
            }
            if (!background) Text(
                "Background checks are off under Background & battery, so wake-ups have nothing to run.",
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }

    if (pick) TsukiDialog(
        onDismissRequest = { pick = false },
        icon = { Icon(Icons.Default.NotificationsActive, null) },
        title = { Text("Choose a push app") },
        text = {
            Column {
                distributors.forEach { pkg ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick = false; PushWake.enable(context, pkg) }
                            .padding(vertical = 4.dp).testTag("push_pick_$pkg"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = st.distributor == pkg, onClick = { pick = false; PushWake.enable(context, pkg) }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                        Column {
                            Text(PushWake.appLabel(context, pkg), color = TextPrimary)
                            if (pkg == PushWake.NTFY_PACKAGE) Text("Recommended · supports scheduled wake-ups", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pick = false }) { Text("Close") } },
    )

    if (install) TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.INFO,
        onDismissRequest = { install = false },
        icon = { Icon(Icons.Default.NotificationsActive, null) },
        title = { Text("Install ntfy first") },
        text = {
            Text(
                "Reliable alerts need a free UnifiedPush app. ntfy is open source, needs no account, and is free to use. Install it, open it once, then come back and turn this on.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { install = false; Links.open(context, "https://f-droid.org/packages/io.heckel.ntfy/") }, modifier = Modifier.testTag("push_get_fdroid")) { Text("F-Droid") }
        },
        dismissButton = {
            TextButton(onClick = { install = false; Links.open(context, "https://play.google.com/store/apps/details?id=io.heckel.ntfy") }, modifier = Modifier.testTag("push_get_play")) { Text("Google Play") }
        },
    )
}
