/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.extras.Links
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Thin gold hairline used around premium cards (matches TsukiDialog and the reminder sheet). */
@Composable
internal fun goldHairline(): Brush {
    val p = LocalMtPalette.current
    return Brush.linearGradient(listOf(BrandGold.copy(alpha = if (p.isDark) 0.42f else 0.6f), p.accent.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)))
}

/** Rounded square with a soft gradient and a tinted icon: the visual anchor of every category row. */
@Composable
internal fun IconTile(icon: ImageVector, tint: Color, size: Int = 38) {
    Box(
        // Calmer tonal tile: the category tint is kept, but as a quiet wash instead of a glowing chip.
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.30f).dp))
            .background(tint.copy(alpha = if (LocalMtPalette.current.isDark) 0.16f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.52f).dp)) }
}

/** Small caps label above a group of categories ("WATCH & LISTEN"). */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        dev.asfakul.yumeiro.ui.components.MoonPath(height = 12.dp)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
    }
}

/** A glass card holding a column of rows, with the gold hairline. */
@Composable
internal fun GlassGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeLg, color = GlassFill,
        modifier = modifier.fillMaxWidth().border(1.dp, dev.asfakul.yumeiro.ui.components.rememberMoonHairline(), dev.asfakul.yumeiro.ui.theme.Yume.shapeLg),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

/** One category row: tile, title, summary, optional status chip and a chevron. */
@Composable
internal fun CategoryRow(
    icon: ImageVector, tint: Color, title: String, summary: String, testTag: String,
    status: String? = null, statusOk: Boolean? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(summary, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            StatusChip(status, statusOk)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
    }
}

@Composable
internal fun StatusChip(text: String, ok: Boolean?) {
    val c = when (ok) { true -> SpotifyGreen; false -> Color(0xFFFFB74D); null -> TextSecondary }
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f)) {
        Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Divider indented past the icon tile so rows in a group read as one list. */
@Composable
internal fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp).height(0.5.dp).background(TextTertiary.copy(alpha = 0.16f)))
}

/** Header shown at the top of a category page: big tile, title and one-line description. */
@Composable
internal fun CategoryHero(icon: ImageVector, tint: Color, title: String, summary: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 52)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(2.dp))
            Text(summary, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

internal fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    AppMessages.success(context, "Copied")
}

/**
 * Collapsible step-by-step guide. Starts open when [startExpanded] (e.g. nothing set up yet) so a new
 * user sees how to begin, and folds away once they know.
 */
@Composable
fun GuideCard(guide: SetupGuide, modifier: Modifier = Modifier, startExpanded: Boolean = false) {
    val context = LocalContext.current
    var open by rememberSaveable(guide.id) { mutableStateOf(startExpanded) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "guide_chevron")
    Surface(
        shape = RoundedCornerShape(20.dp), color = GlassFill,
        modifier = modifier.fillMaxWidth().border(1.dp, goldHairline(), RoundedCornerShape(20.dp)).animateContentSize().testTag("guide_${guide.id}"),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.testTag("guide_${guide.id}_toggle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.AutoMirrored.Filled.MenuBook, BrandGold, size = 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(guide.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (!open) Text("Step-by-step · tap to open", color = TextTertiary, fontSize = 11.sp)
                }
                Icon(Icons.Default.ExpandMore, if (open) "Hide guide" else "Show guide", tint = TextSecondary, modifier = Modifier.rotate(turn))
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(guide.intro, color = TextSecondary, fontSize = 13.sp)
                    guide.steps.forEachIndexed { i, s -> GuideStep(i + 1, s, context) }
                    guide.tips.forEach { tip ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Lightbulb, null, tint = BrandGold.copy(alpha = 0.8f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tip, color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(n: Int, s: SetupGuide.Step, context: Context) {
    val dark = LocalMtPalette.current.isDark
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BrandGold, SpotifyGreen))),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = Color(0xFF15120C), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.text, color = TextPrimary, fontSize = 13.sp)
            if (s.copy != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (dark) Color.Black.copy(alpha = 0.22f) else BrandGold.copy(alpha = 0.12f))
                        .border(0.5.dp, BrandGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { copyToClipboard(context, "Yumeiro setup", s.copy) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.copy, color = if (dark) BrandGold else TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            if (s.link != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, SpotifyGreen.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { if (!Links.open(context, s.link)) AppMessages.error(context, "Couldn't open the browser") }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.linkLabel ?: "Open", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = SpotifyGreen, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
