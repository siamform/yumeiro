/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.telegram.td.TgCounts
import dev.asfakul.yumeiro.online.telegram.td.TgMe
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.telegram.td.TdEngine
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.telegram.td.TgAuth
import dev.asfakul.yumeiro.online.telegram.td.TgAuthState
import dev.asfakul.yumeiro.online.telegram.td.TgChat
import dev.asfakul.yumeiro.online.telegram.td.TgLibrary
import dev.asfakul.yumeiro.online.telegram.td.TgSession
import dev.asfakul.yumeiro.online.telegram.td.TgVideo
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.launch

/**
 * F-02 (ADR-052): sign in to Telegram, then the user's chats and the videos in each. Every step has its own Back:
 * code → phone, chat → chats, chats → Telegram page. Paste and autofill work in every field (WCAG 2.2 3.3.8).
 */
@Composable
fun TelegramAccountScreen(onBack: () -> Unit, onPlay: (StreamRequest) -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var session by remember { mutableStateOf<TgSession?>(null) }
    var startFailed by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val engine by TdEngine.state.collectAsState()
    // OWN-3: the user's own API ID / hash (my.telegram.org), asked first when the build has none.
    var keysForm by remember { mutableStateOf(dev.asfakul.yumeiro.online.telegram.td.TgAccounts.needsKeys(context)) }
    val keyScope = rememberCoroutineScope()
    if (keysForm) {
        TelegramKeysStep(
            onBack = { if (dev.asfakul.yumeiro.online.telegram.td.TgAccounts.needsKeys(context)) onBack() else keysForm = false },
            onSaved = { keyScope.launch { dev.asfakul.yumeiro.online.telegram.td.TgAccounts.keysChanged(context); session = null; keysForm = false; attempt++ } },
        )
        return
    }

    LaunchedEffect(attempt) {
        startFailed = null
        val s = runCatching { TgAccounts.session(context) }.getOrNull()
        if (s == null) startFailed = (TdEngine.state.value as? TdEngine.State.Failed)?.message
            ?: "Telegram could not start on this device." else session = s
    }

    Column(Modifier.fillMaxSize().background(p.background).testTag("tg_account")) {
        val s = session
        if (s == null) {
            SubPageHeader(title = "Your Telegram account", subtitle = "Getting ready", onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    startFailed != null -> {
                        Text(startFailed!!, color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.testTag("tg_start_error"))
                        TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("tg_start_retry")) { Text("Try again") }
                    }
                    engine is TdEngine.State.Downloading -> {
                        val d = engine as TdEngine.State.Downloading
                        Text("Downloading Telegram support (once, about ${d.total / 1_000_000} MB)", color = p.textPrimary, fontSize = 14.sp)
                        LinearProgressIndicator(
                            progress = { if (d.total > 0) d.done.toFloat() / d.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("tg_engine_progress"),
                        )
                    }
                    else -> dev.asfakul.yumeiro.ui.components.YuLoader(Modifier.testTag("tg_starting"), size = 48.dp, label = "Starting Telegram")
                }
            }
            BackHandler { onBack() }
            return@Column
        }
        val state by s.state.collectAsState()
        val editingPhone by s.editingPhone.collectAsState()
        when {
            state is TgAuthState.Ready -> TelegramLibrary(s, onBack, onPlay)
            state is TgAuthState.WaitPhone -> PhoneStep(s, onBack, onOwnKeys = { keysForm = true })
            editingPhone && state is TgAuthState.WaitCode -> PhoneStep(s, onBack = { s.cancelPhoneEdit() })
            else -> {
                val title = "Sign in to Telegram"
                SubPageHeader(title = title, subtitle = "Your account stays on this phone", onBack = onBack)
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (val st = state) {
                        is TgAuthState.WaitCode -> CodeStep(s, st)
                        is TgAuthState.WaitPassword -> PasswordStep(s, st)
                        is TgAuthState.WaitRegistration -> RegisterStep(s, st)
                        is TgAuthState.Unsupported -> Text(
                            "This sign-in step (${st.step}) isn't supported in Yumeiro yet. Sign in once in the Telegram app, then try again.",
                            color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.testTag("tg_unsupported"),
                        )
                        TgAuthState.LoggingOut -> Text("Signing out…", color = p.textSecondary, modifier = Modifier.testTag("tg_logging_out"))
                        TgAuthState.Closed -> {
                            Text("Telegram closed.", color = p.textSecondary)
                            TextButton(onClick = { session = null; attempt++ }, modifier = Modifier.testTag("tg_reopen")) { Text("Open again") }
                        }
                        else -> dev.asfakul.yumeiro.ui.components.YuLoader(Modifier.testTag("tg_starting"), size = 48.dp, label = "Starting Telegram")
                    }
                }
                // Back on the code step returns to the phone step; elsewhere it leaves (sign-in resumes next time).
                BackHandler {
                    if (state is TgAuthState.WaitCode) s.backToPhone() else onBack()
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(s: TgSession) {
    val error by s.error.collectAsState()
    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp).testTag("tg_error")) }
}

