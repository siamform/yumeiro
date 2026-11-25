/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.online.telegram.TelegramSeen
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * F-02 (ADR-042): a saved public Telegram channel. Lists the channel's video posts from Telegram's
 * web preview (newest first) under the channel's own header (photo, subscribers, description),
 * loads older posts on its own when the end of the list is reached (the "Older posts" button stays
 * for remotes and screen readers), marks posts that are new since the last visit, searches with
 * Telegram's own search and plays a post in Yumeiro's player.
 *
 * GROUP F (owner items 4 and 6): a post whose video Telegram keeps out of its web preview plays with the user's own
 * signed-in account ([TelegramAccountPlay]); without one, the card asks the user to sign in here. "Open in Telegram"
 * is offered only where sign-in can't run on this phone.
 */
/** At most this many older pages load on their own per opening (about 20 posts each); then the button. */
private const val AUTO_PAGES_MAX = 5

@Composable
fun TelegramChannelScreen(channel: String, name: String, onBack: () -> Unit, onPlay: (StreamRequest) -> Unit, onSignIn: (() -> Unit)? = null) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val scope = rememberCoroutineScope()
    val posts = remember(channel) { mutableStateListOf<TelegramPublic.VideoPost>() }
    var title by remember(channel) { mutableStateOf(name) }
    var header by remember(channel) { mutableStateOf<TelegramPublic.Page?>(null) }
    var before by remember(channel) { mutableStateOf<Int?>(null) }
    var loading by remember(channel) { mutableStateOf(true) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    var note by remember(channel) { mutableStateOf<String?>(null) }
    var appOnly by remember(channel) { mutableStateOf<TelegramPublic.VideoPost?>(null) }
    var autoOlder by remember(channel) { mutableStateOf(true) }
    // Automatic pages per opening or search; a channel with few videos never pages through its whole history unasked.
    var autoPages by remember(channel) { mutableStateOf(0) }
    var input by androidx.compose.runtime.saveable.rememberSaveable(channel) { mutableStateOf("") }
    var query by remember(channel) { mutableStateOf("") }
    var job by remember(channel) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // What was newest at the last visit, read once per opening (so marks stay while the screen is open).
    val lastSeen = remember(channel) { TelegramSeen.get(context, channel) }
    val newIds = TelegramSeen.newIds(lastSeen, posts.map { it.id })
    val withheld = posts.count { !it.playable }
    // Read off the main thread; refreshed when the screen opens again after a sign-in.
    val signedIn by androidx.compose.runtime.produceState(false, channel) {
        value = withContext(Dispatchers.IO) {
            dev.asfakul.yumeiro.online.telegram.td.TgAccounts.enabled(context) && dev.asfakul.yumeiro.online.telegram.td.TgAccounts.signedInHint(context)
        }
    }
    var appOnlyText by remember(channel) { mutableStateOf(dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Words.NEEDS_SIGN_IN) }
    var opening by remember(channel) { mutableStateOf<Int?>(null) }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable(channel) { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun load(older: Int?) {
        loading = true
        error = null
        val q = query
        job?.cancel()
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { TelegramPublic.fetchChannel(NetworkClient.get(context), channel, older, query = q) }
            loading = false
            when (r) {
                is TelegramPublic.Result.Failed -> {
                    // A failed automatic page stops the automatic loading; the button stays.
                    if (older != null) autoOlder = false
                    if (posts.isEmpty()) error = r.message else note = r.message
                }
                is TelegramPublic.Result.Ok -> {
                    if (older == null) {
                        title = r.value.title.ifBlank { name }
                        if (q.isEmpty()) {
                            header = r.value
                            val photo = r.value.avatarUrl.orEmpty()
                            scope.launch(Dispatchers.IO) { dev.asfakul.yumeiro.online.telegram.TelegramAvatars.remember(context, channel, photo) }
                            r.value.posts.maxOfOrNull { it.id }?.let { TelegramSeen.mark(context, channel, it) }
                        }
                    }
                    // The page lists oldest first; the screen shows newest first, older pages below.
                    val known = posts.map { it.id }.toHashSet()
                    posts.addAll(r.value.posts.filter { it.id !in known }.sortedByDescending { it.id })
                    before = r.value.before
                    if (posts.isEmpty()) {
                        error = (if (q.isNotBlank()) "No video posts match \"$q\" in @$channel." else "No video posts in the latest posts of @$channel.") +
                            (if (r.value.before != null) " Try older posts." else "")
                    }
                }
            }
        }
    }

    LaunchedEffect(channel) { load(null) }

    // Older posts load when the last rows come into view (once per page; never while loading).
    LaunchedEffect(list, channel) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) ->
                val b = before
                if (autoOlder && autoPages < AUTO_PAGES_MAX && !loading && b != null && posts.isNotEmpty() && last != null && last >= total - 2) {
                    autoPages++
                    load(b)
                }
            }
    }

    fun search(text: String) {
        if (text.trim() == query) return
        query = text.trim()
        posts.clear(); before = null; note = null; appOnly = null; autoOlder = true; autoPages = 0
        load(null)
    }

    fun openInTelegram(post: TelegramPublic.VideoPost) {
        val ok = runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(post.link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) note = "No app on this device can open Telegram links. The post is ${post.link}"
    }

    /** A withheld post: play it with the account, or say what is needed. */
    fun playWithAccount(post: TelegramPublic.VideoPost) {
        if (opening != null) return
        opening = post.id
        scope.launch {
            val title = post.text.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: "@$channel · ${post.id}"
            val r = dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.request(context, channel, post.id, title, post.thumbUrl)
            opening = null
            when (r) {
                is dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Result.Ok -> { appOnly = null; onPlay(r.request) }
                dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Result.NeedsSignIn -> {
                    appOnlyText = dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Words.NEEDS_SIGN_IN; appOnly = post
                }
                is dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Result.Unavailable -> { appOnlyText = TelegramPublic.Messages.APP_ONLY + " " + r.reason; appOnly = post }
                is dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Result.Failed -> { appOnlyText = r.message; appOnly = post }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty() || searchOpen) { if (query.isNotEmpty()) { input = ""; search("") } else searchOpen = false }

    Column(Modifier.fillMaxSize().background(p.background).testTag("tg_channel")) {
        SubPageHeader(
            title = title,
            subtitle = "@$channel · public posts" + if (withheld > 0) " · " + dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Words.withheld(withheld, signedIn) else "",
            onBack = onBack,
        ) {
            // GROUP F: search is an icon that opens the box (Group C pattern), not a field that is always there.
            dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen, {
                searchOpen = !searchOpen
                if (!searchOpen && query.isNotEmpty()) { input = ""; search("") }
            }, "tg_search_toggle", "Search this channel")
        }
        if (searchOpen) androidx.compose.material3.OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            placeholder = { Text("Search this channel") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search(input) }),
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = { search(input) }, modifier = Modifier.testTag("tg_search_go")) {
                    androidx.compose.material3.Icon(Icons.Default.Search, "Search")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("tg_search"),
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag("tg_list"),
            state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            header?.takeIf { query.isEmpty() }?.let { h -> item(key = "header") { TelegramChannelHeader(h) } }
            appOnly?.let { post ->
                item(key = "app_only") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.10f)).padding(12.dp).testTag("tg_app_only")) {
                        Text(appOnlyText, color = p.textPrimary, fontSize = 13.sp, modifier = Modifier.testTag("tg_app_only_text"))
                        Row {
                            if (onSignIn != null) {
                                androidx.compose.material3.Button(
                                    onClick = onSignIn,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("tg_sign_in_play"),
                                ) { Text(if (signedIn) "Open my account" else "Sign in to play") }
                            } else {
                                TextButton(onClick = { openInTelegram(post) }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_open_app")) { Text("Open in Telegram") }
                            }
                            TextButton(onClick = { appOnly = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_app_only_close")) { Text("Close") }
                        }
                    }
                }
            }
            note?.let { n -> item(key = "note") { Text(n, color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.testTag("tg_note")) } }
            items(posts, key = { it.id }) { post ->
                TelegramPostRow(post, isNew = post.id in newIds, signedIn = signedIn, opening = opening == post.id) {
                    if (!post.playable) { playWithAccount(post); return@TelegramPostRow }
                    // UP-NEXT-1: "Up next" on the watch page is this channel's other videos.
                    dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.setQueue("@$channel", posts.filter { it.playable }.map { tgRequest(it, channel) })
                    onPlay(tgRequest(post, channel))
                }
            }
            if (error != null && posts.isEmpty()) item(key = "error") {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.testTag("tg_error"))
                    TextButton(onClick = { load(before) }, modifier = Modifier.testTag("tg_retry")) { Text(if (before != null) "Older posts" else "Try again") }
                    // TG-PAGE-1: no public preview → the way in is the user's own account (members only).
                    if (onSignIn != null && before == null) TextButton(onClick = onSignIn, modifier = Modifier.testTag("tg_error_account")) { Text("Open my Telegram account") }
                }
            }
            if (loading) item(key = "loading") {
                // SKELETON-2: post-shaped placeholders (a page of them first, one while older posts load).
                dev.asfakul.yumeiro.ui.components.SkeletonList(rows = if (posts.isEmpty()) 5 else 1, modifier = Modifier.padding(vertical = 8.dp).testTag("tg_loading"))
            } else if (before != null && posts.isNotEmpty()) item(key = "older") {
                TextButton(onClick = { autoOlder = true; autoPages = 0; load(before) }, modifier = Modifier.fillMaxWidth().testTag("tg_older")) { Text("Older posts") }
            }
        }
    }
}

