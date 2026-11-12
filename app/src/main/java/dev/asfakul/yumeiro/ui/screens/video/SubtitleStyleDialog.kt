/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.player.SubtitleStyle
import dev.asfakul.yumeiro.player.SubtitleStyleSettings
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/** Subtitle look editor with a live preview. Changes apply instantly and are remembered. */
@Composable
fun SubtitleStyleDialog(onDismiss: () -> Unit) {
    TsukiDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("subtitle_style_done")) { Text("Done", color = SpotifyGreen) } },
        title = { Text("Subtitle style", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = { SubtitleStyleEditor() },
    )
}

@Composable
fun SubtitleStyleEditor(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember(context) { SubtitleStyleSettings.init(context); 0 }
    val s by SubtitleStyleSettings.style.collectAsState()
    fun change(f: (SubtitleStyle) -> SubtitleStyle) = SubtitleStyleSettings.customise(context, f)

    Column(modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubtitlePreview(s)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use Android caption settings", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text("Follows Settings → Accessibility → Captions", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = s.followSystem,
                onCheckedChange = { on -> SubtitleStyleSettings.set(context, s.copy(followSystem = on)) },
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = SpotifyGreen),
                modifier = Modifier.testTag("subtitle_style_system"),
            )
        }

        Label("SIZE")
        ChipRow(SubtitleStyle.SIZES.map { "${(it * 100).toInt()}%" to it }, current = s.size.takeIf { !s.followSystem }, tag = "sub_size") { v -> change { it.copy(size = v) } }

        Label("TEXT COLOUR")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SubtitleStyle.TextColor.entries.forEach { c ->
                val sel = !s.followSystem && s.color == c
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c.argb))
                        .border(if (sel) 3.dp else 1.dp, if (sel) SpotifyGreen else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable(role = Role.RadioButton) { change { it.copy(color = c) } }
                        .semantics { contentDescription = c.label; selected = sel }
                        .testTag("sub_color_${c.name.lowercase()}"),
                )
            }
        }

        Label("BACKGROUND")
        ChipRow(SubtitleStyle.Edge.entries.map { it.label to it }, current = s.edge.takeIf { !s.followSystem }, tag = "sub_edge") { v -> change { it.copy(edge = v) } }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bold text", color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = !s.followSystem && s.bold, onCheckedChange = { b -> change { it.copy(bold = b) } },
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = SpotifyGreen), modifier = Modifier.testTag("sub_bold"))
        }

        Label("HEIGHT FROM BOTTOM")
        Slider(
            value = if (s.followSystem) 0f else s.lift,
            onValueChange = { v -> change { it.copy(lift = v) } },
            valueRange = 0f..SubtitleStyle.MAX_LIFT,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
            modifier = Modifier.testTag("sub_lift"),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep the file's own colours", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text("Colours and positions written inside SRT, VTT or TTML files. ASS/SSA files always keep their look", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.keepFileStyling, onCheckedChange = { k -> change { it.copy(keepFileStyling = k) } },
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = SpotifyGreen), modifier = Modifier.testTag("sub_keep_file"))
        }

        TextButton(onClick = { SubtitleStyleSettings.reset(context) }, modifier = Modifier.testTag("sub_reset")) {
            Text("Reset to default", color = SpotifyGreen)
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = SpotifyGreen, fontWeight = FontWeight.Bold)

@Composable
private fun <T> ChipRow(options: List<Pair<String, T>>, current: T?, tag: String, onPick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, (label, v) ->
            val sel = v == current
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) SpotifyGreen else DarkSurfaceContainerHigh)
                    .clickable(role = Role.RadioButton) { onPick(v) }
                    .semantics { selected = sel }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("${tag}_$i"),
            ) { Text(label, color = if (sel) OnAccent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Approximate on-screen look, drawn over a scene-like gradient so contrast is visible. */
@Composable
private fun SubtitlePreview(s: SubtitleStyle) {
    val color = if (s.followSystem) Color.White else Color(s.color.argb)
    val edge = if (s.followSystem) SubtitleStyle.Edge.OUTLINE else s.edge
    val shadow = when (edge) {
        SubtitleStyle.Edge.OUTLINE -> Shadow(Color.Black, Offset.Zero, 6f)
        SubtitleStyle.Edge.SHADOW -> Shadow(Color.Black, Offset(3f, 3f), 4f)
        else -> null
    }
    val size = 16.sp * (if (s.followSystem) 1f else s.size)
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF4A6FA5), Color(0xFFE8B37A), Color(0xFF2B2D42))))
            .testTag("subtitle_style_preview"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Subtitles look like this",
            style = TextStyle(color = color, fontSize = size, fontWeight = if (!s.followSystem && s.bold) FontWeight.Bold else FontWeight.Normal, shadow = shadow, textAlign = TextAlign.Center),
            modifier = Modifier
                .padding(bottom = (10 + (if (s.followSystem) 0f else s.lift) * 120).dp)
                .then(if (edge == SubtitleStyle.Edge.BOX) Modifier.background(Color.Black.copy(alpha = 0.7f)) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
