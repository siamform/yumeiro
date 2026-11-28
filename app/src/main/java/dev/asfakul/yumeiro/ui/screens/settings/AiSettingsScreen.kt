/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.intel.ai.AiCatalog
import dev.asfakul.yumeiro.intel.ai.AiError
import dev.asfakul.yumeiro.intel.ai.AiKeys
import dev.asfakul.yumeiro.intel.ai.AiModel
import dev.asfakul.yumeiro.intel.ai.AiModelStatus
import dev.asfakul.yumeiro.intel.ai.AiPersonal
import dev.asfakul.yumeiro.intel.ai.AiPreset
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.WarnTone
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.launch

/**
 * Settings → Yumeiro AI (B21 S4, redesigned in R28 item 15). Per profile and optional.
 *
 *  1. A status card says in one line whether AI is ready, with which provider and model.
 *  2. Provider: one row opening a grouped chooser (free tier · paid · your own server).
 *  3. Key: pasting checks the key's shape ([AiKeys]) and warns when it belongs to another provider (or is a Google
 *     sign-in token, not an API key). "Save and set up" lists the key's models, tries the best few and selects the
 *     first that answers ([AiSettings.autoSetup]); every step is shown.
 *  4. Model: one row opening a searchable picker, grouped by [AiCatalog.groups] with what is known about each model
 *     (works / didn't answer and why / not for chat), so nobody scrolls a raw list of 300 ids.
 *  5. What it's used for, Personalization (item 31), help for free keys, remove.
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit, onPersonal: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { AiSettings.get(context); AiSettings.loadStatus(context); true }
    val cfg by AiSettings.config.collectAsState()
    val status by AiSettings.status.collectAsState()
    val personal by AiPersonal.prefs.collectAsState()
    remember { AiPersonal.get(context); true }
    var keyField by remember { mutableStateOf("") }
    var extraField by remember(cfg.preset) { mutableStateOf(cfg.extra) }
    var models by remember(cfg.preset, cfg.hasKey, cfg.extra) { mutableStateOf(AiSettings.cachedModels(context).takeIf { it.isNotEmpty() }) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var hint by remember(cfg.hasKey) { mutableStateOf(AiSettings.keyHint(context)) }
    var providerOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    fun run(label: String, block: suspend () -> String) {
        busy = label; message = null
        scope.launch {
            message = try { true to block() } catch (e: AiError) { false to e.message.orEmpty() } catch (e: Exception) { false to "Something went wrong (${e.javaClass.simpleName})." }
            busy = null
        }
    }
    fun loadModels() = run("Loading models…") {
        val list = AiSettings.models(context)
        models = list
        val chat = AiCatalog.rank(cfg.preset, list)
        if (cfg.model.isBlank()) chat.firstOrNull()?.let { AiSettings.setModel(context, it.id) }
        val who = if (cfg.hasKey) "The key works" else "The server answered"
        if (chat.isEmpty()) "$who, but it can't use any chat model." else "$who: ${chat.size} chat models."
    }
    fun autoSetup() = run("Checking the key…") {
        val r = AiSettings.autoSetup(context) { step -> busy = step }
        models = r.models
        if (r.model == null && r.tried > 0) throw AiError.Failed(r.message)
        r.message
    }
    // A saved key with no remembered list lists its models once on open (that also shows the key still works).
    LaunchedEffect(cfg.preset, cfg.hasKey, cfg.extra) { if (cfg.preset != null && (cfg.hasKey || cfg.preset == AiPreset.CUSTOM && cfg.extra.isNotBlank()) && models == null) loadModels() }

    Column(Modifier.fillMaxSize().testTag("ai_settings")) {
        SubPageHeader(title = "Yumeiro AI", onBack = onBack, subtitle = "Optional · your own key") {
            GuideHelpButton(SetupGuides.aiKeys, tag = "ai_help")
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusCard(cfg, models, status)
            Text(stringResource(R.string.ai_intro), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            GroupLabel("Provider")
            GlassGroup {
                PickRow(Icons.Filled.Hub, "Provider", cfg.preset?.label ?: "Choose a provider", cfg.preset?.let { if (it.free) "Free" else if (it == AiPreset.CUSTOM) null else "Paid" }, "ai_provider") { providerOpen = true }
            }
            cfg.preset?.let { p ->
                Text(p.note, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("ai_provider_note"))
                if (p.keyUrl.isNotBlank()) TextButton(onClick = { dev.asfakul.yumeiro.ui.screens.extras.Links.open(context, p.keyUrl) }, modifier = Modifier.testTag("ai_get_key")) {
                    Text(stringResource(R.string.ai_get_free_key, p.label), color = SpotifyGreen, fontSize = 13.sp)
                }
                if (p == AiPreset.CLOUDFLARE || p == AiPreset.CUSTOM) {
                    OutlinedTextField(extraField, { extraField = it }, singleLine = true,
                        label = { Text(if (p == AiPreset.CLOUDFLARE) "Account ID" else "Server address (…/v1)") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (p == AiPreset.CUSTOM) KeyboardType.Uri else KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("ai_extra"))
                    if (extraField.trim() != cfg.extra) OutlinedButton(onClick = { AiSettings.setExtra(context, extraField) }, modifier = Modifier.testTag("ai_extra_save")) { Text(stringResource(R.string.ai_save_address)) }
                }

                GroupLabel("Key")
                if (cfg.hasKey) {
                    GlassGroup {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            IconTile(Icons.Filled.Key, SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ai_key_saved), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(hint?.let { "Ending …$it" } ?: "Sealed on this device", color = TextTertiary, fontSize = 12.5.sp, modifier = Modifier.testTag("ai_key_hint"))
                            }
                            TextButton(onClick = { AiSettings.setKey(context, null); hint = null; models = null; message = null }, modifier = Modifier.testTag("ai_key_remove")) {
                                Text(stringResource(R.string.remove_2), color = WarnTone)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(keyField, { keyField = it.trim(); message = null }, singleLine = true, label = { Text(if (p == AiPreset.CUSTOM) "Key (if the server needs one)" else "API key") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("ai_key_field"))
                    val mismatch = remember(keyField, p) { if (keyField.length >= 8) AiKeys.mismatch(keyField, p) else null }
                    val other = remember(keyField, p) { (AiKeys.guess(keyField) as? AiKeys.Guess.Provider)?.preset?.takeIf { it != p && p != AiPreset.CUSTOM } }
                    AnimatedVisibility(mismatch != null, enter = fadeIn(tween(160)) + expandVertically(tween(200)), exit = fadeOut(tween(120)) + shrinkVertically(tween(160))) {
                        Column(Modifier.fillMaxWidth().clip(yuRound(14)).background(WarnTone.copy(alpha = 0.12f)).padding(12.dp).testTag("ai_key_mismatch")) {
                            Text(mismatch.orEmpty(), color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                            if (other != null) TextButton(onClick = { AiSettings.setProvider(context, other); message = null }, modifier = Modifier.testTag("ai_key_switch")) {
                                Text("Switch to ${other.label}", color = SpotifyGreen)
                            }
                        }
                    }
                    Button(onClick = { AiSettings.setKey(context, keyField); keyField = ""; hint = AiSettings.keyHint(context); autoSetup() },
                        enabled = keyField.isNotBlank() && busy == null && AiKeys.guess(keyField) != AiKeys.Guess.GoogleToken,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("ai_key_save")) { Text("Save and set up") }
                }

                busy?.let { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth().testTag("ai_busy"))
                        Text(step, color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.testTag("ai_step").semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                message?.let { (ok, text) ->
                    Text(text, color = if (ok) SpotifyGreen else WarnTone, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth().testTag(if (ok) "ai_ok" else "ai_error").semantics { liveRegion = LiveRegionMode.Polite })
                }

                if (cfg.hasKey || p == AiPreset.CUSTOM) {
                    GroupLabel("Model")
                    val list = models
                    val current = list?.firstOrNull { it.id == cfg.model }
                    val st = status[cfg.model]
                    GlassGroup {
                        PickRow(Icons.Filled.Psychology, "Model", current?.name ?: cfg.model.ifBlank { if (list == null) "Load the key's models first" else "Choose a model" },
                            st?.let { if (it.ok) "Works" else "Didn't answer" }, "ai_model_row", statusOk = st?.ok) { if (list != null) pickerOpen = true else loadModels() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (list == null) OutlinedButton(onClick = { loadModels() }, enabled = busy == null, modifier = Modifier.testTag("ai_models_load")) { Text(stringResource(R.string.ai_load_models)) }
                        else OutlinedButton(onClick = { autoSetup() }, enabled = busy == null, modifier = Modifier.testTag("ai_auto")) {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Pick for me")
                        }
                        if (cfg.model.isNotBlank()) OutlinedButton(onClick = {
                            run("Testing ${current?.name ?: cfg.model}…") {
                                val r = AiSettings.test(context)
                                AiSettings.putStatus(context, cfg.model, AiModelStatus(true, "", System.currentTimeMillis()))
                                "Connected to ${r.model}."
                            }
                        }, enabled = busy == null && cfg.ready, modifier = Modifier.testTag("ai_test")) { Text(stringResource(R.string.ai_test_connection)) }
                    }
                }

                GroupLabel("Use it for")
                SettingsCard {
                    SettingsToggleRow("Assistant", "Chat about what to watch or read next, from your taste", cfg.forAssistant, { AiSettings.setForAssistant(context, it) }, "ai_use_assistant")
                    SettingsToggleRow("Subtitle translation", "Translate subtitles with AI (otherwise the on-device translator is used)", cfg.forSubtitles, { AiSettings.setForSubtitles(context, it) }, "ai_use_subtitles")
                    if (p.webSearch) SettingsToggleRow("Search the web", "Let ${p.label} check Google for reviews and release facts (sources are shown)", cfg.webSearch, { AiSettings.setWebSearch(context, it) }, "ai_use_web")
                }
            }

            GroupLabel("Personalization")
            GlassGroup {
                val summary = when {
                    personal.empty -> "Your instructions, translation style and glossary"
                    else -> listOfNotNull(
                        "Instructions set".takeIf { personal.instructions.isNotBlank() },
                        "${personal.glossary.size} glossary terms".takeIf { personal.glossary.isNotEmpty() },
                        "${personal.memory.size} notes".takeIf { personal.memory.isNotEmpty() },
                        personal.style.label.takeIf { personal.style != AiPersonal.Style.NATURAL },
                    ).joinToString(" · ").ifBlank { "Custom translation style" }
                }
                CategoryRow(Icons.Filled.Tune, SpotifyGreen, "Personalization", summary, "ai_personal_open", onClick = onPersonal)
            }

            if (cfg.preset != null) TextButton(onClick = { AiSettings.clear(context); keyField = ""; extraField = ""; models = null; message = null; hint = null }, modifier = Modifier.testTag("ai_clear")) {
                Text(stringResource(R.string.ai_remove_profile), color = WarnTone)
            }

            GroupLabel("What needs AI")
            Text(stringResource(R.string.ai_what_needs), color = TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.testTag("ai_what_needs"))
        }
    }

    if (providerOpen) ProviderDialog(cfg.preset, onDismiss = { providerOpen = false }) { p ->
        providerOpen = false
        if (p != cfg.preset) { AiSettings.setProvider(context, p); keyField = ""; message = null; models = null }
    }
    val list = models
    if (pickerOpen && list != null) ModelPickerDialog(cfg.preset, list, status, cfg.model, onDismiss = { pickerOpen = false }) { m ->
        pickerOpen = false
        AiSettings.setModel(context, m.id)
        run("Trying ${m.name}…") {
            val s = AiSettings.probe(context, m.id)
            if (s.ok) { AiSettings.markTested(context, s.at); "${m.name} answers on this key." } else throw AiError.Failed("${m.name}: ${s.reason}. You can keep it or pick another.")
        }
    }
}

@Composable
private fun StatusCard(cfg: AiSettings.Config, models: List<AiModel>?, status: Map<String, AiModelStatus>) {
    val name = models?.firstOrNull { it.id == cfg.model }?.name ?: cfg.model
    val (ok, title, line) = when {
        cfg.preset == null -> Triple(null, "Not set up", "Choose a provider and paste a key. Everything else in Yumeiro works without AI.")
        !cfg.hasKey && cfg.preset != AiPreset.CUSTOM -> Triple(null, "Add a key", "${cfg.preset.label} is chosen; paste its key below.")
        cfg.model.isBlank() -> Triple(null, "Choose a model", "The key is saved. Pick a model or let Yumeiro pick one.")
        status[cfg.model]?.ok == false -> Triple(false, "Model didn't answer", "${cfg.preset.label} · $name: ${status[cfg.model]?.reason}")
        else -> Triple(true, "Ready", "${cfg.preset.label} · $name")
    }
    GlassGroup(Modifier.testTag("ai_status")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = when (ok) { true -> SpotifyGreen; false -> WarnTone; null -> TextTertiary }
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.testTag("ai_status_title"))
                Text(line, color = TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A settings row that opens a chooser: icon, label, the current value and a chevron. */
