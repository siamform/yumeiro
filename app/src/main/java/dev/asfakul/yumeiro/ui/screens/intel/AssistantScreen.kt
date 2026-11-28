/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.intel

import dev.asfakul.yumeiro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.intel.ai.AiError
import dev.asfakul.yumeiro.intel.ai.AiPersonal
import dev.asfakul.yumeiro.intel.ai.AssistantTalk
import dev.asfakul.yumeiro.intel.ai.AiMessage
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.intel.ai.Assistant
import dev.asfakul.yumeiro.intel.ai.AssistantChats
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.extras.Links
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.WarnTone
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Categories the assistant can be pointed at (null = everything). */
private val CHAT_CATEGORIES: List<MediaCategory?> = listOf(null, MediaCategory.ANIME, MediaCategory.MANGA, MediaCategory.NOVEL, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.MUSIC, MediaCategory.YOUTUBE)

/** Starting points for each category (the owner's examples first). */
internal fun assistantPrompts(cat: MediaCategory?): List<String> = when (cat) {
    MediaCategory.ANIME -> listOf("Recommend anime similar to Future Diary", "Find underrated anime with Yandere female characters", "Something based on my taste but with a different storyline", "Surprise me")
    MediaCategory.MANGA -> listOf("Find manga that matches my favourite anime", "Underrated psychological manga", "Something I haven't read yet")
    MediaCategory.NOVEL -> listOf("Suggest novels based on the themes I usually enjoy", "Light novels like the anime I loved", "Something completely different")
    MediaCategory.MOVIE -> listOf("Suggest psychological thrillers with survival-game stories", "Underrated mystery movies", "Something completely different")
    MediaCategory.SERIES -> listOf("Series like the ones I finished recently", "Hidden gem crime series", "Surprise me")
    MediaCategory.MUSIC -> listOf("Artists like the ones I play most", "Music for a rainy evening, based on my artists")
    MediaCategory.YOUTUBE -> listOf("Channels like the ones I watch", "Documentary channels I might like")
    null -> listOf("Suggest psychological thrillers with survival-game stories", "Recommend anime similar to Future Diary", "Find manga that matches my favourite anime", "Suggest novels based on the themes I usually enjoy")
}

