/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.BackdropSettings
import dev.asfakul.yumeiro.ui.theme.BackdropStyle
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.MtPalette
import dev.asfakul.yumeiro.ui.theme.buildScene
import kotlinx.coroutines.launch

/** A small live preview of a theme's 夢色 scene (same renderer as the page backdrop). */
fun Modifier.dreamScenePreview(p: MtPalette, intensity: Float = 1f, scene: Boolean = true): Modifier = drawWithCache {
    val layer = if (scene) buildScene(p, size.width, size.height, intensity) else null
    val brush = Brush.verticalGradient(0f to p.bgTop, 0.42f to p.bgMid, 1f to p.bgBottom)
    onDrawBehind { drawRect(brush); layer?.draw(this, 0f) }
}

/**
 * Settings → Appearance → Backdrop: the original Yumeiro background or your photo, plus glow intensity. The photo stays on
 * the device (app storage), is downscaled on import and drawn blurred under a theme scrim.
 */
@Composable
fun BackdropSettingsSection() {
    val context = LocalContext.current
    remember(context) { BackdropSettings.init(context) }
    val p = LocalMtPalette.current
    val style by BackdropSettings.style.collectAsState()
    val stored by BackdropSettings.intensity.collectAsState()
    val photoV by BackdropSettings.photoVersion.collectAsState()
    var live by remember(stored) { mutableFloatStateOf(stored) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = BackdropSettings.importPhoto(context, uri)
            if (ok) AppMessages.success(context, "Backdrop updated") else AppMessages.error(context, "That picture couldn't be opened. Try another one.")
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Text("Backdrop", color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    Text("夢色 — the picture behind every page. Each theme has its own scene.", color = p.textTertiary, fontSize = 12.5.sp, lineHeight = 17.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BackdropStyle.entries.filter { it != BackdropStyle.CALM }.forEach { s ->
            val sel = style == s
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .clickable(role = Role.RadioButton) {
                        if (s == BackdropStyle.PHOTO && photoV == 0L) pick() else BackdropSettings.setStyle(context, s)
                    }
                    .semantics { selected = sel }
                    .testTag("backdrop_${s.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(shape)
                        .dreamScenePreview(p, live, scene = s == BackdropStyle.SCENE)
                        .border(if (sel) 2.dp else 1.dp, if (sel) p.accent else p.textPrimary.copy(alpha = 0.14f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (s == BackdropStyle.PHOTO) Icon(Icons.Default.AddPhotoAlternate, null, tint = p.textSecondary, modifier = Modifier.size(28.dp))
                    if (sel) Icon(Icons.Default.CheckCircle, null, tint = p.accent, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(s.label, color = if (sel) p.textPrimary else p.textSecondary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                Text(s.detail, color = p.textTertiary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
    if (style == BackdropStyle.PHOTO && photoV != 0L) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTextButton("Change photo", p.accent, "backdrop_photo_change", pick)
            SmallTextButton("Remove photo", p.textSecondary, "backdrop_photo_remove") { BackdropSettings.removePhoto(context) }
        }
    }
    run {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Intensity", color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(when { live < 0.7f -> "Faint"; live < 1.2f -> "Balanced"; else -> "Vivid" }, color = p.textTertiary, fontSize = 12.sp)
        }
        Slider(
            value = live, onValueChange = { live = it }, valueRange = 0.3f..1.6f,
            onValueChangeFinished = { BackdropSettings.setIntensity(context, live) },
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.textPrimary.copy(alpha = 0.16f)),
            modifier = Modifier.testTag("backdrop_intensity"),
        )
    }
}

@Composable
private fun SmallTextButton(label: String, color: androidx.compose.ui.graphics.Color, tag: String, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick)
            .testTag(tag).padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