@Composable
private fun SubmitButton(label: String, s: TgSession, enabled: Boolean, tag: String, onClick: suspend () -> Unit) {
    val busy by s.busy.collectAsState()
    val scope = rememberCoroutineScope()
    Button(
        onClick = { scope.launch { onClick() } }, enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp).testTag(tag),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(label)
    }
}

@Composable
private fun PhoneStep(s: TgSession, onBack: () -> Unit, onOwnKeys: (() -> Unit)? = null) {
    val p = LocalMtPalette.current
    var phone by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    SubPageHeader(title = "Sign in to Telegram", subtitle = "Your account stays on this phone", onBack = onBack)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Phone number", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("With the country code, for example +880 1712 345678. Telegram sends a code to your Telegram app or by SMS.", color = p.textSecondary, fontSize = 13.sp)
        OutlinedTextField(
            value = phone, onValueChange = { phone = it; s.clearError() }, singleLine = true,
            label = { Text("Phone number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitPhone(phone) } }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PhoneNumber }.testTag("tg_phone"),
        )
        ErrorLine(s)
        SubmitButton("Send code", s, TgAuth.normalizePhone(phone) != null, "tg_phone_go") { s.submitPhone(phone) }
        Text(
            "Yumeiro signs in with Telegram's official library. Your messages are not sent anywhere else; the login is saved encrypted for this profile only.",
            color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp),
        )
        if (onOwnKeys != null) TextButton(onClick = onOwnKeys, modifier = Modifier.padding(top = 4.dp).testTag("tg_own_keys")) {
            Text(if (dev.asfakul.yumeiro.online.telegram.td.TgKeys.hasOwn(LocalContext.current)) "Change your API ID and hash" else "Use your own API ID and hash")
        }
    }
    BackHandler { onBack() }
}

