/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.intel.ai.AiError
import dev.asfakul.yumeiro.intel.ai.AiModel
import dev.asfakul.yumeiro.intel.ai.AiPreset
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.launch

/**
 * B21 S4: Settings → Yumeiro AI. Optional, per profile: pick a free provider, paste a key (sealed on this device),
 * load the key's models, test, and choose what may use it. Nothing else in the app depends on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { AiSettings.get(context); true }
    val cfg by AiSettings.config.collectAsState()
    var keyField by remember { mutableStateOf("") }
    var extraField by remember(cfg.preset) { mutableStateOf(cfg.extra) }
    var models by remember(cfg.preset, cfg.hasKey, cfg.extra) { mutableStateOf<List<AiModel>?>(null) }
    var filter by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var hint by remember(cfg.hasKey) { mutableStateOf(AiSettings.keyHint(context)) }

    fun run(label: String, block: suspend () -> String) {
        busy = label; message = null
        scope.launch {
            message = try { true to block() } catch (e: AiError) { false to e.message.orEmpty() } catch (e: Exception) { false to "Something went wrong (${e.javaClass.simpleName})." }
            busy = null
        }
    }
    fun loadModels() = run("models") {
        val list = AiSettings.models(context)
        models = list
        if (cfg.model.isBlank()) list.firstOrNull { it.id.contains("flash") || it.id.endsWith(":free") }?.let { AiSettings.setModel(context, it.id) }
        if (list.isEmpty()) "The key works, but it can't use any chat model." else "The key works: ${list.size} models."
    }
    // A saved key lists its models once on open (that also shows the key still works).
    LaunchedEffect(cfg.preset, cfg.hasKey) { if (cfg.preset != null && (cfg.hasKey || cfg.preset == AiPreset.CUSTOM && cfg.extra.isNotBlank()) && models == null) loadModels() }

    Column(Modifier.fillMaxSize().testTag("ai_settings")) {
        SubPageHeader(title = "Yumeiro AI", onBack = onBack, subtitle = "Optional · your own free key")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nothing in Yumeiro needs AI. For You, Similar by story and the reasons for each pick come from AniList and TMDB data. " +
                "With your own key, Yumeiro can also answer questions in the Assistant and translate subtitles with AI. The key is kept on this " +
                "device for this profile only, sealed by Android's Keystore.", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            GroupLabel("Provider")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiPreset.entries.forEach { p ->
                    FilterPill(p.label, cfg.preset == p, onClick = { AiSettings.setProvider(context, p); keyField = ""; message = null }, testTag = "ai_provider_${p.code}")
                }
            }
            cfg.preset?.let { p ->
                Text(p.note, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("ai_provider_note"))
                if (p.keyUrl.isNotBlank()) TextButton(onClick = { dev.asfakul.yumeiro.ui.screens.extras.Links.open(context, p.keyUrl) }, modifier = Modifier.testTag("ai_get_key")) {
                    Text("Get a free key from ${p.label}", color = SpotifyGreen, fontSize = 13.sp)
                }
                if (p == AiPreset.CLOUDFLARE || p == AiPreset.CUSTOM) {
                    OutlinedTextField(extraField, { extraField = it }, singleLine = true,
                        label = { Text(if (p == AiPreset.CLOUDFLARE) "Account ID" else "Server address (…/v1)") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (p == AiPreset.CUSTOM) KeyboardType.Uri else KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("ai_extra"))
                    if (extraField.trim() != cfg.extra) OutlinedButton(onClick = { AiSettings.setExtra(context, extraField) }, modifier = Modifier.testTag("ai_extra_save")) { Text("Save address") }
                }

                GroupLabel("Key")
                if (cfg.hasKey) {
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Key saved", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(hint?.let { "Ending …$it" } ?: "Sealed on this device", color = TextTertiary, fontSize = 12.5.sp, modifier = Modifier.testTag("ai_key_hint"))
                            }
                            TextButton(onClick = { AiSettings.setKey(context, null); hint = null; models = null; message = null }, modifier = Modifier.testTag("ai_key_remove")) {
                                Text("Remove", color = dev.asfakul.yumeiro.ui.theme.WarnTone)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(keyField, { keyField = it; message = null }, singleLine = true, label = { Text(if (p == AiPreset.CUSTOM) "Key (if the server needs one)" else "API key") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("ai_key_field"))
                    Button(onClick = { AiSettings.setKey(context, keyField); keyField = ""; hint = AiSettings.keyHint(context); loadModels() }, enabled = keyField.isNotBlank() && busy == null,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("ai_key_save")) { Text("Save and check") }
                }

                if (busy != null) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth().testTag("ai_busy"))
                message?.let { (ok, text) ->
                    Text(text, color = if (ok) SpotifyGreen else dev.asfakul.yumeiro.ui.theme.WarnTone, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().testTag(if (ok) "ai_ok" else "ai_error").semantics { liveRegion = LiveRegionMode.Polite })
                }

                if (cfg.hasKey || p == AiPreset.CUSTOM) {
                    GroupLabel("Model")
                    Text(if (cfg.model.isBlank()) "No model chosen yet" else "Using ${cfg.model}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("ai_model_current"))
                    val list = models
                    if (list == null) OutlinedButton(onClick = { loadModels() }, enabled = busy == null, modifier = Modifier.testTag("ai_models_load")) { Text("Load models") }
                    else if (list.isNotEmpty()) {
                        if (list.size > 8) OutlinedTextField(filter, { filter = it }, singleLine = true, label = { Text("Find a model") }, modifier = Modifier.fillMaxWidth().testTag("ai_model_filter"))
                        val shown = list.filter { filter.isBlank() || it.id.contains(filter.trim(), true) || it.name.contains(filter.trim(), true) }.take(40)
                        Card(Modifier.fillMaxWidth(), shape = yuRound(14), colors = CardDefaults.cardColors(containerColor = GlassFill)) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                shown.forEach { m ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { AiSettings.setModel(context, m.id) }.padding(horizontal = 8.dp).testTag("ai_model_${m.id}"),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(cfg.model == m.id, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(m.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                                            if (m.name != m.id) Text(m.id, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (cfg.model.isNotBlank()) OutlinedButton(onClick = {
                        run("test") { val r = AiSettings.test(context); "Connected to ${r.model}." }
                    }, enabled = busy == null && cfg.ready, modifier = Modifier.testTag("ai_test")) { Text("Test connection") }
                }

                GroupLabel("Use it for")
                SettingsCard {
                    SettingsToggleRow("Assistant", "Answer questions about what to watch or read next, from your taste", cfg.forAssistant, { AiSettings.setForAssistant(context, it) }, "ai_use_assistant")
                    SettingsToggleRow("Subtitle translation", "Translate subtitles with AI (otherwise the on-device translator is used)", cfg.forSubtitles, { AiSettings.setForSubtitles(context, it) }, "ai_use_subtitles")
                    if (p.webSearch) SettingsToggleRow("Search the web", "Let ${p.label} check Google for reviews and release facts (sources are shown)", cfg.webSearch, { AiSettings.setWebSearch(context, it) }, "ai_use_web")
                }
                TextButton(onClick = { AiSettings.clear(context); keyField = ""; extraField = ""; models = null; message = null; hint = null }, modifier = Modifier.testTag("ai_clear")) {
                    Text("Remove AI from this profile", color = dev.asfakul.yumeiro.ui.theme.WarnTone)
                }
            }

            GroupLabel("What needs AI")
            Text("• Assistant: needs a key.\n• Subtitle translation: works without a key on phones (on-device translator); AI is used when chosen or on TV.\n" +
                "• For You, Similar by story, the why sheet and Not interested: never need AI.\n" +
                "What is sent: your question, the taste summary of this profile for that kind of media (titles and tags, nothing else), " +
                "and the facts Yumeiro found. Reddit and X can't be searched (no free API).",
                color = TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.testTag("ai_what_needs"))
        }
    }
}
