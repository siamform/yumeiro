/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Spoken name for a clickable card: its non-blank parts joined with ", ". Set it on the clickable
 * node itself. A card's own Text children only name it while they are on screen; a card cut off
 * by the list edge or the bottom bar was read as an unnamed button (G12 X8, runs 43754318750
 * Music cards and 43760044391 Home → Continue).
 */
fun Modifier.a11yLabel(vararg parts: String?): Modifier = semantics {
    contentDescription = a11yLabelText(*parts)
}

/** The text [a11yLabel] sets; separate so it can be unit tested. */
fun a11yLabelText(vararg parts: String?): String =
    parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.joinToString(", ")