/**
 * B21: the assistant space, redesigned for R28 item 16 / 32. One chat at a time for the open profile (its history in
 * the sheet). The start screen greets the person and suggests questions built from their own titles
 * ([AssistantTalk.starters]); the composer is one pill with the category chip inside it, "/" opens the commands, and
 * every message goes through [AssistantTalk.respond] (researched picks, plain conversation or a local command).
 * Answers can be copied, retried and followed up; a translation correction offers "Add to glossary".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(initialCategory: MediaCategory?, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenRec: (Discovery.Rec) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { AiSettings.get(context); true }
    // B20 S8: past chats are a file read; loaded off the main thread (the list fills in when ready).
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { AssistantChats.ensure(context); AiPersonal.get(context) } }
    val cfg by AiSettings.config.collectAsState()
    val chats by AssistantChats.chats.collectAsState()
    val profileName = remember { Profiles.current(context).name }
    var category by rememberSaveable { mutableStateOf(initialCategory?.code) }
    var chatId by rememberSaveable { mutableStateOf<String?>(null) }
    var turns by remember { mutableStateOf<List<AssistantChats.Turn>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var step by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val cat = MediaCategory.of(category)
    var starters by remember(cat) { mutableStateOf(assistantPrompts(cat)) }
    LaunchedEffect(cat) { starters = withContext(Dispatchers.IO) { runCatching { AssistantTalk.starters(cat, TasteSignals.entries(context), assistantPrompts(cat)) }.getOrDefault(assistantPrompts(cat)) } }

    LaunchedEffect(chatId) { chatId?.let { id -> AssistantChats.get(context, id)?.let { turns = it.turns; category = it.category?.code } } }
    LaunchedEffect(turns.size, step) { if (turns.isNotEmpty()) runCatching { list.animateScrollToItem(turns.size + 1) } }

    fun send(text: String) {
        val q = text.trim()
        if (q.isBlank() || job != null) return
        input = ""
        val id = chatId ?: "c" + System.currentTimeMillis().toString(36)
        chatId = id
        val history = turns.filter { !it.error && it.answer?.kind != "local" && !(it.user && AssistantTalk.parse(it.text)?.let { p -> p.command != "recom" && p.command != "translate" } == true) }
            .map { t -> AiMessage(t.user, if (t.user) t.text else (t.answer?.let { a -> a.reply + a.picks.joinToString("") { "\n- ${it.title}" } } ?: t.text)) }
        val asked = turns + AssistantChats.Turn(true, q, at = System.currentTimeMillis())
        turns = asked
        job = scope.launch {
            val turn = try {
                val a = AssistantTalk.respond(context, q, cat, history, AssistantChats.recentlySuggested(context)) { s -> step = s }
                AssistantChats.Turn(false, a.reply, a, System.currentTimeMillis())
            } catch (e: CancellationException) { throw e } catch (e: AiError) {
                AssistantChats.Turn(false, e.message.orEmpty(), at = System.currentTimeMillis(), error = true)
            } catch (e: Exception) {
                AssistantChats.Turn(false, "Something went wrong (${e.javaClass.simpleName}). Try again.", at = System.currentTimeMillis(), error = true)
            } finally { step = null; job = null }
            turns = asked + turn
            AssistantChats.put(context, AssistantChats.Chat(id, asked.first { it.user }.text.take(60), cat, turns, System.currentTimeMillis()))
        }
    }
    fun retryLast() { turns.lastOrNull { it.user }?.text?.let { q -> turns = turns.dropLastWhile { !it.user }.dropLast(1); send(q) } }

    Column(Modifier.fillMaxSize().imePadding().testTag("assistant")) {
        SubPageHeader(
            title = "Yumeiro AI", onBack = onBack,
            subtitle = listOf(profileName.ifBlank { null }, cfg.preset?.takeIf { cfg.ready }?.label?.substringBefore(" (")).filterNotNull().joinToString(" · ").ifBlank { "Assistant" },
            actions = {
                IconButton(onClick = { historyOpen = true }, modifier = Modifier.testTag("assistant_history")) { Icon(Icons.Default.History, "Past chats", tint = TextPrimary) }
                IconButton(onClick = { job?.cancel(); chatId = null; turns = emptyList() }, modifier = Modifier.testTag("assistant_new")) { Icon(Icons.Default.AddComment, "New chat", tint = TextPrimary) }
            },
        )
        if (!cfg.ready || !cfg.forAssistant) {
            SetupCard(cfg.ready, onOpenSettings)
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (turns.isEmpty()) item("intro") {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("assistant_intro"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (profileName.isBlank()) "What are you in the mood for?" else "Hi $profileName, what are you in the mood for?",
                        color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, modifier = Modifier.testTag("assistant_greeting"))
                    Text(stringResource(R.string.as_intro), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    starters.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(yuRound(14)).background(GlassFill).clickable { send(p) }.padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("assistant_prompt_$i"), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(p, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
                        }
                    }
                    Text("Tip: type / for commands such as /recom, /translate and /glossary.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).testTag("assistant_tip"))
                }
            }
            itemsIndexed(turns, key = { i, t -> "$i-${t.at}" }) { i, t ->
                if (t.user) UserBubble(t.text) else AnswerBlock(t, last = i == turns.lastIndex && job == null,
                    onOpen = { r -> Discovery.opened(context, r); onOpenRec(r) },
                    onNotInterested = { r -> Discovery.dismiss(context, r); AppMessages.show(context, "You won't see ${r.title} in For You again") },
                    onRetry = { retryLast() }, onFollow = { send(it) },
                    onSaveTerm = { term -> if (AiPersonal.addTerm(context, term.source, term.target, term.note)) AppMessages.show(context, "Added ${term.source} → ${term.target} to your glossary") })
            }
            step?.let { s ->
                item("step") {
                    Column(Modifier.fillMaxWidth().testTag("assistant_step").semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(s, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Composer(input, { input = it.take(1_000) }, cat, { category = it?.code }, busy = job != null, onSend = { send(input) },
            onStop = { job?.cancel(); job = null; step = null; turns = turns.dropLastWhile { it.user } })
    }

    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(), containerColor = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("assistant_history_sheet")) {
            Text(stringResource(R.string.as_past_chats), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(if (dev.asfakul.yumeiro.security.Privacy.incognito.collectAsState().value) R.string.as_history_incognito else R.string.as_history_hint), color = TextTertiary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (chats.isEmpty()) Text(stringResource(R.string.as_no_chats), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(chats, key = { it.id }) { ch ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(12)).clickable { chatId = ch.id; historyOpen = false }.padding(horizontal = 8.dp).testTag("assistant_chat_${ch.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(ch.category?.label, android.text.format.DateUtils.getRelativeTimeSpanString(ch.at).toString()).joinToString(" · "), color = TextTertiary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { AssistantChats.delete(context, ch.id); if (chatId == ch.id) { chatId = null; turns = emptyList() } }) { Icon(Icons.Default.Delete, "Delete chat", tint = TextTertiary) }
                    }
                }
            }
            if (chats.isNotEmpty()) TextButton(onClick = { AssistantChats.clear(context); chatId = null; turns = emptyList(); historyOpen = false }, modifier = Modifier.testTag("assistant_clear")) {
                Text(stringResource(R.string.as_delete_all), color = WarnTone)
            }
        }
    }
}

@Composable
private fun SetupCard(keySet: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp).clip(yuRound(16)).background(GlassFill).padding(18.dp).testTag("assistant_setup"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (keySet) "The assistant is turned off" else "Add your own AI key", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(if (keySet) "Turn on \"Assistant\" in Settings → Yumeiro AI to chat here." else
            "The assistant uses your own key: free ones from Google Gemini, Groq, OpenRouter, Cerebras, Mistral or Cloudflare, paid ones from OpenAI, xAI, Anthropic or DeepSeek, or your own server. " +
                "For You and Similar by story work without it.", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("assistant_setup_open")) {
            Text(stringResource(R.string.as_open_ai_settings))
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(text, color = OnAccent, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.widthIn(max = 520.dp).clip(yuRound(16)).background(SpotifyGreen)
            .padding(horizontal = 14.dp, vertical = 10.dp).testTag("assistant_user"))
    }
}

/** The composer pill: category chip inside the field (its menu picks the category), the text, send or stop; "/" lists commands above it. */
@Composable
private fun Composer(input: String, onInput: (String) -> Unit, cat: MediaCategory?, onCategory: (MediaCategory?) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val commands = remember(input) { AssistantTalk.suggestions(input) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        androidx.compose.animation.AnimatedVisibility(commands.isNotEmpty(), enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(180)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(140))) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(yuRound(16)).background(dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.surfaceContainer).padding(vertical = 4.dp).testTag("assistant_commands")) {
                commands.forEach { c ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onInput("/${c.name} ") }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("assistant_cmd_${c.name}")) {
                        Text("/${c.name}" + (if (c.args.isNotBlank()) " ${c.args}" else ""), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.description, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(yuRound(26)).background(GlassFill).padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag("assistant_composer"),
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.heightIn(min = 40.dp).clip(yuRound(20)).background(SpotifyGreen.copy(alpha = 0.14f)).clickable(onClickLabel = "Choose what to ask about") { menu = true }
                    .padding(horizontal = 10.dp).testTag("assistant_cat"), verticalAlignment = Alignment.CenterVertically) {
                    Text(cat?.label ?: "Everything", color = SpotifyGreen, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }, modifier = Modifier.testTag("assistant_cat_menu")) {
                    CHAT_CATEGORIES.forEach { c ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(c?.label ?: "Everything", fontWeight = if (c == cat) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { onCategory(c); menu = false }, trailingIcon = { if (c == cat) Icon(Icons.Default.Check, "Selected", tint = SpotifyGreen) },
                            modifier = Modifier.testTag("assistant_cat_${c?.code ?: "all"}"))
                    }
                }
            }
            androidx.compose.material3.TextField(input, onInput, Modifier.weight(1f).testTag("assistant_input"), maxLines = 5,
                placeholder = { Text(if (cat == null) "Ask anything, or type /" else "Ask about ${cat.label.lowercase()}, or type /", color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }))
            if (busy) TextButton(onClick = onStop, modifier = Modifier.testTag("assistant_stop")) { Text(stringResource(R.string.stop)) }
            else IconButton(onClick = onSend, enabled = input.isNotBlank(), modifier = Modifier.testTag("assistant_send")) {
                Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (input.isNotBlank()) SpotifyGreen else GlassFill), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) OnAccent else TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AnswerBlock(t: AssistantChats.Turn, last: Boolean, onOpen: (Discovery.Rec) -> Unit, onNotInterested: (Discovery.Rec) -> Unit, onRetry: () -> Unit,
                        onFollow: (String) -> Unit, onSaveTerm: (AiPersonal.Term) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().testTag(if (t.error) "assistant_error" else "assistant_answer"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (t.error) {
            Text(t.text, color = WarnTone, fontSize = 14.sp)
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("assistant_retry")) { Text(stringResource(R.string.try_again)) }
            return@Column
        }
        val a = t.answer ?: return@Column
        if (a.reply.isNotBlank()) Text(a.reply, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        if (a.clarify.isNotBlank()) Text(a.clarify, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("assistant_clarify"))
        a.research?.let { r ->
            Text(listOf(
                if (r.anilist) "AniList checked" else "AniList not reached",
                if (r.tmdb) "TMDB checked" else null,
                if (r.web) "Google searched" else "No web search",
                "Reddit and X aren't accessible",
            ).filterNotNull().joinToString(" · "), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.testTag("assistant_research"))
        }
        a.sections.forEach { s ->
            Text(s.name, color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            s.picks.forEach { p -> PickCard(p, onOpen, onNotInterested) }
        }
        if (a.sections.isEmpty() && a.reply.isBlank() && a.clarify.isBlank()) Text(stringResource(R.string.as_no_answer), color = TextSecondary, fontSize = 14.sp)
        a.offer?.let { term ->
            var saved by remember(t.at) { mutableStateOf(false) }
            if (!saved) OutlinedButton(onClick = { onSaveTerm(term); saved = true }, modifier = Modifier.testTag("assistant_offer")) {
                Text("Add \"${term.source} → ${term.target}\" to glossary", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (last && a.followUps.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.followUps.forEachIndexed { i, f ->
                Text(f, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.clip(yuRound(16)).background(GlassFill).clickable { onFollow(f) }
                    .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp).testTag("assistant_follow_$i"))
            }
        }
        if (a.kind != "local") Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            val clip = androidx.compose.ui.platform.LocalClipboardManager.current
            IconButton(onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(a.reply + a.picks.joinToString("") { "\n• ${it.title}" + (it.year?.let { y -> " ($y)" } ?: "") })); AppMessages.show(context, "Copied") },
                modifier = Modifier.testTag("assistant_copy")) { Icon(Icons.Default.ContentCopy, "Copy answer", tint = TextTertiary, modifier = Modifier.size(18.dp)) }
            if (last) IconButton(onClick = onRetry, modifier = Modifier.testTag("assistant_regen")) { Icon(Icons.Default.Refresh, "Ask again", tint = TextTertiary, modifier = Modifier.size(18.dp)) }
            if (a.model.isNotBlank()) Text(a.model, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.sources.isNotEmpty()) {
            Text(stringResource(R.string.as_sources), color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            a.sources.take(8).forEach { src ->
                Text(src.title, color = SpotifyGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable { Links.open(context, src.url) }.padding(vertical = 6.dp).testTag("assistant_source"))
            }
        }
    }
}

@Composable
private fun PickCard(p: Assistant.Pick, onOpen: (Discovery.Rec) -> Unit, onNotInterested: (Discovery.Rec) -> Unit) {
    val rec = p.rec
    Row(Modifier.fillMaxWidth().clip(yuRound(14)).background(GlassFill)
        .then(if (rec != null) Modifier.clickable(onClickLabel = "Open ${p.title}") { onOpen(rec) } else Modifier)
        .padding(10.dp).testTag(if (p.verified) "assistant_pick" else "assistant_pick_unverified")) {
        if (rec?.cover != null) {
            Box(Modifier.width(64.dp).height(96.dp).clip(yuRound(10))) { PosterImage(rec.cover, emptyMap(), Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.verified) Icon(Icons.Default.Verified, "Checked", tint = SpotifyGreen, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
            Text(listOfNotNull(p.year?.toString(), p.category?.label, p.tags.take(3).joinToString(", ").ifBlank { null }).joinToString(" · "), color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!p.verified) Text(stringResource(R.string.as_not_confirmed), color = WarnTone, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            p.note?.takeIf { p.verified }?.let { Text(it, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            if (p.why.isNotBlank()) Text(p.why, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            if (p.differences.isNotBlank()) Text(stringResource(R.string.as_different, p.differences), color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            if (p.reception.isNotBlank()) Text(stringResource(R.string.as_reception, p.reception), color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            if (p.discovery.isNotBlank()) Text(p.discovery, color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp)
            if (p.confidence.isNotBlank()) Text(stringResource(R.string.as_confidence, p.confidence), color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            if (rec != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onOpen(rec) }, modifier = Modifier.testTag("assistant_pick_open")) { Text(stringResource(R.string.as_open), color = SpotifyGreen) }
                TextButton(onClick = { onNotInterested(rec) }, modifier = Modifier.testTag("assistant_pick_dismiss")) { Text(stringResource(R.string.as_not_interested), color = TextSecondary) }
            }
        }
    }
}