/** The player request for a playable public post (its file address is temporary; TelegramPlayback renews it). */
internal fun tgRequest(post: TelegramPublic.VideoPost, channel: String) = StreamRequest(
    url = post.videoUrl!!,
    title = post.text.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: "@$channel · ${post.id}",
    format = StreamFormat.PROGRESSIVE,
    referer = "https://t.me/",
    pageUrl = post.link,
    logoUrl = post.thumbUrl,
    group = StreamResolver.TELEGRAM_GROUP,
)

@Composable
private fun TelegramChannelHeader(page: TelegramPublic.Page) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag("tg_header"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(page.title.take(1).uppercase(), color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            page.avatarUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            page.subscribers?.let { Text("$it subscribers", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.testTag("tg_subscribers")) }
            page.description?.let { Text(it, color = p.textTertiary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TelegramPostRow(post: TelegramPublic.VideoPost, isNew: Boolean, signedIn: Boolean, opening: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val label = post.text.lineSequence().firstOrNull()?.ifBlank { null } ?: "Video ${post.id}"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceContainerHigh)
            .clickable(onClickLabel = if (post.playable || signedIn) "Play" else "Sign in to play", role = Role.Button, onClick = onClick)
            .padding(10.dp).testTag("tg_post_${post.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(10.dp)).background(p.accent.copy(alpha = 0.14f))) {
            post.thumbUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            post.duration?.let {
                Text(it, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (isNew) {
                Text("New", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.testTag("tg_new_${post.id}"))
            }
            Text(label, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(post.date?.take(10), when { post.playable -> null; opening -> "Opening with your account…"; else -> dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.rowNote(signedIn) }).joinToString(" · ").ifBlank { "Post ${post.id}" },
                color = if (post.playable) p.textTertiary else p.textSecondary, fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}
