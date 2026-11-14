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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.ReliabilityGuide
import dev.asfakul.yumeiro.notify.ReliabilityGuide.Check
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/**
 * Round 2, order 9: "Keep notifications on time". Live checklist of the Android settings (each
 * with a one-tap fix, re-checked when the user comes back), then the phone maker's own steps
 * with a button that opens the right page on this phone.
 */
@Composable
fun ReliabilityGuideDialog(onClose: () -> Unit, inline: Boolean = false) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val status = remember(tick) { ReliabilityGuide.status(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) PermissionCenter.openNotificationSettings(context); tick++
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(22.dp)) {
            Text("Keep notifications on time", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (status.stalled) "Yumeiro's background checks haven't run for a while, so alerts arrive late. These settings let them run while the app is closed."
                else "Android and your phone maker can stop apps that are closed. Check each item once.",
                color = TextSecondary, fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            Check.entries.forEach { check ->
                val ok = check !in status.failing
                CheckRow(check.title, if (ok) null else check.fix, ok, "guide_${check.name.lowercase()}") {
                    when (check) {
                        Check.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else PermissionCenter.openNotificationSettings(context)
                        Check.BACKGROUND_ON -> { NotifySettings.set(context, NotifySettings.Key.BACKGROUND, true); tick++ }
                        Check.BATTERY -> PermissionCenter.openBatterySettings(context)
                        Check.NOT_RESTRICTED, Check.NOT_STANDBY_RESTRICTED -> PermissionCenter.openAppSettings(context)
                    }
                }
            }
            val v = status.vendor
            if (v.aggressive) {
                Spacer(Modifier.height(12.dp))
                Text(v.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                v.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { ReliabilityGuide.openVendorPage(context, v) }, modifier = Modifier.weight(1f).testTag("guide_vendor_open")) {
                        Text("Open settings")
                    }
                    Button(
                        onClick = { ReliabilityGuide.markVendorDone(context, !status.vendorDone); tick++ },
                        colors = ButtonDefaults.buttonColors(containerColor = if (status.vendorDone) TextSecondary else SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.weight(1f).testTag("guide_vendor_done"),
                    ) { Text(if (status.vendorDone) "Done ✓" else "I did this") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("guide_close"),
            ) { Text(if (status.allGood) "All set" else "Close", fontWeight = FontWeight.Bold) }
        }
    }
    if (inline || dev.asfakul.yumeiro.ui.components.LocalPopupPreview.current) { ReminderSurface("reliability_guide", Modifier.padding(horizontal = 20.dp), body); return }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReminderSurface("reliability_guide", Modifier.padding(horizontal = 20.dp), body)
    }
}

@Composable
private fun CheckRow(title: String, fix: String?, ok: Boolean, tag: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag)) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
            tint = if (ok) SpotifyGreen else androidx.compose.ui.graphics.Color(0xFFE5A100), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (fix != null) Text(fix, color = TextSecondary, fontSize = 12.sp)
        }
        if (!ok) TextButton(onClick = onFix, modifier = Modifier.testTag("${tag}_fix")) { Text("Fix") }
    }
}
