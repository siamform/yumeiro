/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SectionTitleStyle

/*
 * "夢色 editorial" — the R5 signature layer on top of the Tsuki no Michi tokens. Yumeiro's identity
 * is a Japanese magazine page: a kana overline in the accent colour ("つづき · CONTINUE"), a wide
 * Unbounded heading, generous "ma" (negative space) between sections, a faint kanji watermark on
 * feature cards and a small hanko (判子) seal on new things. Colours still come from the palette.
 */

/** "つづき · CONTINUE": kana in the accent colour, the English label quiet and letter-spaced. */
@Composable
fun kanaOverline(kana: String?, label: String): AnnotatedString {
    val p = LocalMtPalette.current
    val on = kanaEnabled()
    return buildAnnotatedString {
        if (on && !kana.isNullOrBlank()) {
            pushStyle(SpanStyle(color = p.accent)); append(kana); pop()
            pushStyle(SpanStyle(color = p.textTertiary)); append("  ·  "); pop()
        }
        pushStyle(SpanStyle(color = p.textTertiary)); append(label.uppercase()); pop()
    }
}

/**
 * The one section header used across Home and the tabs: kana overline, Unbounded title and an
 * optional text action ("See all") with a 48dp target. No chevrons, nothing folds.
 */
@Composable
fun YuSection(
    kana: String?,
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    top: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val p = LocalMtPalette.current
    Row(modifier.fillMaxWidth().padding(top = top, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).semantics { heading() }) {
            Text(kanaOverline(kana, label), style = OverlineStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(4.dp))
            Text(title, style = SectionTitleStyle, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        if (action != null && onAction != null) {
            Box(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onAction)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

/** A large, faint kanji set into the corner of a card (音 映 読 彩 …). Decorative only. */
@Composable
fun KanjiMark(text: String, modifier: Modifier = Modifier, size: TextUnit = 64.sp, color: Color? = null, alpha: Float = 0.08f) {
    if (!kanaEnabled()) return
    val p = LocalMtPalette.current
    Text(
        text, modifier = modifier, color = (color ?: p.textPrimary).copy(alpha = alpha),
        fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, maxLines = 1,
    )
}

/** Hanko (判子) seal: a small vermilion stamp for new things ("新"). */
@Composable
fun Hanko(text: String = "新", modifier: Modifier = Modifier, contentDescription: String = "New") {
    Box(
        modifier.size(22.dp).rotate(-8f).clip(RoundedCornerShape(5.dp)).background(HankoRed)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black) }
}

/** Shu-iro (朱色), the vermilion of hanko ink and torii gates. Only used for the seal. */
val HankoRed = Color(0xFFD9452B)

/**
 * Translucent glass pill: something you can change ("Mix ⌄", "Filter", "Shuffle"). The see-through
 * fill and hairline say "this switches" without competing with the page title.
 */
@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    chevron: Boolean = false,
    expanded: Boolean = false,
    selected: Boolean = false,
    tag: String? = null,
    clickLabel: String? = null,
) {
    val p = LocalMtPalette.current
    val turn by animateFloatAsState(if (expanded) 180f else 0f, YuMotion.move(reducedMotion(), 250), label = "pillChevron")
    val shape = RoundedCornerShape(50)
    val fg = if (selected) p.onAccent else p.textPrimary
    Row(
        modifier.heightIn(min = 36.dp).clip(shape)
            .background(if (selected) p.accent else p.textPrimary.copy(alpha = if (p.isDark) 0.08f else 0.06f))
            .border(1.dp, if (selected) Color.Transparent else p.textPrimary.copy(alpha = if (p.isDark) 0.16f else 0.12f), shape)
            .clickable(role = if (chevron) Role.DropdownList else Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(start = if (leading != null) 10.dp else 14.dp, end = if (chevron) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = if (selected) fg else p.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chevron) {
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = if (selected) fg else p.accent, modifier = Modifier.size(18.dp).rotate(turn))
        }
    }
}
