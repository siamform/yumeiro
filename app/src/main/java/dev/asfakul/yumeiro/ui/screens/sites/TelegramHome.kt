/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.telegram.td.TgMiniApp
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.components.AddLinkSheet
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.components.sheetDragHandle
import dev.asfakul.yumeiro.ui.screens.settings.GuideCard
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GROUP F: the Telegram page, on its own (it used to be a mode of the Websites screen).
 *
 * The header holds icons only: search, add, the user button and a menu (list import / export, how it works).
 * The list is the saved channels and nothing else. Everything about the account (who is signed in, your chats,
 * bot mini apps, sign out) sits behind the user button; with nobody signed in that button goes straight to sign-in.
 * A channel post that needs the account sends the user to sign-in and, once signed in, back to that channel.
 */
@Composable
fun TelegramHome(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { SavedSites.init(context) }
    val allSites by SavedSites.sites.collectAsState()
    val sites = remember(allSites) { SavedSites.ofKind(allSites, SavedSites.Kind.TELEGRAM) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var userSheet by rememberSaveable { mutableStateOf(false) }
    var openUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedSites.Site?>(null) }
    // The account screen (sign-in when signed out, your chats when signed in) and the channel to go back to after it.
    var account by rememberSaveable { mutableStateOf(false) }
    var backTo by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val shown = remember(sites, query) { SavedSites.filter(SavedSites.ordered(sites), query) }

    val accountEnabled = remember { TgAccounts.enabled(context) }
    val accountUnavailable = remember { TgAccounts.unavailableReason(context) }
    // Read off the main thread; refreshed every time the page comes back from the account screen.
    val signedIn by produceState(false, accountEnabled, account, refresh) {
        if (accountEnabled) value = withContext(Dispatchers.IO) { TgAccounts.signedInHint(context) }
    }
    val me by produceState<TgAccounts.MeHint?>(null, accountEnabled, account, signedIn) {
        value = if (accountEnabled && signedIn) withContext(Dispatchers.IO) { TgAccounts.meHint(context) } else null
    }

    if (account && accountEnabled) {
        TelegramAccountScreen(
            onBack = { account = false; backTo?.let { openUrl = it }; backTo = null },
            onPlay = { req -> IncomingMedia.open(context, req) },
        )
        return
    }

    val open = openUrl?.let { u -> allSites.firstOrNull { it.url == u } }
    val channel = open?.let { TelegramPublic.channelOf(it.url) }?.takeIf { FeatureFlags.on(context, FeatureFlags.Flag.TELEGRAM_PUBLIC) }
    if (open != null && channel != null) {
        TelegramChannelScreen(
            channel = channel, name = open.name, onBack = { openUrl = null },
            onPlay = { req -> IncomingMedia.open(context, req) },
            onSignIn = if (accountEnabled) ({ backTo = open.url; openUrl = null; account = true }) else null,
        )
        return
    }

    BackHandler(enabled = query.isNotEmpty() || searchOpen) { if (query.isNotEmpty()) query = "" else searchOpen = false }

    if (adding) AddLinkSheet(
        title = "Add a public Telegram channel",
        placeholder = "@channel or t.me/channel",
        hint = "Its video posts play in Yumeiro, no login needed.",
        tag = "tg_add",
        onDismiss = { adding = false },
        showRetry = false,
        onAdd = { typed ->
            val err = SavedSites.wrongPage(typed, SavedSites.Kind.TELEGRAM) ?: SavedSites.add(context, typed)
            if (err != null) AppMessages.error(context, err)
            err == null
        },
    )

    if (help) TsukiDialog(
        onDismissRequest = { help = false },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { GuideCard(SetupGuides.telegram, startExpanded = true) } },
        confirmButton = { TextButton(onClick = { help = false }, modifier = Modifier.testTag("site_help_close")) { Text("Got it") } },
    )

    if (importing) {
        var text by rememberSaveable { mutableStateOf("") }
        TsukiDialog(
            onDismissRequest = { importing = false },
            title = { Text("Add channels from a list") },
            text = {
                Column {
                    Text("Paste @names or t.me links, one per line. Channels you already saved stay as they are.", fontSize = 13.sp)
                    OutlinedTextField(text, { text = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("site_import_field"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = SavedSites.importText(context, text)
                    importing = false
                    AppMessages.info(context, TelegramHomeWords.imported(r.added, r.duplicates, r.bad, r.overLimit))
                }, enabled = text.isNotBlank(), modifier = Modifier.testTag("site_import_confirm")) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }

    renaming?.let { site ->
        var name by remember(site.url) { mutableStateOf(site.name) }
        TsukiDialog(
            onDismissRequest = { renaming = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename channel") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("site_rename_field")) },
            confirmButton = { TextButton(onClick = { SavedSites.rename(context, site.url, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    fun exportList() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, SavedSites.export(sites))
        runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share channel list").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The user button: signed out → sign-in at once; signed in (or sign-in unavailable) → the user sheet. */
    fun onUser() {
        if (accountEnabled && !signedIn) account = true else userSheet = true
    }

    if (userSheet) TelegramUserSheet(
        signedIn = signedIn, me = me, unavailable = accountUnavailable,
        onDismiss = { userSheet = false },
        onChats = { userSheet = false; account = true },
        onSignIn = { userSheet = false; account = true },
        onSignedOut = { userSheet = false; refresh++ },
    )

    Column(Modifier.fillMaxSize().testTag("telegram_screen")) {
        SubPageHeader(
            title = "Telegram", onBack = onBack,
            subtitle = if (sites.isNotEmpty()) "${sites.size} saved" else "Public channels, no login",
        ) {
            if (sites.isNotEmpty()) dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen, { searchOpen = !searchOpen; if (!searchOpen) query = "" }, "tg_home_search_toggle", "Search your channels")
            IconButton(onClick = { adding = true }, modifier = Modifier.testTag("tg_add_open")) { Icon(Icons.Default.Add, "Add channel", tint = p.textPrimary) }
            IconButton(
                onClick = { onUser() },
                modifier = Modifier.testTag("tg_user").semantics { stateDescription = if (signedIn) "Signed in" else "Not signed in" },
            ) {
                if (signedIn) TelegramAvatar(me?.photoPath, me?.name ?: "Telegram", size = 28.dp, modifier = Modifier.testTag("tg_user_avatar"))
                else Icon(Icons.Default.Person, if (accountEnabled) "Sign in to Telegram" else "Telegram account", tint = p.textSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("tg_more")) { Icon(Icons.Default.MoreVert, "More", tint = p.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add channels from a list") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }, onClick = { menu = false; importing = true }, modifier = Modifier.testTag("site_import"))
                    if (sites.isNotEmpty()) DropdownMenuItem(text = { Text("Share channel list") }, leadingIcon = { Icon(Icons.Default.FileUpload, null) }, onClick = { menu = false; exportList() }, modifier = Modifier.testTag("site_export"))
                    DropdownMenuItem(text = { Text("How Telegram works") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) }, onClick = { menu = false; help = true }, modifier = Modifier.testTag("site_help"))
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("tg_home_list"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("telegram"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searchOpen) item("search") {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search your channels") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = p.textSecondary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("tg_home_search"),
                )
            }
            if (sites.isNotEmpty() && shown.isEmpty()) item("no_match") {
                Text("No saved channel matches \"$query\"", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(8.dp).testTag("site_no_match"))
            }
            if (sites.isEmpty()) item("empty") {
                TelegramEmpty(
                    signInOffered = accountEnabled && !signedIn,
                    onAdd = { adding = true }, onSignIn = { account = true },
                )
            } else items(shown, key = { it.url }) { site ->
                SiteRow(
                    site, onOpen = { openUrl = site.url },
                    onRename = { renaming = site }, onRemove = { SavedSites.remove(context, site.url) },
                    onPin = { SavedSites.setPinned(context, site.url, !site.pinned) },
                    onMove = if (query.isBlank()) ({ up -> SavedSites.moveSite(context, site.url, up) }) else null,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** First visit: two clear ways in, a public channel or the user's own account. */
@Composable
private fun TelegramEmpty(signInOffered: Boolean, onAdd: () -> Unit, onSignIn: () -> Unit) {
    val p = LocalMtPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp).testTag("tg_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent2.copy(alpha = 0.18f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = p.accentBright, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(12.dp))
        Text("No channels yet", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            TelegramHomeWords.empty(signInOffered),
            color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                modifier = Modifier.heightIn(min = 48.dp).testTag("tg_empty_add"),
            ) { Text("Add a channel", fontWeight = FontWeight.SemiBold) }
            if (signInOffered) OutlinedButton(onClick = onSignIn, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_empty_sign_in")) {
                Text("Sign in", color = p.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** GROUP F: the user section (behind the user button): who is signed in, your chats, bot mini apps, sign out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TelegramUserSheet(
    signedIn: Boolean, me: TgAccounts.MeHint?, unavailable: String?,
    onDismiss: () -> Unit, onChats: () -> Unit, onSignIn: () -> Unit, onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bot by remember { mutableStateOf(TgMiniApp.consented(context)) }
    var confirmLogout by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    if (confirmLogout) TsukiDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Sign out of Telegram?") },
        text = { Text("Yumeiro forgets this login and its downloaded Telegram files. Your account itself is not changed.") },
        confirmButton = {
            TextButton(onClick = {
                confirmLogout = false; signingOut = true
                scope.launch {
                    val ok = runCatching { TgAccounts.session(context)?.logOut() == true }.getOrDefault(false)
                    signingOut = false
                    if (ok) { AppMessages.info(context, "Signed out of Telegram"); onSignedOut() }
                    else AppMessages.error(context, "Couldn't sign out. Check the connection and try again.")
                }
            }, modifier = Modifier.testTag("tg_logout_confirm")) { Text("Sign out") }
        },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("tg_user_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            when {
                unavailable != null -> {
                    Text("Sign-in isn't available here", color = p.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("tg_account_unavailable"))
                    Text(unavailable, color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                signedIn -> {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).semantics(mergeDescendants = true) {}.testTag("tg_user_me"), verticalAlignment = Alignment.CenterVertically) {
                        TelegramAvatar(me?.photoPath, me?.name ?: "Telegram", size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(me?.name ?: "Your Telegram account", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Signed in", color = p.textSecondary, fontSize = 12.sp)
                        }
                    }
                    SettingLink(Icons.AutoMirrored.Filled.Chat, "Your chats", "Videos and audio from your chats, groups and the private channels you joined", "tg_user_chats") { onChats() }
                    SettingSwitch("Bot mini apps", "DiskWala links open through a Telegram downloader bot (third party) with this account", bot, "tg_user_bot") {
                        bot = it; TgMiniApp.setConsented(context, it)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.textPrimary.copy(alpha = 0.08f))
                    SettingLink(Icons.AutoMirrored.Filled.Logout, if (signingOut) "Signing out…" else "Sign out", "Yumeiro forgets this login on this phone", "tg_user_signout") {
                        if (!signingOut) confirmLogout = true
                    }
                }
                else -> {
                    Text("Your Telegram account", color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(TelegramHomeWords.SIGN_IN_LINE, color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    SettingLink(Icons.AutoMirrored.Filled.Login, "Sign in", "Telegram sends the code; Yumeiro never sees your password", "tg_user_sign_in") { onSignIn() }
                }
            }
        }
    }
}

/** Words of the Telegram page (pure → unit-tested). */
internal object TelegramHomeWords {
    const val SIGN_IN_LINE = "Watch videos from your chats, groups and the private channels you have joined, and the big videos public channels keep for signed-in users."

    fun empty(signInOffered: Boolean) =
        "Add a public channel with @name or a t.me link. Its video posts play in Yumeiro." +
            if (signInOffered) " Or sign in to watch your own chats." else ""

    fun imported(added: Int, duplicates: Int, bad: Int, overLimit: Int) = buildString {
        append(if (added == 1) "1 channel added" else "$added channels added")
        if (duplicates > 0) append(", $duplicates already saved")
        if (bad > 0) append(", $bad not channel links")
        if (overLimit > 0) append(", $overLimit over the 200 limit")
    }
}