@Composable
private fun PickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, badge: String?, tag: String, statusOk: Boolean? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(yuRound(16)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, SpotifyGreen)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextTertiary, fontSize = 12.sp)
            Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("${tag}_value"))
        }
        if (badge != null) { Spacer(Modifier.width(8.dp)); StatusChip(badge, statusOk ?: true) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
    }
}

@Composable
private fun ProviderDialog(current: AiPreset?, onDismiss: () -> Unit, onPick: (AiPreset) -> Unit) {
    TsukiDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text("Choose a provider") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp).testTag("ai_provider_list")) {
                AiPreset.entries.groupBy { it.group }.forEach { (group, presets) ->
                    item(key = "g_$group") { Text(group, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                    items(presets, key = { it.code }) { p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(12)).clickable { onPick(p) }.padding(horizontal = 8.dp, vertical = 6.dp).testTag("ai_provider_${p.code}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, color = TextPrimary, fontSize = 15.sp, fontWeight = if (p == current) FontWeight.SemiBold else FontWeight.Normal)
                                Text(p.note, color = TextTertiary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (p.webSearch) { Spacer(Modifier.width(6.dp)); StatusChip("Web search", null) }
                            if (p == current) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Check, "Selected", tint = SpotifyGreen) }
                        }
                    }
                }
            }
        })
}

