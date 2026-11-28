/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen

/**
 * B23 (ADR-B23-5): the episode's own extension has no source, and the Anticipation Engine prepared one from another
 * source. Shown where the "finding a source" status would be, never over a playing video. Play alternative is focused
 * first (TV remotes land on it); Skip and Never show again sit next to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlternativeOfferCard(state: OnlineSession.State, offer: OnlineSession.Offer, compact: Boolean) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(offer) { runCatching { focus.requestFocus() } }
    Column(Modifier.padding(horizontal = if (compact) 16.dp else 4.dp).testTag("anticipation_offer"), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.SwapHoriz, null, tint = SpotifyGreen, modifier = Modifier.size(if (compact) 26.dp else 32.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.an_offer_title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.an_offer_body, offer.own, offer.via),
            color = Color.White.copy(alpha = 0.75f), fontSize = if (compact) 11.sp else 13.sp, textAlign = TextAlign.Center,
            maxLines = 3, modifier = Modifier.testTag("anticipation_offer_text"),
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { OnlineSession.acceptOffer(context) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                modifier = Modifier.focusRequester(focus).testTag("anticipation_play_alternative"),
            ) { Text(stringResource(R.string.an_play_alternative)) }
            OutlinedButton(onClick = { OnlineSession.skipOffer(context) }, modifier = Modifier.testTag("anticipation_skip")) {
                Text(stringResource(R.string.an_skip), color = Color.White)
            }
            TextButton(onClick = { OnlineSession.neverAskOffer(context) }, modifier = Modifier.testTag("anticipation_never")) {
                Text(stringResource(R.string.an_never_show), color = Color.White.copy(alpha = 0.8f))
            }
        }
        if (state.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(state.title, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
        }
    }
}
