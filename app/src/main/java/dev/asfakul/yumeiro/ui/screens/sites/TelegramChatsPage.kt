/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.telegram.td.TgChat
import dev.asfakul.yumeiro.online.telegram.td.TgMe
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * B8 (beta chapter): the Telegram chats page kept a "who is signed in" card and a search field fixed on top of the
 * list, eating a third of a small phone's screen. Both now live in the header as icons: a search icon that turns
 * the header into a search field (back or the close icon leaves search and clears it), and the account's own
 * avatar that opens a small account card with sign out. The chat kinds are chips that scroll away with the list.
 */
object TelegramChatFilter {
    enum class Kind(val label: String) { ALL("All"), CHANNELS("Channels"), GROUPS("Groups"), CHATS("Chats") }

    /** Chats matching [query] (title, case-insensitive) and [kind]. Pure. */
    fun apply(chats: List<TgChat>, query: String, kind: Kind): List<TgChat> {
        val q = query.trim()
        return chats.filter { c ->
            (q.isEmpty() || c.title.contains(q, ignoreCase = true)) && when (kind) {
                Kind.ALL -> true
                Kind.CHANNELS -> c.kind == TgChat.Kind.CHANNEL
                Kind.GROUPS -> c.kind == TgChat.Kind.GROUP
                Kind.CHATS -> c.kind == TgChat.Kind.PRIVATE
            }
        }
    }

    /** Kinds worth a chip: All plus each kind the list really has (no chip that can only show nothing). */
    fun kinds(chats: List<TgChat>): List<Kind> = buildList {
        add(Kind.ALL)
        if (chats.any { it.kind == TgChat.Kind.CHANNEL }) add(Kind.CHANNELS)
        if (chats.any { it.kind == TgChat.Kind.GROUP }) add(Kind.GROUPS)
        if (chats.any { it.kind == TgChat.Kind.PRIVATE }) add(Kind.CHATS)
    }
}

/**
 * A page header that holds its search behind an icon. Closed: title, subtitle, the search icon and [actions].
 * Open: a back arrow, the field (focused, keyboard up) and a clear icon. [onSubmit] runs on the keyboard's search
 * key (pages that search on the server); pages that filter as you type can leave it null.
 */
@Composable
fun TgSearchHeader(
    title: String,
    subtitle: String?,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit,
    onSubmit: ((String) -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val p = LocalMtPalette.current
    fun close() { onQuery(""); onSubmit?.invoke(""); onSearching(false) }
    // One back handler for the page: leave search first, then the page.
    BackHandler { if (searching) close() else onBack() }
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "tg_search_header",
    ) { open ->
        if (!open) {
            SubPageHeader(title = title, subtitle = subtitle, onBack = onBack, handleBack = false) {
                IconButton(onClick = { onSearching(true) }, modifier = Modifier.testTag("tg_search_open")) {
                    Icon(Icons.Default.Search, contentDescription = placeholder)
                }
                actions()
            }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { close() }, modifier = Modifier.testTag("tg_search_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close_search), tint = p.textPrimary)
                }
                TextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text(placeholder, maxLines = 1) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke(query) }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQuery(""); onSubmit?.invoke("") }, modifier = Modifier.testTag("tg_search_clear")) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = p.surfaceContainerHigh, unfocusedContainerColor = p.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = yuRound(24),
                    modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus).testTag("tg_search_field"),
                )
            }
        }
    }
}

