/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.player.SleepTimer
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.delay

/** "12:04" style remaining time for a countdown, or null. */
@Composable
fun rememberSleepRemaining(state: SleepTimer.State?): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state is SleepTimer.State.Countdown) { now = System.currentTimeMillis(); delay(1000) }
    }
    return when (state) {
        is SleepTimer.State.Countdown -> {
            val s = ((state.endsAtMs - now).coerceAtLeast(0) / 1000)
            "%d:%02d".format(s / 60, s % 60)
        }
        is SleepTimer.State.EndOfTrack -> "End of song"
        null -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimer.State?,
    onStart: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalMtPalette.current
    val remaining = rememberSleepRemaining(state)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet, dragHandle = { dev.asfakul.yumeiro.ui.components.MoonSheetHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).testTag("sleep_timer_sheet")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, null, tint = p.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sleep timer", style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (remaining != null) "Music pauses in $remaining" else "Music fades out and pauses when the timer ends",
                color = p.textSecondary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("sleep_timer_status"),
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepTimer.PRESETS.forEach { m ->
                    Chip(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min", "sleep_$m") { onStart(m); onDismiss() }
                }
                Chip("End of song", "sleep_end_of_track", icon = true) { onEndOfTrack(); onDismiss() }
            }
            if (state != null) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state is SleepTimer.State.Countdown) Chip("+5 min", "sleep_extend") { onExtend(5) }
                    Chip("Turn off", "sleep_cancel", danger = true) { onCancel(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, tag: String, icon: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (danger) Color(0x33FF5A6E) else p.glass)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) { Icon(Icons.Default.MusicNote, null, tint = p.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (danger) dev.asfakul.yumeiro.ui.theme.DangerTone else p.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}
