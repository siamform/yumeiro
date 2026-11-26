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
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * WEB-BAR-1 + GROUP E2: the bottom bar of Yumeiro's browser (Websites page): Back, Forward, the open tabs (with
 * their count) and the page menu (Reload, this page's ad filter, Hide an element, Manage ads, Open in Chrome,
 * Close tab). Off for the stream finder used by links and extensions.
 */
@Composable
internal fun BrowserBar(
    canBack: Boolean, canForward: Boolean, picking: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onReload: () -> Unit, onBlock: () -> Unit, onOpenOutside: () -> Unit,
    accent: Color,
    filterOn: Boolean = true, onFilter: (() -> Unit)? = null, onManageAds: (() -> Unit)? = null,
    tabCount: Int = 0, onTabs: (() -> Unit)? = null, onCloseTab: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
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
        if (picking) IconButton(onClick = onBlock, modifier = Modifier.testTag("browser_block_stop")) { Icon(Icons.Default.Block, "Stop hiding parts", tint = accent) }
        if (onTabs != null) IconButton(onClick = onTabs, modifier = Modifier.testTag("browser_tabs").semantics { stateDescription = if (tabCount == 1) "1 tab" else "$tabCount tabs" }) {
            // The tab count in a rounded square, like other browsers' tab button.
            Box(Modifier.size(22.dp).border(1.6.dp, TextPrimary, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text(if (tabCount > 9) "9+" else tabCount.toString(), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("browser_menu")) { Icon(Icons.Default.MoreVert, "Page menu", tint = TextPrimary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.testTag("browser_menu_list")) {
                // GROUP E2 (owner: "reload, back, forward, tabs, ad controls"): a Chrome-style icon row on top of the menu.
                Row(Modifier.padding(horizontal = 8.dp).testTag("browser_menu_nav"), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { menu = false; onBack() }, enabled = canBack, modifier = Modifier.testTag("browser_menu_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canBack) TextPrimary else TextTertiary)
                    }
                    IconButton(onClick = { menu = false; onForward() }, enabled = canForward, modifier = Modifier.testTag("browser_menu_forward")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canForward) TextPrimary else TextTertiary)
                    }
                    IconButton(onClick = { menu = false; onReload() }, modifier = Modifier.testTag("browser_menu_reload")) { Icon(Icons.Default.Refresh, "Reload", tint = TextPrimary) }
                }
                DropdownMenuItem(text = { Text("Reload") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onReload() }, modifier = Modifier.testTag("browser_reload"))
                if (onTabs != null) DropdownMenuItem(
                    text = { Text(if (tabCount == 1) "Tabs (1 open)" else "Tabs ($tabCount open)") },
                    leadingIcon = { Icon(Icons.Default.Tab, null) },
                    onClick = { menu = false; onTabs() }, modifier = Modifier.testTag("browser_menu_tabs"),
                )
                if (onFilter != null) DropdownMenuItem(
                    text = { Text(if (filterOn) "Ad filter on for this page" else "Ad filter off for this page") },
                    leadingIcon = { Icon(if (filterOn) Icons.Default.Shield else Icons.Default.RemoveModerator, null, tint = if (filterOn) accent else TextTertiary) },
                    trailingIcon = { Switch(filterOn, null) },
                    onClick = { menu = false; onFilter() }, modifier = Modifier.testTag("browser_menu_filter").semantics { stateDescription = if (filterOn) "On" else "Off" },
                )
                DropdownMenuItem(text = { Text(if (picking) "Stop hiding parts" else "Hide an element") }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menu = false; onBlock() }, modifier = Modifier.testTag("browser_block"))
                if (onManageAds != null) DropdownMenuItem(text = { Text("Manage ads") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { menu = false; onManageAds() }, modifier = Modifier.testTag("browser_manage_ads"))
                DropdownMenuItem(text = { Text("Open in Chrome") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = { menu = false; onOpenOutside() }, modifier = Modifier.testTag("browser_open_outside"))
                if (onCloseTab != null) DropdownMenuItem(text = { Text("Close tab") }, leadingIcon = { Icon(Icons.Default.Close, null) }, onClick = { menu = false; onCloseTab() }, modifier = Modifier.testTag("browser_close_tab"))
            }
        }
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
