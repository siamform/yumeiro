/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.feed

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

fun Modifier.feedCard(feed: String, key: String): Modifier = semantics { feedCardKey = "$feed|$key" }
