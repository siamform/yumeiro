/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * ST-18 (G12): composes [content] from the frame after the first one.
 *
 * The first Home frame on api33-lowram took 2.2–2.4 s (Perfetto, 43828509434 / 43863246577); inside
 * it the root also composed overlay hosts that show nothing at start (online "Opening…" layer,
 * YouTube loading page, browser check, explore tour, update and reminder prompts), loading and
 * verifying their code before Home could draw. Those hosts read app-wide state (StateFlows in
 * singletons), so composing them one frame later loses no request: a request made earlier is still
 * pending when they appear. Hosts whose absence changes behaviour (extension review and download
 * prompt count attached hosts; messages fall back to toasts) must not use this.
 * [enabled] = false composes [content] at once (fixed for the life of the call site).
 */
@Composable
fun AfterFirstFrame(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; ready = true }
    if (ready) content()
}
