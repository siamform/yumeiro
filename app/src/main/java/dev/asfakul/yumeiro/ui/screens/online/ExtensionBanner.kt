/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * The banner every Yumix extension gets: a wide backdrop taken from the extension's own first
 * poster, a dark scrim for legibility, the extension name, what the row is, and one clear action.
 * Without a poster it falls back to a quiet accent-toned panel (no fake art).
 */
@Composable
internal fun ExtensionBanner(
    name: String,
    title: String,
    count: Int,
    cover: OnlineItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    action: String = "Open",
) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(p.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "Open $name", onClick = onClick)
            .testTag("extension_banner_$name"),
    ) {
        if (cover?.posterUrl != null) {
            PosterImage(cover.posterUrl, cover.posterHeaders, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.28f), p.surfaceContainerHigh))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.86f), 0.62f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.15f))
            )
        )
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Extension, null, tint = p.onAccent, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Yumix source", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (count > 0) "$title · $count titles" else title,
                        color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(p.accent).padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(action, color = p.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = p.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** A horizontal strip of [ExtensionBanner]s, one per extension (Yumix mode, Home). */
@Composable
internal fun ExtensionBannerStrip(
    banners: List<Triple<String, String, List<OnlineItem>>>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth().testTag("extension_banner_strip"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(banners.size, key = { "b_${it}_" + banners[it].first }) { i ->
            val (name, title, items) = banners[i]
            ExtensionBanner(name, title, items.size, items.firstOrNull { it.posterUrl != null }, onClick = { onOpen(name) }, modifier = Modifier.width(300.dp))
        }
    }
}
