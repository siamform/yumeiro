/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.sources.drama.YumeiroDrama
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Yumeiro Drama — the built-in short-drama source. One tap on, one tap off; no install. */
@Composable
internal fun DramaBuiltinCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val on by YumeiroDrama.enabled.collectAsState()
    fun toggle() {
        YumeiroDrama.setEnabled(context, !on)
        OnlineRepository.clearCaches()
        if (!on) AppMessages.show(context, "Yumeiro Drama is on — find it in Yumix", AppMessages.Kind.SUCCESS)
        else AppMessages.show(context, "Yumeiro Drama turned off", AppMessages.Kind.INFO)
    }
    Surface(
        shape = RoundedCornerShape(20.dp), color = GlassFill,
        modifier = modifier.fillMaxWidth().border(1.dp, SpotifyGreen.copy(alpha = if (on) 0.55f else 0.25f), RoundedCornerShape(20.dp)).testTag("market_builtin_drama"),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(SpotifyGreen, SpotifyGreen.copy(alpha = 0.45f)))),
                    contentAlignment = Alignment.Center,
                ) { Text("劇", color = OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(YumeiroDrama.NAME, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("Built in · Short drama · no install", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                }
                AnimatedContent(on, label = "drama_toggle") { enabled ->
                    if (enabled) OutlinedButton(onClick = ::toggle, shape = RoundedCornerShape(18.dp), modifier = Modifier.testTag("market_builtin_drama_toggle")) {
                        Icon(Icons.Default.Check, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("On", color = TextPrimary, fontWeight = FontWeight.Bold)
                    } else Button(
                        onClick = ::toggle, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.testTag("market_builtin_drama_toggle"),
                    ) { Text("Turn on", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "Vertical short dramas from DramaBox, ShortMax, NetShort, DramaWave, GoodShort and FlexTV in one place — shelves per studio, search across all of them, every episode in order.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reads a public drama index · no account · never bypasses paywalls or DRM", color = TextTertiary, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}
