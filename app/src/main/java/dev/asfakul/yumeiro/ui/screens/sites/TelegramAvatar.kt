/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * TG-ME-1 / TG-AVATAR-1 (owner list 8A): a round Telegram picture (the user, a chat or a public channel). [model] is a
 * local file path (TDLib download) or an https URL (a channel's public preview); until it loads, or when there is
 * none, the first letter of [title] on the accent tint, the way Telegram itself shows chats without a photo.
 */
@Composable
fun TelegramAvatar(model: Any?, title: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.32f), p.accent2.copy(alpha = 0.20f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialOf(title), color = p.accentBright, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.40f).sp)
        val m = when (model) { is String -> model.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else java.io.File(it) }; else -> model }
        if (m != null) AsyncImage(model = m, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** First letter or digit of a name ("@movies_hd" -> "M"); "?" when there is none. */
internal fun initialOf(title: String): String =
    title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
