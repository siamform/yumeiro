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
import dev.asfakul.yumeiro.intel.ai.AiError
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
import kotlinx.coroutines.launch

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
 * B21: the assistant space. One chat at a time for the open profile (its history in the sheet), the section's
 * category preselected, every pick verified against AniList / TMDB before it is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(initialCategory: MediaCategory?, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenRec: (Discovery.Rec) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { AiSettings.get(context); AssistantChats.ensure(context); true }
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

    LaunchedEffect(chatId) { chatId?.let { id -> AssistantChats.get(context, id)?.let { turns = it.turns; category = it.category?.code } } }
    LaunchedEffect(turns.size, step) { if (turns.isNotEmpty()) runCatching { list.animateScrollToItem(turns.size + 1) } }

    fun send(text: String) {
        val q = text.trim()
        if (q.isBlank() || job != null) return
        input = ""
        val id = chatId ?: "c" + System.currentTimeMillis().toString(36)
        chatId = id
        val history = turns.filter { !it.error }.map { t -> AiMessage(t.user, if (t.user) t.text else (t.answer?.let { a -> a.reply + a.picks.joinToString("") { "\n- ${it.title}" } } ?: t.text)) }
        val asked = turns + AssistantChats.Turn(true, q, at = System.currentTimeMillis())
        turns = asked
        job = scope.launch {
            val turn = try {
                val a = Assistant.ask(context, q, cat, history, AssistantChats.recentlySuggested(context)) { s -> step = s }
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

    Column(Modifier.fillMaxSize().imePadding().testTag("assistant")) {
        SubPageHeader(
            title = "Yumeiro AI", onBack = onBack,
            subtitle = listOf("Assistant", profileName.ifBlank { null }, cfg.preset?.takeIf { cfg.ready }?.label?.substringBefore(" (")).filterNotNull().joinToString(" · "),
            actions = {
                IconButton(onClick = { historyOpen = true }, modifier = Modifier.testTag("assistant_history")) { Icon(Icons.Default.History, "Past chats", tint = TextPrimary) }
                IconButton(onClick = { job?.cancel(); chatId = null; turns = emptyList() }, modifier = Modifier.testTag("assistant_new")) { Icon(Icons.Default.AddComment, "New chat", tint = TextPrimary) }
            },
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHAT_CATEGORIES.forEach { c ->
                FilterPill(c?.label ?: "Everything", cat == c, onClick = { category = c?.code }, testTag = "assistant_cat_${c?.code ?: "all"}")
            }
        }
        if (!cfg.ready || !cfg.forAssistant) {
            SetupCard(cfg.ready, onOpenSettings)
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (turns.isEmpty()) item("intro") {
                Column(Modifier.fillMaxWidth().testTag("assistant_intro"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.as_intro), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    assistantPrompts(cat).forEachIndexed { i, p ->
                        Text(p, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(yuRound(12)).background(GlassFill)
                            .clickable { send(p) }.padding(horizontal = 14.dp, vertical = 12.dp).testTag("assistant_prompt_$i"))
                    }
                }
            }
            itemsIndexed(turns, key = { i, t -> "$i-${t.at}" }) { _, t ->
                if (t.user) UserBubble(t.text) else AnswerBlock(t, onOpen = { r -> Discovery.opened(context, r); onOpenRec(r) },
                    onNotInterested = { r -> Discovery.dismiss(context, r); AppMessages.show(context, "You won't see ${r.title} in For You again") },
                    onRetry = { turns.lastOrNull { it.user }?.text?.let { q -> turns = turns.dropLast(2); send(q) } })
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
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(1_000) }, Modifier.weight(1f).testTag("assistant_input"), maxLines = 4,
                placeholder = { Text(if (cat == null) "Ask Yumeiro AI" else "Ask about ${cat.label.lowercase()}", color = TextTertiary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(input) }))
            Spacer(Modifier.width(8.dp))
            if (job != null) TextButton(onClick = { job?.cancel(); job = null; step = null; turns = turns.dropLastWhile { it.user } }, modifier = Modifier.testTag("assistant_stop")) { Text(stringResource(R.string.stop)) }
            else IconButton(onClick = { send(input) }, enabled = input.isNotBlank(), modifier = Modifier.testTag("assistant_send")) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) SpotifyGreen else TextTertiary)
            }
        }
    }

    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(), containerColor = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("assistant_history_sheet")) {
            Text(stringResource(R.string.as_past_chats), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(if (dev.asfakul.yumeiro.security.Privacy.incognito.value) R.string.as_history_incognito else R.string.as_history_hint), color = TextTertiary, fontSize = 12.sp)
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
            "The assistant uses your own free key from Google Gemini, OpenRouter, Groq or Cloudflare (or your own server). " +
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

@Composable
private fun AnswerBlock(t: AssistantChats.Turn, onOpen: (Discovery.Rec) -> Unit, onNotInterested: (Discovery.Rec) -> Unit, onRetry: () -> Unit) {
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