@Composable
private fun ModelPickerDialog(preset: AiPreset?, models: List<AiModel>, status: Map<String, AiModelStatus>, selected: String, onDismiss: () -> Unit, onPick: (AiModel) -> Unit) {
    var query by remember { mutableStateOf("") }
    val groups = remember(models, status, query) { AiCatalog.groups(preset, models, status, query) }
    TsukiDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text("Choose a model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search ${models.size} models") }, modifier = Modifier.fillMaxWidth().testTag("ai_model_filter"))
                if (groups.isEmpty()) Text("No model matches \"$query\".", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.testTag("ai_model_none"))
                LazyColumn(Modifier.heightIn(max = 420.dp).testTag("ai_model_list")) {
                    groups.forEach { g ->
                        item(key = "g_${g.title}") {
                            Text("${g.title} · ${g.models.size}", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).testTag("ai_group_${g.title}"))
                        }
                        items(g.models, key = { g.title + "|" + it.id }) { m ->
                            val st = status[m.id]
                            val line = when {
                                !g.selectable -> "Can't chat (${if (m.id.contains("embed", true)) "embeddings" else "not a text model"})"
                                st?.ok == true -> "Works on this key"
                                st?.ok == false -> st.reason
                                m.contextTokens > 0 -> "${m.contextTokens / 1000}K context"
                                else -> m.id.takeIf { it != m.name }.orEmpty()
                            }
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(12)).let { if (g.selectable) it.clickable { onPick(m) } else it }
                                .padding(horizontal = 8.dp, vertical = 6.dp).testTag("ai_model_${m.id}"), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, color = if (g.selectable) TextPrimary else TextTertiary, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (m.id == selected) FontWeight.SemiBold else FontWeight.Normal)
                                    if (line.isNotBlank()) Text(line, color = if (st?.ok == false) WarnTone else TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (m.id == selected) Icon(Icons.Filled.Check, "Selected", tint = SpotifyGreen)
                            }
                        }
                    }
                }
            }
        })
}
