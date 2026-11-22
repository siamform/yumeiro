/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** OL-8: how long the website helper banner stays before it hides by itself. */
internal const val PAGE_HINT_AUTO_HIDE_MS = 3_000L

/**
 * The helper banner over a website page ("Browsing …, press play …"). OL-8: it hides by itself
 * [PAGE_HINT_AUTO_HIDE_MS] after it shows (a new [text], e.g. a sign-in note, shows it again for the
 * same time); a tap still hides it at once.
 */
@Composable
internal fun PageFinderHint(text: String, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val hide = rememberUpdatedState(onHide)
    if (FeatureFlags.isOn(FeatureFlags.Flag.PAGE_HINT_AUTOHIDE)) {
        LaunchedEffect(text) {
            kotlinx.coroutines.delay(PAGE_HINT_AUTO_HIDE_MS)
            hide.value()
        }
    }
    Surface(color = GlassFill, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth().padding(8.dp).clickable { hide.value() }.testTag("page_finder_hint")) {
        Text(
            text + "\n\nStart playback on the page; detected streams appear below. Tap to hide.",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(10.dp),
        )
    }
}

/** OL-2: how the website "Detected streams" panel is shown. */
internal enum class StreamPanel { OPEN, COLLAPSED, CLOSED }

/**
 * The website "Detected streams" panel under the page. OL-2: Rescan (clears the list and asks the page
 * again), a down arrow that collapses it to its header (up arrow opens it), and Close, which leaves a
 * small "Streams" pill so it can always come back. Flag [FeatureFlags.Flag.STREAM_PANEL_CONTROLS]
 * off keeps the old always-open panel without buttons.
 */
@Composable
internal fun DetectedStreamsPanel(
    found: List<CapturedStream>,
    drmNote: String?,
    state: StreamPanel,
    onState: (StreamPanel) -> Unit,
    onRescan: () -> Unit,
    onPlay: (CapturedStream) -> Unit,
    modifier: Modifier = Modifier,
    /** OL-3: title of the video playing now (null = not shown). */
    nowTitle: String? = null,
) {
    val controls = FeatureFlags.isOn(FeatureFlags.Flag.STREAM_PANEL_CONTROLS)
    val shown = if (controls) state else StreamPanel.OPEN
    if (shown == StreamPanel.CLOSED) {
        Box(modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier.background(GlassFill, RoundedCornerShape(18.dp)).clickable { onState(StreamPanel.OPEN) }
                    .padding(horizontal = 14.dp, vertical = 8.dp).testTag("page_finder_streams_pill"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Text(if (found.isEmpty()) "Streams" else "Streams · ${found.size}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        return
    }
    Surface(color = DarkSurface, modifier = modifier.fillMaxWidth().testTag("page_finder_streams_panel")) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (found.isEmpty()) "No stream detected yet — start the video on the page." else "Detected streams",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                    // OL-3: which video these streams belong to.
                    if (found.isNotEmpty() && !nowTitle.isNullOrBlank()) {
                        Text("Now playing: $nowTitle", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("page_finder_now_title"))
                    }
                }
                if (controls) {
                    IconButton(onClick = onRescan, modifier = Modifier.testTag("page_finder_streams_reload")) { Icon(Icons.Default.Refresh, "Rescan streams", tint = TextSecondary) }
                    if (shown == StreamPanel.OPEN) {
                        IconButton(onClick = { onState(StreamPanel.COLLAPSED) }, modifier = Modifier.testTag("page_finder_streams_collapse")) { Icon(Icons.Default.KeyboardArrowDown, "Minimize streams", tint = TextSecondary) }
                    } else {
                        IconButton(onClick = { onState(StreamPanel.OPEN) }, modifier = Modifier.testTag("page_finder_streams_expand")) { Icon(Icons.Default.KeyboardArrowUp, "Show streams", tint = TextSecondary) }
                    }
                    IconButton(onClick = { onState(StreamPanel.CLOSED) }, modifier = Modifier.testTag("page_finder_streams_close")) { Icon(Icons.Default.Close, "Close streams", tint = TextSecondary) }
                }
            }
            if (shown == StreamPanel.OPEN) {
                drmNote?.let { Text(it, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp)) }
                LazyColumn(Modifier.heightIn(max = 180.dp).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.url }) { c -> DetectedStreamRow(c, onPlay) }
                }
            }
        }
    }
}

@Composable
private fun DetectedStreamRow(c: CapturedStream, onPlay: (CapturedStream) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(GlassFill, RoundedCornerShape(12.dp)).clickable { onPlay(c) }.padding(10.dp).testTag("page_finder_stream"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(c.url, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(34.dp).background(SpotifyGreen, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "Play", tint = OnAccent, modifier = Modifier.size(20.dp))
        }
    }
}
