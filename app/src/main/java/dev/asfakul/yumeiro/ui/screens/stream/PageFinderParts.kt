/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.TextSecondary

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
