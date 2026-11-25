/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/**
 * WEB-BAR-1 (owner list 5): the bottom bar of Yumeiro's browser (Websites page): Back, Forward, Reload,
 * Block element and Open in Chrome. Off for the stream finder used by links and extensions.
 */
@Composable
internal fun BrowserBar(
    canBack: Boolean, canForward: Boolean, picking: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onReload: () -> Unit, onBlock: () -> Unit, onOpenOutside: () -> Unit,
    accent: Color,
) {
    Row(
        Modifier.fillMaxWidth().background(TextPrimary.copy(alpha = 0.05f)).navigationBarsPadding().height(52.dp).testTag("browser_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canBack, modifier = Modifier.testTag("browser_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canBack) TextPrimary else TextTertiary)
        }
        IconButton(onClick = onForward, enabled = canForward, modifier = Modifier.testTag("browser_forward")) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canForward) TextPrimary else TextTertiary)
        }
        IconButton(onClick = onReload, modifier = Modifier.testTag("browser_reload")) { Icon(Icons.Default.Refresh, "Reload", tint = TextPrimary) }
        IconButton(onClick = onBlock, modifier = Modifier.testTag("browser_block")) {
            Icon(Icons.Default.Block, if (picking) "Cancel blocking" else "Block element", tint = if (picking) accent else TextPrimary)
        }
        IconButton(onClick = onOpenOutside, modifier = Modifier.testTag("browser_open_outside")) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in Chrome", tint = TextPrimary) }
    }
}

/** Opens [url] in Chrome when it is installed, else in the phone's browser. Only on the user's tap. */
internal object OutsideBrowser {
    const val CHROME = "com.android.chrome"

    fun intent(url: String, chrome: Boolean): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (chrome) setPackage(CHROME) }

    /** Returns false when nothing on the phone can open the link. */
    fun open(context: Context, url: String): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
        return runCatching { context.startActivity(intent(url, chrome = true)); true }.getOrNull()
            ?: runCatching { context.startActivity(intent(url, chrome = false)); true }.getOrDefault(false)
    }
}

/** A short note above the browser bar, with an optional action (Undo). */
internal data class BarNote(val text: String, val actionLabel: String?, val action: (() -> Unit)?)

@Composable
internal fun BrowserNote(note: BarNote, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(TextPrimary.copy(alpha = 0.08f)).padding(start = 16.dp, end = 4.dp).height(48.dp).testTag("browser_note"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(note.text, color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (note.actionLabel != null && note.action != null) TextButton(onClick = { note.action.invoke(); onDone() }, modifier = Modifier.testTag("browser_note_action")) {
            Text(note.actionLabel, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen, fontWeight = FontWeight.Bold)
        }
    }
}
