/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/** ADCHAIN-1: what the banner says. [kind] picks the icon (never colour alone). */
internal data class ChainBanner(val title: String, val line: String, val kind: Kind) {
    enum class Kind { WORKING, ASK, DONE, STOPPED }
}

/**
 * ADCHAIN-1 (owner list 23): a strip above the page while Yumeiro walks a link-shortener chain:
 * which step, what it waits for ("Waiting 6 s (the site's timer)", "Tick the box on the page"),
 * and a Stop button. It sits above the page, never on it, so the site's captcha and buttons stay visible.
 */
@Composable
internal fun LinkChainBanner(
    banner: ChainBanner,
    onStop: (() -> Unit)?,
    /** ADCHAIN-2: recovery when the walk stopped: one page back, or the same page in the phone's browser. */
    onBack: (() -> Unit)? = null,
    onOutside: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().background(TextPrimary.copy(alpha = 0.08f)).heightIn(min = 52.dp)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("link_chain_banner")
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${banner.title}. ${banner.line}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (banner.kind) {
            ChainBanner.Kind.WORKING -> CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            ChainBanner.Kind.ASK -> Icon(Icons.Default.TouchApp, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
            ChainBanner.Kind.DONE -> Icon(Icons.Default.CheckCircle, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
            ChainBanner.Kind.STOPPED -> Icon(Icons.Default.Warning, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(banner.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedContent(banner.line, label = "chainLine") {
                Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onStop != null) TextButton(onClick = onStop, modifier = Modifier.testTag("link_chain_stop")) {
            Text(stringResource(R.string.stop), color = SpotifyGreen, fontWeight = FontWeight.Bold)
        }
        if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.testTag("link_chain_back")) {
            Text(stringResource(R.string.back), color = SpotifyGreen, fontWeight = FontWeight.Bold)
        }
        if (onOutside != null) TextButton(onClick = onOutside, modifier = Modifier.testTag("link_chain_outside")) {
            Text(stringResource(R.string.browser), color = SpotifyGreen, fontWeight = FontWeight.Bold)
        }
    }
}
