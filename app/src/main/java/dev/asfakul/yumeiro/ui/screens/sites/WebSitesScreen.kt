/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.settings.GuideCard
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamFinderDialog
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * Websites: sites the user saved to watch through Yumeiro's player. A site opens in the page
 * browser (ad / pop-up shield on); the stream its own player starts plays in Yumeiro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebSitesScreen(onBack: () -> Unit, only: SavedSites.Kind? = null) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { SavedSites.init(context) }
    val allSites by SavedSites.sites.collectAsState()
    // F-02 split: the Telegram page and the Websites page each show their own kind.
    val sites = remember(allSites, only) { SavedSites.ofKind(allSites, only) }
    val telegramPage = only == SavedSites.Kind.TELEGRAM
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<SavedSites.Site?>(null) }
    var renaming by remember { mutableStateOf<SavedSites.Site?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    // F-04: search (shown from 6 sites), pin, move, import / export.
    var query by rememberSaveable { mutableStateOf("") }
    var importing by rememberSaveable { mutableStateOf(false) }
    val shown = remember(sites, query) { SavedSites.filter(SavedSites.ordered(sites), query) }
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) { query = "" }

    fun add() {
        // F-02: "@name" or a t.me channel link saves the channel's public page (handled in SavedSites.add).
        val err = SavedSites.wrongPage(input, only) ?: SavedSites.add(context, input)
        if (err != null) error = err else { input = ""; error = null }
    }

    // F-02 (ADR-052): the signed-in account opens in place, like a channel.
    var account by rememberSaveable { mutableStateOf(false) }
    val accountEnabled = remember(telegramPage) { telegramPage && dev.asfakul.yumeiro.online.telegram.td.TgAccounts.enabled(context) }
    val accountUnavailable = remember(telegramPage) { if (telegramPage) dev.asfakul.yumeiro.online.telegram.td.TgAccounts.unavailableReason(context) else null }
    // Read off the main thread (SharedPreferences + profile id); refreshed each time the page comes back from the account.
    val signedIn by androidx.compose.runtime.produceState(false, accountEnabled, account) {
        if (accountEnabled) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.online.telegram.td.TgAccounts.signedInHint(context) }
    }
    if (account && accountEnabled) {
        TelegramAccountScreen(onBack = { account = false }, onPlay = { req -> IncomingMedia.open(context, req) })
        return
    }

    // F-02 (ADR-042): a saved public Telegram channel opens its video posts, not the page browser.
    val tgChannel = open?.let { site -> dev.asfakul.yumeiro.online.telegram.TelegramPublic.channelOf(site.url) }
        ?.takeIf { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.TELEGRAM_PUBLIC) }
    if (tgChannel != null) {
        val site = open!!
        TelegramChannelScreen(
            channel = tgChannel, name = site.name, onBack = { open = null },
            onPlay = { req -> IncomingMedia.open(context, req) },
            onSignIn = if (accountEnabled) ({ open = null; account = true }) else null,
        )
        return
    }

    // OL-1: websites open in the kept tab: playing a video, Downloads, Minimize or Back hide it, never close it.
    val tabs = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.WEBSITE_TAB) }
    val tab by WebsiteTab.tab.collectAsState()
    tab?.takeIf { tabs && it.visible }?.let { t ->
        PageStreamFinderDialog(
            pageUrl = t.keep.pageUrl,
            reason = "Browsing ${t.name}. Press play on any video; Yumeiro plays the stream in its own player.",
            base = null,
            keep = t.keep,
            onMinimize = { WebsiteTab.minimize(byPlay = false) },
            onPlay = { req -> WebsiteTab.minimize(byPlay = true); IncomingMedia.open(context, req.copy(title = req.title.ifBlank { t.name })) },
            onDismiss = { WebsiteTab.close() },
        )
    }

    open?.let { site ->
        PageStreamFinderDialog(
            pageUrl = site.url,
            reason = "Browsing ${site.name}. Press play on any video; Yumeiro plays the stream in its own player.",
            base = null,
            onPlay = { req -> open = null; IncomingMedia.open(context, req.copy(title = req.title.ifBlank { site.name })) },
            onDismiss = { open = null },
        )
    }

    if (help) TsukiDialog(
        onDismissRequest = { help = false },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { GuideCard(SetupGuides.websites, startExpanded = true) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { help = false }, modifier = Modifier.testTag("site_help_close")) { Text("Got it") } },
    )

    if (importing) {
        var text by rememberSaveable { mutableStateOf("") }
        TsukiDialog(
            onDismissRequest = { importing = false },
            title = { Text("Add websites from a list") },
            text = {
                Column {
                    Text("Paste links, one per line. Sites you already saved stay as they are.", fontSize = 13.sp)
                    OutlinedTextField(text, { text = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("site_import_field"))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val r = SavedSites.importText(context, text)
                    importing = false
                    dev.asfakul.yumeiro.ui.components.AppMessages.info(context, buildString {
                        append(if (r.added == 1) "1 website added" else "${r.added} websites added")
                        if (r.duplicates > 0) append(", ${r.duplicates} already saved")
                        if (r.bad > 0) append(", ${r.bad} not web addresses")
                        if (r.overLimit > 0) append(", ${r.overLimit} over the 200 limit")
                    })
                }, enabled = text.isNotBlank(), modifier = Modifier.testTag("site_import_confirm")) { Text("Add") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }

    renaming?.let { site ->
        var name by remember(site.url) { mutableStateOf(site.name) }
        TsukiDialog(
            onDismissRequest = { renaming = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename website") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("site_rename_field")) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { SavedSites.rename(context, site.url, name); renaming = null }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().testTag(if (telegramPage) "telegram_screen" else "websites_screen")) {
        SubPageHeader(
            title = if (telegramPage) "Telegram" else "Websites", onBack = onBack,
            subtitle = if (sites.isNotEmpty()) "${sites.size} saved" else if (telegramPage) "Public channels, no login" else "Play videos from any site",
        ) {
            IconButton(onClick = { importing = true }, modifier = Modifier.testTag("site_import")) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add websites from a list", tint = p.textSecondary) }
            if (sites.isNotEmpty()) IconButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, SavedSites.export(sites))
                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share website list").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.testTag("site_export")) { Icon(Icons.Default.Share, "Share website list", tint = p.textSecondary) }
            IconButton(onClick = { help = true }, modifier = Modifier.testTag("site_help")) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "How websites work", tint = p.textSecondary) }
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("websites"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // OL-1: the website kept in the background, one tap from where it was.
            tab?.takeIf { tabs && !it.visible }?.let { t ->
                item("website_tab_card") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surfaceContainerHigh)
                            .clickable { WebsiteTab.resume() }.heightIn(min = 56.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).testTag("website_tab_card"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Open tab · ${t.name}", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.keep.title.ifBlank { t.keep.address }, color = p.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.TextButton(onClick = { WebsiteTab.resume() }, modifier = Modifier.testTag("website_tab_resume")) { Text("Resume") }
                        IconButton(onClick = { WebsiteTab.close() }, modifier = Modifier.testTag("website_tab_close")) { Icon(Icons.Default.Close, "Close tab", tint = p.textSecondary) }
                    }
                }
            }
            // TG-PAGE-1: a clear way in. The card says whether this profile is signed in and has a real button;
            // when sign-in can't run on this phone it says why instead of disappearing.
            if (accountEnabled) item("tg_account_card") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surfaceContainerHigh)
                        .clickable(onClickLabel = if (signedIn) "Open your chats" else "Sign in to Telegram") { account = true }
                        .heightIn(min = 56.dp).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp).testTag("tg_account_card"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (signedIn) "Your Telegram account" else "Sign in to Telegram", color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (signedIn) "Signed in. Videos and audio from your chats, groups and channels, including private ones you have joined"
                            else "Watch videos from your chats, groups and the private channels you have joined. Telegram sends the code; Yumeiro never sees your password",
                            color = p.textSecondary, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.Button(
                        onClick = { account = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tg_account_button"),
                    ) { Text(if (signedIn) "Open" else "Sign in", fontWeight = FontWeight.SemiBold) }
                }
            } else if (telegramPage && accountUnavailable != null) item("tg_account_unavailable") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surfaceContainerHigh).padding(16.dp).testTag("tg_account_unavailable"),
                ) {
                    Text("Sign-in isn't available here", color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(accountUnavailable, color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            item("add") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surfaceContainerHigh)
                        .border(1.dp, p.textPrimary.copy(alpha = 0.06f), RoundedCornerShape(22.dp)).padding(14.dp),
                ) {
                    Text(when (only) { SavedSites.Kind.TELEGRAM -> "Add a public Telegram channel"; SavedSites.Kind.WEBSITE -> "Add a website"; null -> "Add a website or Telegram channel" }, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; error = null }, singleLine = true,
                            placeholder = { Text(if (telegramPage) "@channel or t.me/channel" else "example.com or @channel") }, isError = error != null,
                            leadingIcon = { Icon(Icons.Default.Public, null, tint = p.textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                            modifier = Modifier.weight(1f).testTag("site_add_field"),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(p.accent)
                                .combinedClickable(onClickLabel = if (telegramPage) "Add channel" else "Add website", onClick = { add() }).testTag("site_add"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, if (telegramPage) "Add channel" else "Add website", tint = p.onAccent) }
                    }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (sites.size >= 6) item("search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(if (telegramPage) "Search your channels" else "Search your websites") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = p.textSecondary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                    modifier = Modifier.fillMaxWidth().testTag("site_search"),
                )
            }
            if (sites.isNotEmpty() && shown.isEmpty()) item("no_match") {
                Text(if (telegramPage) "No saved channel matches \"$query\"" else "No saved website matches \"$query\"", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(8.dp).testTag("site_no_match"))
            }
            if (sites.isEmpty()) item("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent2.copy(alpha = 0.18f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (telegramPage) "No channels yet" else "No websites yet", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (telegramPage) "Add a public channel with @name or a t.me link. Its video posts play in Yumeiro." else "Add a site you watch on. Its videos play in Yumeiro.", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else items(shown, key = { it.url }) { site ->
                SiteRow(
                    site, onOpen = {
                        // OL-1: Telegram channels keep their own screen; every other site opens in the kept tab.
                        val tg = dev.asfakul.yumeiro.online.telegram.TelegramPublic.channelOf(site.url) != null &&
                            dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.TELEGRAM_PUBLIC)
                        if (tabs && !tg) WebsiteTab.open(context, site.url, site.name) else open = site
                    }, onRename = { renaming = site }, onRemove = { SavedSites.remove(context, site.url) },
                    onPin = { SavedSites.setPinned(context, site.url, !site.pinned) },
                    onMove = if (query.isBlank()) ({ up -> SavedSites.moveSite(context, site.url, up) }) else null,
                    modifier = Modifier.animateItem(),
                )
            }
            // The step-by-step guide lives behind the "?" in the header (not in the list) so the
            // saved sites stay the focus of the page.
            item("help_hint") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClickLabel = "How it works", onClick = { help = true })
                        .padding(vertical = 10.dp).testTag("site_help_hint"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = p.textTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("How it works", color = p.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteRow(
    site: SavedSites.Site, onOpen: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit,
    onPin: () -> Unit = {}, onMove: ((up: Boolean) -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceContainerHigh)
            .combinedClickable(onClickLabel = "Open ${site.name}", onLongClick = { menu = true }, onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp).testTag("site_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(site.name.take(1).uppercase(), color = p.accentBright, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            AsyncImage(
                model = "https://www.google.com/s2/favicons?domain=${site.host}&sz=64", contentDescription = null,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(site.name, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (site.pinned) "Pinned · ${site.host}" else site.host, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("site_menu")) { Icon(Icons.Default.MoreVert, "More for ${site.name}", tint = p.textSecondary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (site.pinned) "Unpin" else "Pin to top") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; onPin() }, modifier = Modifier.testTag("site_pin"))
                if (onMove != null) {
                    DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menu = false; onMove(true) }, modifier = Modifier.testTag("site_move_up"))
                    DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menu = false; onMove(false) }, modifier = Modifier.testTag("site_move_down"))
                }
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onRemove() }, modifier = Modifier.testTag("site_remove"))
            }
        }
    }
}