@Composable
private fun CodeStep(s: TgSession, st: TgAuthState.WaitCode) {
    val p = LocalMtPalette.current
    var code by rememberSaveable(st.phone) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val where = when (st.kind) {
        "Telegram app" -> "in your Telegram app"
        "SMS" -> "by SMS"
        "call" -> "by a phone call"
        "Fragment" -> "on fragment.com"
        else -> "to you"
    }
    Text("Code sent $where to ${st.phone}", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.testTag("tg_code_where"))
    OutlinedTextField(
        value = code, onValueChange = { code = TgAuth.normalizeCode(it).take(12); s.clearError() }, singleLine = true,
        label = { Text(if (st.length > 0) "${st.length}-digit code" else "Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitCode(code) } }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.SmsOtpCode }.testTag("tg_code"),
    )
    ErrorLine(s)
    SubmitButton("Sign in", s, code.isNotEmpty(), "tg_code_go") { s.submitCode(code) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { s.backToPhone() }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_change_phone")) { Text("Change number") }
        if (st.next != null) TextButton(onClick = { scope.launch { s.resendCode() } }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_resend")) { Text("Send again") }
    }
}

@Composable
private fun PasswordStep(s: TgSession, st: TgAuthState.WaitPassword) {
    val p = LocalMtPalette.current
    var pass by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text("Two-step verification password", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    if (st.hint.isNotBlank()) Text("Hint: ${st.hint}", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.testTag("tg_pass_hint"))
    OutlinedTextField(
        value = pass, onValueChange = { pass = it; s.clearError() }, singleLine = true,
        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitPassword(pass) } }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.Password }.testTag("tg_password"),
    )
    ErrorLine(s)
    SubmitButton("Sign in", s, pass.isNotEmpty(), "tg_password_go") { s.submitPassword(pass) }
    Text("Forgot it? Reset it in the Telegram app (Settings › Privacy and Security › Two-Step Verification).", color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun RegisterStep(s: TgSession, st: TgAuthState.WaitRegistration) {
    val p = LocalMtPalette.current
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    Text("This number has no Telegram account yet. Create one:", color = p.textPrimary, fontSize = 14.sp)
    OutlinedTextField(first, { first = it; s.clearError() }, singleLine = true, label = { Text("First name") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PersonFirstName }.testTag("tg_first"))
    OutlinedTextField(last, { last = it }, singleLine = true, label = { Text("Last name (optional)") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PersonLastName }.testTag("tg_last"))
    if (st.termsText.isNotBlank()) Text(st.termsText.take(600), color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    ErrorLine(s)
    SubmitButton("Create account", s, first.isNotBlank(), "tg_register_go") { s.register(first, last) }
}

/** Signed in: chats (filtered on the phone), then a chat's videos (searched by Telegram). */
@Composable
private fun TelegramLibrary(s: TgSession, onBack: () -> Unit, onPlay: (StreamRequest) -> Unit) {
    val p = LocalMtPalette.current
    val library = remember(s) { TgLibrary(s.client) }
    val scope = rememberCoroutineScope()
    val chats = remember(s) { mutableStateListOf<TgChat>() }
    var loading by remember(s) { mutableStateOf(true) }
    var error by remember(s) { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    var openChat by remember { mutableStateOf<TgChat?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun load() {
        loading = true; error = null
        scope.launch {
            runCatching { library.chats() }
                .onSuccess { chats.clear(); chats.addAll(it) }
                .onFailure { error = TgAuth.errorText(it) }
            loading = false
        }
    }
    LaunchedEffect(s) { load() }
    // TG-ME-1 (owner list 8A): who is signed in, with their photo; remembered for the Telegram page's account card.
    val appContext = LocalContext.current.applicationContext
    val me by produceState<Pair<TgMe, String?>?>(null, s) {
        val m = library.me() ?: return@produceState
        value = m to null
        val photo = library.smallFile(m.photoFileId)
        value = m to photo
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TgAccounts.rememberMe(appContext, m.name, photo) }
    }

    openChat?.let { chat ->
        TelegramChatVideos(s, library, chat, onBack = { openChat = null }, onPlay = onPlay)
        return
    }

    if (confirmLogout) dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Sign out of Telegram?") },
        text = { Text("Yumeiro forgets this login and its downloaded Telegram files. Your account itself is not changed.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; scope.launch { s.logOut() } }, modifier = Modifier.testTag("tg_logout_confirm")) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
    )

    val shown = remember(chats.toList(), filter) {
        val f = filter.trim()
        if (f.isEmpty()) chats.toList() else chats.filter { it.title.contains(f, ignoreCase = true) }
    }
    BackHandler { if (filter.isNotEmpty()) filter = "" else onBack() }
    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Your Telegram", subtitle = if (chats.isNotEmpty()) "${chats.size} chats" else "Chats", onBack = onBack) {
            TextButton(onClick = { confirmLogout = true }, modifier = Modifier.testTag("tg_logout")) { Text("Sign out") }
        }
        me?.let { (m, photo) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                    .background(p.surfaceContainerHigh).padding(12.dp).semantics(mergeDescendants = true) {}.testTag("tg_me"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TelegramAvatar(photo, m.name, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (m.username.isNotBlank()) "@${m.username} · signed in" else "Signed in", color = p.textSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        // DW-BOT-1: the one switch behind the consent card (TgMiniApp.CONSENT_TEXT points here).
        val ctx = LocalContext.current
        var miniOk by remember { mutableStateOf(dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.consented(ctx)) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp)
                .toggleable(value = miniOk, role = androidx.compose.ui.semantics.Role.Switch) {
                    miniOk = it; dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.setConsented(ctx, it)
                }.testTag("tg_miniapp_switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Bot mini apps", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("DiskWala links open through a Telegram downloader bot (third party) with this account", color = p.textSecondary, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(checked = miniOk, onCheckedChange = null)
        }
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, singleLine = true,
            label = { Text("Find a chat") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("tg_chat_filter"),
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag("tg_chats"),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { chat ->
                // TG-AVATAR-1 (owner list 8A): each chat with its own photo (TDLib's small copy, fetched as it scrolls in).
                val photo by produceState<String?>(null, chat.photoFileId) { value = library.smallFile(chat.photoFileId) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surfaceContainerHigh)
                        .clickable { openChat = chat }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp).testTag("tg_chat_${chat.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TelegramAvatar(photo, chat.title, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title.ifBlank { "Untitled chat" }, color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when (chat.kind) { TgChat.Kind.CHANNEL -> "Channel"; TgChat.Kind.GROUP -> "Group"; TgChat.Kind.PRIVATE -> "Chat" }, color = p.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (!loading && shown.isEmpty()) item("empty") {
                Text(error ?: if (filter.isNotBlank()) "No chat matches \"$filter\"." else "No chats yet.", color = p.textSecondary, modifier = Modifier.padding(16.dp).testTag("tg_chats_empty"))
                if (error != null) TextButton(onClick = { load() }, modifier = Modifier.testTag("tg_chats_retry")) { Text("Try again") }
            }
            if (loading) item("loading") {
                dev.asfakul.yumeiro.ui.components.SkeletonList(rows = if (shown.isEmpty()) 7 else 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("tg_chats_loading")) // SKELETON-2
            }
        }
    }
}

@Composable
private fun TelegramChatVideos(s: TgSession, library: TgLibrary, chat: TgChat, onBack: () -> Unit, onPlay: (StreamRequest) -> Unit) {
    val p = LocalMtPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos = remember(chat.id) { mutableStateListOf<TgVideo>() }
    var next by remember(chat.id) { mutableStateOf(0L) }
    var loading by remember(chat.id) { mutableStateOf(true) }
    var error by remember(chat.id) { mutableStateOf<String?>(null) }
    var input by rememberSaveable(chat.id) { mutableStateOf("") }
    var query by remember(chat.id) { mutableStateOf("") }
    var job by remember(chat.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pages by remember(chat.id) { mutableStateOf(0) }
    val list = rememberLazyGridState()

    fun load(from: Long) {
        loading = true; error = null
        val q = query
        job?.cancel()
        job = scope.launch {
            runCatching { library.videos(chat.id, q, from) }
                .onSuccess { page ->
                    val known = videos.map { it.messageId }.toHashSet()
                    videos.addAll(page.videos.filter { it.messageId !in known })
                    next = page.next
                    if (videos.isEmpty() && page.next == 0L) error = if (q.isNotBlank()) "No videos or audio match \"$q\" in this chat." else "No videos or audio in this chat."
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) error = TgAuth.errorText(it) }
            loading = false
        }
    }
    fun search(text: String) {
        if (text.trim() == query) return
        query = text.trim(); videos.clear(); next = 0; pages = 0
        load(0)
    }
    LaunchedEffect(chat.id) { load(0) }
    // Next page when the end comes into view (capped so a chat without videos doesn't page through all history unasked).
    LaunchedEffect(list, chat.id) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) ->
                if (!loading && next != 0L && pages < 5 && last != null && last >= total - 2) { pages++; load(next) }
            }
    }
    BackHandler { if (query.isNotEmpty()) { input = ""; search("") } else onBack() }

    // TG-COUNT-1 (owner list 8B): what the chat holds, from Telegram's own counts.
    val counts by produceState<TgCounts?>(null, chat.id) { value = runCatching { library.counts(chat.id) }.getOrNull() }
    Column(Modifier.fillMaxSize().testTag("tg_chat_videos")) {
        SubPageHeader(title = chat.title.ifBlank { "Chat" }, subtitle = counts?.label()?.ifBlank { null } ?: "Videos and audio", onBack = onBack)
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            label = { Text("Search this chat") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(input) }),
            trailingIcon = { IconButton(onClick = { search(input) }, modifier = Modifier.testTag("tg_video_search_go")) { Icon(Icons.Default.Search, "Search") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("tg_video_search"),
        )
        // TG-GRID-1 (owner list 8C): a gallery. Each video shows its own preview frame with the length on it; tap plays
        // (no autoplay, so no data is spent on previews beyond Telegram's small thumbnail).
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp), state = list, modifier = Modifier.fillMaxSize().testTag("tg_video_grid"),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(videos, key = { it.messageId }) { v ->
                val title = v.caption.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: v.name.ifBlank { chat.title }
                TgVideoTile(
                    v, title = v.caption.lineSequence().firstOrNull()?.ifBlank { null } ?: v.name.ifBlank { if (v.audio) "Audio" else "Video" },
                    thumb = { library.smallFile(v.thumbFileId) },
                    onPlay = { onPlay(StreamRequest(url = TgAccounts.streamUrl(s, v), title = title, format = StreamFormat.PROGRESSIVE)) },
                    onDownload = {
                        val req = StreamRequest(url = TgAccounts.downloadUrl(s, v), title = title, format = StreamFormat.PROGRESSIVE)
                        // OL-4: the same Offline / Permanent popup as every other download.
                        dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk.ask(context, req, sourceUrl = "https://t.me/c/${chat.id}/${v.messageId}") { dev.asfakul.yumeiro.ui.components.AppMessages.info(context, it) }
                    },
                )
            }
            error?.let { e -> item("error", span = { GridItemSpan(maxLineSpan) }) {
                Text(e, color = p.textSecondary, modifier = Modifier.padding(16.dp).testTag("tg_videos_error"))
            } }
            // SKELETON-2: video-tile placeholders in the grid's own shape.
            if (loading) items(dev.asfakul.yumeiro.ui.components.skeletonCells(videos.isNotEmpty()), key = { "sk_$it" }, contentType = { "skeleton" }) {
                dev.asfakul.yumeiro.ui.components.CoverSkeleton(aspect = 16f / 9f)
            } else if (next != 0L) item("more", span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { pages = 0; load(next) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tg_videos_more")) { Text("Older") }
            }
        }
    }
}

/** TG-GRID-1: one gallery tile: 16:9 preview (or a music tile), length and quality on the picture, title, size, download. */
@Composable
private fun TgVideoTile(v: TgVideo, title: String, thumb: suspend () -> String?, onPlay: () -> Unit, onDownload: () -> Unit) {
    val p = LocalMtPalette.current
    val path by produceState<String?>(null, v.thumbFileId) { if (v.thumbFileId != 0) value = thumb() }
    Column(Modifier.fillMaxWidth().testTag("tg_video_${v.messageId}")) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(p.surfaceContainerHigh)
                .clickable(onClickLabel = "Play $title", onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (v.audio) Icon(Icons.Default.MusicNote, null, tint = p.accentBright, modifier = Modifier.size(34.dp))
            else Icon(Icons.Default.PlayArrow, null, tint = p.textTertiary, modifier = Modifier.size(34.dp))
            path?.let { AsyncImage(model = java.io.File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            tileBadges(v).takeIf { it.isNotBlank() }?.let { b ->
                Text(
                    b, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.68f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                sizeText(v.size)?.let { Text(it, color = p.textSecondary, fontSize = 11.sp) }
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp).testTag("tg_download_${v.messageId}")) {
                Icon(Icons.Default.Download, "Download $title", tint = p.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Length and quality shown on the picture: "12:04 · 720p"; audio shows only its length. */
internal fun tileBadges(v: TgVideo): String = buildList {
    if (v.durationSec > 0) add(if (v.durationSec >= 3600) "%d:%02d:%02d".format(v.durationSec / 3600, v.durationSec / 60 % 60, v.durationSec % 60) else "%d:%02d".format(v.durationSec / 60, v.durationSec % 60))
    if (!v.audio && v.height > 0) add("${v.height}p")
}.joinToString(" · ")

internal fun sizeText(size: Long): String? = when {
    size <= 0 -> null
    size >= 1_000_000_000 -> "%.1f GB".format(size / 1e9)
    else -> "${maxOf(1, size / 1_000_000)} MB"
}


/**
 * OWN-3: the user's own Telegram API ID and API hash (my.telegram.org -> API development tools), like Hikari.
 * Saving them signs every profile out of Telegram on this phone (the old login belongs to the old keys).
 */
@Composable
private fun TelegramKeysStep(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var id by rememberSaveable { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hasOwn = remember { dev.asfakul.yumeiro.online.telegram.td.TgKeys.hasOwn(context) }
    val hasBuild = remember { dev.asfakul.yumeiro.online.telegram.td.TgKeys.build() != null }
    Column(Modifier.fillMaxSize().background(p.background).testTag("tg_keys")) {
        SubPageHeader(title = "Telegram API keys", subtitle = "From my.telegram.org", onBack = onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Telegram asks every app for its own keys. Get yours in a minute: sign in at my.telegram.org, open " +
                    "\"API development tools\", create an app (any name), then copy the App api_id and App api_hash here.",
                color = p.textSecondary, fontSize = 13.sp,
            )
            TextButton(onClick = { runCatching { uri.openUri("https://my.telegram.org/apps") } }, modifier = Modifier.testTag("tg_keys_site")) { Text("Open my.telegram.org") }
            OutlinedTextField(
                value = id, onValueChange = { id = it.filter { c -> c.isDigit() }.take(10); error = null }, singleLine = true,
                label = { Text("API ID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("tg_keys_id"),
            )
            OutlinedTextField(
                value = hash, onValueChange = { hash = it.trim().take(32); error = null }, singleLine = true,
                label = { Text("API hash") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("tg_keys_hash"),
            )
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp).testTag("tg_keys_error")) }
            Button(
                onClick = {
                    val e = dev.asfakul.yumeiro.online.telegram.td.TgKeys.save(context, id, hash)
                    if (e != null) error = e else onSaved()
                },
                enabled = id.isNotEmpty() && hash.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp).testTag("tg_keys_save"),
            ) { Text("Save and sign in") }
            if (hasOwn && hasBuild) TextButton(onClick = {
                dev.asfakul.yumeiro.online.telegram.td.TgKeys.clear(context); onSaved()
            }, modifier = Modifier.testTag("tg_keys_reset")) { Text("Use Yumeiro's keys again") }
            Text(
                "The hash is kept encrypted on this phone and never leaves it except to Telegram. Saving new keys signs " +
                    "every profile out of Telegram on this phone.",
                color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    BackHandler { onBack() }
}
