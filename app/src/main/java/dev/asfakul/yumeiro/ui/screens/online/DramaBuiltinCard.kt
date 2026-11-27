/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * OWN-13: the one built-in source, "Yumeiro" (short dramas + public-domain films, documentaries, cartoons, TV and
 * films by country). One card, one switch, nothing to install.
 */
@Composable
internal fun YumeiroHubCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val on by dev.asfakul.yumeiro.sources.hub.YumeiroHub.enabled.collectAsState()
    BuiltinSourceCard(
        name = dev.asfakul.yumeiro.sources.hub.YumeiroHub.NAME, glyph = "夢", kind = "Official · all in one · no install", on = on, tag = "market_builtin_yumeiro",
        about = "Everything Yumeiro brings by itself in one source: short dramas from DramaBox, ShortMax, NetShort and more, plus classic films, " +
            "film noir, sci-fi, silent films, documentaries, cartoons, old TV and films by country (India, France, Germany, Italy, Japan, Russia and more) " +
            "that are free to watch, and free anime episodes from the official channels of Muse Asia, Ani-One Asia, Gundam, VIZ Media, RetroCrush and Tezuka Productions. " +
            "One home, one search, one switch.",
        note = "Legal sources only: public drama index, the Internet Archive and official distributor channels · no account · never bypasses paywalls, DRM or region limits · no 18+",
        modifier = modifier,
    ) { dev.asfakul.yumeiro.sources.hub.YumeiroHub.setEnabled(context, !on) }
}

/** OWN-13: the official source cards for a list; with the hub on, one "Yumeiro" card stands for both parts. */
internal fun officialHub(): Boolean = dev.asfakul.yumeiro.sources.hub.YumeiroHub.on()

/** Yumeiro Drama — the built-in short-drama source. One tap on, one tap off; no install. */
@Composable
internal fun DramaBuiltinCard(modifier: Modifier = Modifier) {
    if (officialHub()) { YumeiroHubCard(modifier); return }
    val context = LocalContext.current
    val on by YumeiroDrama.enabled.collectAsState()
    BuiltinSourceCard(
        name = YumeiroDrama.NAME, glyph = "劇", kind = "Built in · Short drama · no install", on = on, tag = "market_builtin_drama",
        about = "Vertical short dramas from DramaBox, ShortMax, NetShort, DramaWave, GoodShort and FlexTV in one place — shelves per studio, search across all of them, every episode in order.",
        note = "Reads a public drama index · no account · never bypasses paywalls or DRM",
        modifier = modifier,
    ) { YumeiroDrama.setEnabled(context, !on) }
}

/** OWN-9: Yumeiro Classics — the official built-in movie source (Internet Archive public domain). */
@Composable
internal fun ClassicsBuiltinCard(modifier: Modifier = Modifier) {
    if (officialHub()) { YumeiroHubCard(modifier); return }
    val context = LocalContext.current
    val on by dev.asfakul.yumeiro.sources.classics.YumeiroClassics.enabled.collectAsState()
    BuiltinSourceCard(
        name = dev.asfakul.yumeiro.sources.classics.YumeiroClassics.NAME, glyph = "映", kind = "Official · Movies, cartoons, TV · no install", on = on,
        tag = "market_builtin_classics",
        about = "Thousands of classic films, film noir, sci-fi, silent films, cartoons and old TV shows that are free to watch and download, from the Internet Archive.",
        note = "Public domain and freely licensed only · no account · no 18+",
        modifier = modifier,
    ) { dev.asfakul.yumeiro.sources.classics.YumeiroClassics.setEnabled(context, !on) }
}

@Composable
private fun BuiltinSourceCard(
    name: String, glyph: String, kind: String, on: Boolean, tag: String, about: String, note: String,
    modifier: Modifier = Modifier, setOn: () -> Unit,
) {
    val context = LocalContext.current
    fun toggle() {
        setOn()
        OnlineRepository.clearCaches()
        if (!on) AppMessages.show(context, "$name is on — find it in Yumix", AppMessages.Kind.SUCCESS)
        else AppMessages.show(context, "$name turned off", AppMessages.Kind.INFO)
    }
    Surface(
        shape = yuRound(20), color = GlassFill,
        modifier = modifier.fillMaxWidth().border(1.dp, SpotifyGreen.copy(alpha = if (on) 0.55f else 0.25f), yuRound(20)).testTag(tag),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(yuRound(14))
                        .background(Brush.linearGradient(listOf(SpotifyGreen, SpotifyGreen.copy(alpha = 0.45f)))),
                    contentAlignment = Alignment.Center,
                ) { Text(glyph, color = OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(kind, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                }
                AnimatedContent(on, label = "${tag}_toggle") { enabled ->
                    if (enabled) OutlinedButton(onClick = ::toggle, shape = yuRound(18), modifier = Modifier.testTag("${tag}_toggle")) {
                        Icon(Icons.Default.Check, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.on), color = TextPrimary, fontWeight = FontWeight.Bold)
                    } else Button(
                        onClick = ::toggle, shape = yuRound(18),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.testTag("${tag}_toggle"),
                    ) { Text(stringResource(R.string.turn_on), fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Text(about, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(note, color = TextTertiary, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}
