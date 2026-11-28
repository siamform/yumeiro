/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.intel.ai.AiPersonal
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.WarnTone

/**
 * R28 item 31: Settings → Yumeiro AI → Personalization. The person's own instructions, translation style, glossary
 * and remembered notes for this profile ([AiPersonal]). They are added after Yumeiro's rules, never instead of them;
 * the page says so in plain words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPersonalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { AiPersonal.get(context); true }
    val p by AiPersonal.prefs.collectAsState()
    var instructions by rememberSaveable { mutableStateOf(p.instructions) }
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().testTag("ai_personal")) {
        SubPageHeader(title = "Personalization", onBack = onBack, subtitle = "Yumeiro AI · this profile")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tell Yumeiro AI how to talk to you and what you like. This is added after Yumeiro's own rules: it changes tone, language, wording and taste, never safety, privacy or how answers are checked.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            GroupLabel("Instructions")
            OutlinedTextField(instructions, { instructions = it.take(AiPersonal.INSTRUCTIONS_MAX); saved = false }, minLines = 4, maxLines = 10,
                placeholder = { Text("For example: Answer in Banglish. I like slow-burn romance and dislike gore. Keep answers short.") },
                supportingText = { Text("${instructions.length} / ${AiPersonal.INSTRUCTIONS_MAX}", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth().testTag("ai_instructions"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { AiPersonal.setInstructions(context, instructions); instructions = AiPersonal.get(context).instructions; saved = true },
                    enabled = instructions != p.instructions, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.testTag("ai_instructions_save")) { Text("Save") }
                if (saved) Text("Saved", color = SpotifyGreen, fontSize = 13.sp, modifier = Modifier.testTag("ai_instructions_saved"))
            }

            GroupLabel("Use them for")
            SettingsCard {
                SettingsToggleRow("Chat", "Conversations with Yumeiro AI", p.forChat, { AiPersonal.setUse(context, chat = it) }, "ai_personal_chat")
                SettingsToggleRow("Recommendations", "Researched picks (/recom and \"suggest …\")", p.forRecommendations, { AiPersonal.setUse(context, recs = it) }, "ai_personal_recs")
                SettingsToggleRow("Subtitle translation", "Style and glossary for AI-translated subtitles", p.forSubtitles, { AiPersonal.setUse(context, subs = it) }, "ai_personal_subs")
            }

            GroupLabel("Translation style")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiPersonal.Style.entries.forEach { s -> FilterPill(s.label, p.style == s, onClick = { AiPersonal.setStyle(context, s) }, testTag = "ai_style_${s.code}") }
            }
            Text(p.style.hint, color = TextTertiary, fontSize = 12.sp)
            SettingsCard {
                SettingsToggleRow("Keep honorifics", "Leave -san, -kun, senpai, oppa and similar as they are", p.keepHonorifics, { AiPersonal.setKeepHonorifics(context, it) }, "ai_honorifics")
            }

            GroupLabel("Glossary")
            Text("Words that must always be translated your way, in subtitles and chat. In the chat you can also type /glossary add word = translation.",
                color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(source, { source = it }, singleLine = true, label = { Text("Word") }, modifier = Modifier.weight(1f).testTag("ai_term_source"))
                OutlinedTextField(target, { target = it }, singleLine = true, label = { Text("Translation") }, modifier = Modifier.weight(1f).testTag("ai_term_target"))
            }
            OutlinedButton(onClick = { if (AiPersonal.addTerm(context, source, target)) { source = ""; target = "" } },
                enabled = source.isNotBlank() && target.isNotBlank(), modifier = Modifier.testTag("ai_term_add")) { Text("Add to glossary") }
            if (p.glossary.isNotEmpty()) GlassGroup(Modifier.testTag("ai_glossary")) {
                p.glossary.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp).testTag("ai_term_${t.source}"), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.source, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("→", color = TextTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(t.target, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { AiPersonal.removeTerm(context, t.source) }) { Icon(Icons.Filled.Close, "Remove ${t.source}", tint = TextTertiary) }
                    }
                }
            }

            GroupLabel("Remembered")
            if (p.memory.isEmpty()) Text("Nothing yet. In the chat, type /remember and a note, for example: /remember I watch with Bangla subtitles.",
                color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("ai_memory_empty"))
            else GlassGroup(Modifier.testTag("ai_memory")) {
                p.memory.forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp).testTag("ai_memory_$i"), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { AiPersonal.forget(context, n) }) { Icon(Icons.Filled.Close, "Forget", tint = TextTertiary) }
                    }
                }
            }

            if (!p.empty) TextButton(onClick = { AiPersonal.clear(context); instructions = "" }, modifier = Modifier.testTag("ai_personal_clear")) {
                Text("Reset personalization", color = WarnTone)
            }
        }
    }
}
