/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * R16 Phase 3 evidence: the identity of a card in an endless feed (`feed|key`), as a semantics
 * property. Nothing reads it in the app; the device test `FeedPositionDeviceTest` reads it to
 * prove that a card on screen is never replaced by an older (cached) one while the user scrolls.
 * Invisible to TalkBack (a custom key, not a content description).
 */
val FeedCardKey = SemanticsPropertyKey<String>("FeedCardKey")
private var SemanticsPropertyReceiver.feedCardKey by FeedCardKey

/**
 * R16: where the card sits — the shelf, grid row or list it belongs to (a LazyColumn item key).
 * The same title may legitimately appear in two shelves; with the slot the device test can tell
 * "a second copy further down" from "this card jumped". Empty when the caller sets no slot.
 */
val FeedSlotKey = SemanticsPropertyKey<String>("FeedSlotKey")
private var SemanticsPropertyReceiver.feedSlotKey by FeedSlotKey

/** The slot (shelf / grid row) the cards below are composed in. */
val LocalFeedSlot: ProvidableCompositionLocal<String> = staticCompositionLocalOf { "" }

@Composable
fun Modifier.feedCard(feed: String, key: String): Modifier {
    val slot = LocalFeedSlot.current
    return semantics { feedCardKey = "$feed|$key"; if (slot.isNotEmpty()) feedSlotKey = slot }
}

/** Composes [content] with [slot] as the [LocalFeedSlot] of its feed cards. */
@Composable
fun FeedSlot(slot: String, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalFeedSlot provides slot, content = content)
