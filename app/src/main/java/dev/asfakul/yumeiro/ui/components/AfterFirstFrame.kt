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

/**
 * GATE-1-VIDEOS-LOWRAM: a tab page composes its content one frame after the page itself.
 *
 * A far tab jump (Home → Videos) fades the pager out, jumps and fades in. The jump frame used to
 * dispose Home's page and compose the whole Videos page (top bar change, Yumix feed) in one main-thread
 * message: 794 ms on api33-lowram and 697 ms on api36 (device run 44937485728), 1,251 ms on api36 in
 * 44937327148 — the gate's limit is 700 ms. The page is invisible at that moment (alpha 0), so its
 * content can arrive one frame later; the two halves run as separate messages and input is answered
 * between them (Android "Diagnose and fix ANRs": expensive frame → do less work per frame).
 * [enabled] = false composes [content] at once (fixed for the life of the call site).
 */
@Composable
fun NextFramePage(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; ready = true }
    if (ready) content()
}

/** Which tab pages wait a frame ([NextFramePage]): every page but the very first one shown after start. */
class NextFramePages(private val on: Boolean) {
    private var firstShown = false
    /** Called once per page composition; true when that page's content should wait a frame. */
    fun deferNext(): Boolean {
        if (!on) return false
        if (!firstShown) { firstShown = true; return false }
        return true
    }

    /**
     * NAV-1 (R19, field run 45134330301): Home's page is composed again when a page on top of it is closed
     * (arrow, Back key, swipe). That page is on screen at once, under the closing page's slide, so waiting a
     * frame left it empty: the "blank page underneath" for 1-4 frames (183-283 ms each on the CI emulator).
     * Called when the Home entry composes; the next page composition shows its content at once. Far tab
     * jumps (pager hidden at alpha 0) still wait a frame.
     */
    fun restoring() { firstShown = false }
}
