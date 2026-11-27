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
import dev.asfakul.yumeiro.ui.theme.yuRound

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
            ?: tr(R.string.telegram_could_not_start_on_this, "Telegram could not start on this device.") else session = s
    }

    Column(Modifier.fillMaxSize().background(p.background).testTag("tg_account")) {
        val s = session
        if (s == null) {
            SubPageHeader(title = tr(R.string.your_telegram_account, "Your Telegram account"), subtitle = tr(R.string.getting_ready, "Getting ready"), onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    startFailed != null -> {
                        Text(startFailed!!, color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.testTag("tg_start_error"))
                        TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("tg_start_retry")) { Text(stringResource(R.string.try_again)) }
                    }
                    engine is TdEngine.State.Downloading -> {
                        val d = engine as TdEngine.State.Downloading
                        Text(stringResource(R.string.downloading_telegram_support_once_about_mb, d.total / 1_000_000), color = p.textPrimary, fontSize = 14.sp)
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
                val title = tr(R.string.sign_in_to_telegram, "Sign in to Telegram")
                SubPageHeader(title = title, subtitle = tr(R.string.your_account_stays_on_this_phone, "Your account stays on this phone"), onBack = onBack)
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (val st = state) {
                        is TgAuthState.WaitCode -> CodeStep(s, st)
                        is TgAuthState.WaitPassword -> PasswordStep(s, st)
                        is TgAuthState.WaitRegistration -> RegisterStep(s, st)
                        is TgAuthState.Unsupported -> Text(
                            stringResource(R.string.this_sign_in_step_isnt_supported, st.step),
                            color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.testTag("tg_unsupported"),
                        )
                        TgAuthState.LoggingOut -> Text(stringResource(R.string.signing_out), color = p.textSecondary, modifier = Modifier.testTag("tg_logging_out"))
                        TgAuthState.Closed -> {
                            Text(stringResource(R.string.telegram_closed), color = p.textSecondary)
                            TextButton(onClick = { session = null; attempt++ }, modifier = Modifier.testTag("tg_reopen")) { Text(stringResource(R.string.open_again)) }
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
    SubPageHeader(title = tr(R.string.sign_in_to_telegram, "Sign in to Telegram"), subtitle = tr(R.string.your_account_stays_on_this_phone, "Your account stays on this phone"), onBack = onBack)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.phone_number), color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(stringResource(R.string.with_the_country_code_for_example), color = p.textSecondary, fontSize = 13.sp)
        OutlinedTextField(
            value = phone, onValueChange = { phone = it; s.clearError() }, singleLine = true,
            label = { Text(stringResource(R.string.phone_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitPhone(phone) } }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PhoneNumber }.testTag("tg_phone"),
        )
        ErrorLine(s)
        SubmitButton(tr(R.string.send_code, "Send code"), s, TgAuth.normalizePhone(phone) != null, "tg_phone_go") { s.submitPhone(phone) }
        Text(
            stringResource(R.string.yumeiro_signs_in_with_telegrams_official),
            color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp),
        )
        if (onOwnKeys != null) TextButton(onClick = onOwnKeys, modifier = Modifier.padding(top = 4.dp).testTag("tg_own_keys")) {
            Text(if (dev.asfakul.yumeiro.online.telegram.td.TgKeys.hasOwn(LocalContext.current)) tr(R.string.change_your_api_id_and_hash, "Change your API ID and hash") else tr(R.string.use_your_own_api_id_and, "Use your own API ID and hash"))
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
    Text(stringResource(R.string.code_sent_to, where, st.phone), color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.testTag("tg_code_where"))
    OutlinedTextField(
        value = code, onValueChange = { code = TgAuth.normalizeCode(it).take(12); s.clearError() }, singleLine = true,
        label = { Text(if (st.length > 0) "${st.length}-digit code" else "Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitCode(code) } }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.SmsOtpCode }.testTag("tg_code"),
    )
    ErrorLine(s)
    SubmitButton(tr(R.string.sign_in, "Sign in"), s, code.isNotEmpty(), "tg_code_go") { s.submitCode(code) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { s.backToPhone() }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_change_phone")) { Text(stringResource(R.string.change_number)) }
        if (st.next != null) TextButton(onClick = { scope.launch { s.resendCode() } }, modifier = Modifier.heightIn(min = 48.dp).testTag("tg_resend")) { Text(stringResource(R.string.send_again)) }
    }
}

@Composable
private fun PasswordStep(s: TgSession, st: TgAuthState.WaitPassword) {
    val p = LocalMtPalette.current
    var pass by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.two_step_verification_password), color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    if (st.hint.isNotBlank()) Text(stringResource(R.string.hint, st.hint), color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.testTag("tg_pass_hint"))
    OutlinedTextField(
        value = pass, onValueChange = { pass = it; s.clearError() }, singleLine = true,
        label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { scope.launch { s.submitPassword(pass) } }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.Password }.testTag("tg_password"),
    )
    ErrorLine(s)
    SubmitButton(tr(R.string.sign_in, "Sign in"), s, pass.isNotEmpty(), "tg_password_go") { s.submitPassword(pass) }
    Text(stringResource(R.string.forgot_it_reset_it_in_the), color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun RegisterStep(s: TgSession, st: TgAuthState.WaitRegistration) {
    val p = LocalMtPalette.current
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.this_number_has_no_telegram_account), color = p.textPrimary, fontSize = 14.sp)
    OutlinedTextField(first, { first = it; s.clearError() }, singleLine = true, label = { Text(stringResource(R.string.first_name)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PersonFirstName }.testTag("tg_first"))
    OutlinedTextField(last, { last = it }, singleLine = true, label = { Text(stringResource(R.string.last_name_optional)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.PersonLastName }.testTag("tg_last"))
    if (st.termsText.isNotBlank()) Text(st.termsText.take(600), color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    ErrorLine(s)
    SubmitButton(tr(R.string.create_account, "Create account"), s, first.isNotBlank(), "tg_register_go") { s.register(first, last) }
}

/** Signed in: chats (filtered on the phone), then a chat's videos (searched by Telegram). */
@Composable
private fun TelegramLibrary(s: TgSession, onBack: () -> Unit, onPlay: (StreamRequest) -> Unit) {
    val library = remember(s) { TgLibrary(s.client) }
    val scope = rememberCoroutineScope()
    val chats = remember(s) { mutableStateListOf<TgChat>() }
    var loading by remember(s) { mutableStateOf(true) }
    var error by remember(s) { mutableStateOf<String?>(null) }
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
        title = { Text(stringResource(R.string.sign_out_of_telegram)) },
        text = { Text(stringResource(R.string.yumeiro_forgets_this_login_and_its)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; scope.launch { s.logOut() } }, modifier = Modifier.testTag("tg_logout_confirm")) { Text(stringResource(R.string.sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
    )

    // B8 (beta chapter): account and search moved into the header as icons (TelegramChatsPage).
    TelegramChatsPage(
        chats = chats.toList(), me = me, loading = loading, error = error,
        photo = { library.smallFile(it) }, onOpen = { openChat = it }, onRetry = { load() },
        onSignOut = { confirmLogout = true }, onBack = onBack,
    )
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
                    if (videos.isEmpty() && page.next == 0L) error = if (q.isNotBlank()) tr(R.string.no_videos_or_audio_match_in, "No videos or audio match \"%1\$s\" in this chat.", q) else tr(R.string.no_videos_or_audio_in_this, "No videos or audio in this chat.")
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
    var searchOpen by rememberSaveable(chat.id) { mutableStateOf(false) }

    // TG-COUNT-1 (owner list 8B): what the chat holds, from Telegram's own counts.
    val counts by produceState<TgCounts?>(null, chat.id) { value = runCatching { library.counts(chat.id) }.getOrNull() }
    Column(Modifier.fillMaxSize().testTag("tg_chat_videos")) {
        // B8: the chat's search sits behind the header's search icon too.
        TgSearchHeader(
            title = chat.title.ifBlank { "Chat" }, subtitle = counts?.label()?.ifBlank { null } ?: "Videos and audio",
            searching = searchOpen, onSearching = { searchOpen = it },
            query = input, onQuery = { input = it }, placeholder = tr(R.string.search_this_chat, "Search this chat"),
            onBack = onBack, onSubmit = { search(it) },
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
                TextButton(onClick = { pages = 0; load(next) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tg_videos_more")) { Text(stringResource(R.string.older)) }
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
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(yuRound(14)).background(p.surfaceContainerHigh)
                .clickable(onClickLabel = "Play $title", onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (v.audio) Icon(Icons.Default.MusicNote, null, tint = p.accentBright, modifier = Modifier.size(34.dp))
            else Icon(Icons.Default.PlayArrow, null, tint = p.textTertiary, modifier = Modifier.size(34.dp))
            path?.let { AsyncImage(model = java.io.File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            tileBadges(v).takeIf { it.isNotBlank() }?.let { b ->
                Text(
                    b, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(yuRound(6))
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
                Icon(Icons.Default.Download, stringResource(R.string.download_2, title), tint = p.textSecondary, modifier = Modifier.size(20.dp))
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
        SubPageHeader(title = tr(R.string.telegram_api_keys, "Telegram API keys"), subtitle = tr(R.string.from_my_telegram_org, "From my.telegram.org"), onBack = onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                tr(R.string.telegram_asks_every_app_for_its, "Telegram asks every app for its own keys. Get yours in a minute: sign in at my.telegram.org, open ") +
                    tr(R.string.api_development_tools_create_an_app, "\"API development tools\", create an app (any name), then copy the App api_id and App api_hash here."),
                color = p.textSecondary, fontSize = 13.sp,
            )
            TextButton(onClick = { runCatching { uri.openUri("https://my.telegram.org/apps") } }, modifier = Modifier.testTag("tg_keys_site")) { Text(stringResource(R.string.open_my_telegram_org)) }
            OutlinedTextField(
                value = id, onValueChange = { id = it.filter { c -> c.isDigit() }.take(10); error = null }, singleLine = true,
                label = { Text(stringResource(R.string.api_id)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("tg_keys_id"),
            )
            OutlinedTextField(
                value = hash, onValueChange = { hash = it.trim().take(32); error = null }, singleLine = true,
                label = { Text(stringResource(R.string.api_hash)) },
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
            ) { Text(stringResource(R.string.save_and_sign_in)) }
            if (hasOwn && hasBuild) TextButton(onClick = {
                dev.asfakul.yumeiro.online.telegram.td.TgKeys.clear(context); onSaved()
            }, modifier = Modifier.testTag("tg_keys_reset")) { Text(stringResource(R.string.use_yumeiros_keys_again)) }
            Text(
                tr(R.string.the_hash_is_kept_encrypted_on, "The hash is kept encrypted on this phone and never leaves it except to Telegram. Saving new keys signs ") +
                    "every profile out of Telegram on this phone.",
                color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    BackHandler { onBack() }
}