/** B8: the signed-in chats page, without any TDLib (state in, events out) so it can be checked with plain data. */
@Composable
fun TelegramChatsPage(
    chats: List<TgChat>,
    me: Pair<TgMe, String?>?,
    loading: Boolean,
    error: String?,
    photo: suspend (Int) -> String?,
    onOpen: (TgChat) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalMtPalette.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(TelegramChatFilter.Kind.ALL.name) }
    var showMe by remember { mutableStateOf(false) }
    val kinds = remember(chats) { TelegramChatFilter.kinds(chats) }
    val kind = TelegramChatFilter.Kind.valueOf(kindName).takeIf { it in kinds } ?: TelegramChatFilter.Kind.ALL
    val shown = remember(chats, query, kind) { TelegramChatFilter.apply(chats, query, kind) }

    Column(Modifier.fillMaxSize().testTag("tg_chats_page")) {
        TgSearchHeader(
            title = tr(R.string.your_telegram, "Your Telegram"),
            subtitle = if (chats.isNotEmpty()) "${chats.size} chats" else "Chats",
            searching = searching, onSearching = { searching = it },
            query = query, onQuery = { query = it },
            placeholder = tr(R.string.find_a_chat, "Find a chat"), onBack = onBack,
        ) {
            IconButton(onClick = { showMe = true }, modifier = Modifier.testTag("tg_me_button").semantics { contentDescription = "Your account" }) {
                TelegramAvatar(me?.second, me?.first?.name.orEmpty().ifBlank { "Me" }, size = 30.dp)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("tg_chats"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (kinds.size > 2) item("kinds") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("tg_kinds")) {
                    items(kinds, key = { it.name }) { k ->
                        FilterChip(selected = k == kind, onClick = { kindName = k.name }, label = { Text(k.label) }, modifier = Modifier.testTag("tg_kind_${k.name.lowercase()}"))
                    }
                }
            }
            items(shown, key = { it.id }) { chat ->
                // TG-AVATAR-1 (owner list 8A): each chat with its own photo (TDLib's small copy, fetched as it scrolls in).
                val pic by produceState<String?>(null, chat.photoFileId) { value = photo(chat.photoFileId) }
                Row(
                    Modifier.fillMaxWidth().clip(yuRound(14)).background(p.surfaceContainerHigh)
                        .clickable { onOpen(chat) }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp).testTag("tg_chat_${chat.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TelegramAvatar(pic, chat.title, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title.ifBlank { tr(R.string.untitled_chat, "Untitled chat") }, color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when (chat.kind) { TgChat.Kind.CHANNEL -> "Channel"; TgChat.Kind.GROUP -> "Group"; TgChat.Kind.PRIVATE -> "Chat" }, color = p.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (!loading && shown.isEmpty()) item("empty") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        error ?: when {
                            query.isNotBlank() -> "No chat matches \"${query.trim()}\". Try part of the name."
                            kind != TelegramChatFilter.Kind.ALL -> "No ${kind.label.lowercase()} here."
                            else -> tr(R.string.no_chats_yet, "No chats yet.")
                        },
                        color = p.textSecondary, modifier = Modifier.testTag("tg_chats_empty"),
                    )
                    if (error != null) TextButton(onClick = onRetry, modifier = Modifier.testTag("tg_chats_retry")) { Text(stringResource(R.string.try_again)) }
                }
            }
            if (loading) item("loading") {
                dev.asfakul.yumeiro.ui.components.SkeletonList(rows = if (shown.isEmpty()) 7 else 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("tg_chats_loading")) // SKELETON-2
            }
        }
    }

    if (showMe) dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = { showMe = false },
        title = { Text(stringResource(R.string.your_account)) },
        text = {
            Column(Modifier.fillMaxWidth().testTag("tg_me"), horizontalAlignment = Alignment.CenterHorizontally) {
                TelegramAvatar(me?.second, me?.first?.name.orEmpty().ifBlank { "Me" }, size = 72.dp)
                Spacer(Modifier.size(12.dp))
                Text(me?.first?.name?.ifBlank { null } ?: "Signed in", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                me?.first?.username?.takeIf { it.isNotBlank() }?.let { Text("@$it", color = p.textSecondary, fontSize = 13.sp, maxLines = 1) }
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.your_login_stays_on_this_phone), color = p.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.size(16.dp))
                OutlinedButton(onClick = { showMe = false; onSignOut() }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_me_signout")) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMe = false }, modifier = Modifier.testTag("tg_me_close")) { Text(stringResource(R.string.close)) } },
    )
}
