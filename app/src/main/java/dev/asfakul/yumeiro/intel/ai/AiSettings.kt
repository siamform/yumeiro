/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * B21 S4: "Yumeiro AI" for the open profile. Provider, model and choices live in the profile's own prefs
 * (`intel_ai`, in [Profiles.SCOPED_PREFS]); the key is a secret sealed with the Keystore ([OnlineSettings.setSecret],
 * name `ai_key_p<profile>`), so one person's key is never used by another profile and leaves with that profile.
 */
object AiSettings {
    private const val PREFS = "intel_ai"

    data class Config(
        val preset: AiPreset? = null,
        val model: String = "",
        /** Custom server base URL (CUSTOM) or the Cloudflare account ID (CLOUDFLARE). */
        val extra: String = "",
        val hasKey: Boolean = false,
        val forAssistant: Boolean = true,
        val forSubtitles: Boolean = true,
        val webSearch: Boolean = false,
        val testedAt: Long = 0,
    ) {
        /** Ready to answer: a provider, a model and a key (a custom local server may run without one). */
        val ready: Boolean get() = preset != null && model.isNotBlank() && (hasKey || preset == AiPreset.CUSTOM) && (preset != AiPreset.CUSTOM || extra.isNotBlank()) &&
            (preset != AiPreset.CLOUDFLARE || extra.isNotBlank())
    }

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()
    @Volatile private var loadedFor: String? = null

    internal fun keyName(profileId: String) = "ai_key_p$profileId"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(Profiles.prefsName(c, PREFS), Context.MODE_PRIVATE)

    /** The open profile's settings (loaded once per profile). */
    fun get(c: Context): Config {
        val id = Profiles.currentId(c)
        if (loadedFor == id) return _config.value
        synchronized(this) {
            if (loadedFor == id) return _config.value
            OnlineSettings.init(c)
            val p = prefs(c)
            _config.value = Config(
                AiPreset.of(p.getString("provider", null)), p.getString("model", "").orEmpty(), p.getString("extra", "").orEmpty(),
                !OnlineSettings.secret(keyName(id)).isNullOrBlank(), p.getBoolean("assistant", true), p.getBoolean("subtitles", true),
                p.getBoolean("web", false), p.getLong("tested", 0),
            )
            loadedFor = id
        }
        return _config.value
    }

    private fun update(c: Context, change: (Config) -> Config) {
        val now = change(get(c))
        _config.value = now
        prefs(c).edit().putString("provider", now.preset?.code).putString("model", now.model).putString("extra", now.extra)
            .putBoolean("assistant", now.forAssistant).putBoolean("subtitles", now.forSubtitles).putBoolean("web", now.webSearch)
            .putLong("tested", now.testedAt).apply()
    }

    /** A new provider forgets the old model and key check (a key belongs to one provider). */
    fun setProvider(c: Context, preset: AiPreset?) = update(c) { old ->
        if (old.preset == preset) old else {
            OnlineSettings.setSecret(c, keyName(Profiles.currentId(c)), null)
            clearRemembered(c, old.preset)
            old.copy(preset = preset, model = "", extra = "", hasKey = false, webSearch = false, testedAt = 0)
        }
    }
    fun setModel(c: Context, model: String) = update(c) { it.copy(model = model.trim()) }
    fun setExtra(c: Context, extra: String) = update(c) { it.copy(extra = extra.trim(), testedAt = 0) }
    fun setForAssistant(c: Context, on: Boolean) = update(c) { it.copy(forAssistant = on) }
    fun setForSubtitles(c: Context, on: Boolean) = update(c) { it.copy(forSubtitles = on) }
    fun setWebSearch(c: Context, on: Boolean) = update(c) { it.copy(webSearch = on && it.preset?.webSearch == true) }
    internal fun markTested(c: Context, at: Long) = update(c) { it.copy(testedAt = at) }

    /** Stores the key sealed (blank = remove). Never logged, never shown again in full. */
    fun setKey(c: Context, key: String?) {
        val clean = key?.trim().orEmpty()
        OnlineSettings.setSecret(c, keyName(Profiles.currentId(c)), clean.ifBlank { null })
        clearRemembered(c, get(c).preset)
        update(c) { it.copy(hasKey = clean.isNotBlank(), testedAt = 0) }
    }

    /** The key's last four characters, for "Key ending …abcd". */
    fun keyHint(c: Context): String? {
        OnlineSettings.init(c)
        return OnlineSettings.secret(keyName(Profiles.currentId(c)))?.takeIf { it.length >= 8 }?.takeLast(4)
    }

    internal fun key(c: Context): String { OnlineSettings.init(c); return OnlineSettings.secret(keyName(Profiles.currentId(c))).orEmpty() }

    /** Removing a profile removes its key too. */
    fun forgetProfile(c: Context, profileId: String) {
        OnlineSettings.setSecret(c, keyName(profileId), null)
        if (loadedFor == profileId) unload()
    }

    /** Remove everything: key, provider and choices (Settings → Remove). */
    fun clear(c: Context) {
        OnlineSettings.setSecret(c, keyName(Profiles.currentId(c)), null)
        prefs(c).edit().clear().apply()
        _config.value = Config()
        _status.value = emptyMap()
    }

    fun unload() { loadedFor = null; _config.value = Config(); _status.value = emptyMap() }

    // region the provider

    /** Tests replace it to answer without a network. */
    @Volatile internal var providerForTests: AiProvider? = null

    /** The provider for [cfg], or null when it isn't set up. */
    fun provider(cfg: Config): AiProvider? {
        providerForTests?.let { return it }
        return when (val p = cfg.preset ?: return null) {
            AiPreset.GEMINI -> GeminiProvider()
            AiPreset.CLOUDFLARE -> cfg.extra.takeIf { ACCOUNT.matches(it) }?.let { OpenAiCompatProvider(p, p.base.replace("{account}", it)) }
            AiPreset.CUSTOM -> cfg.extra.takeIf { it.isNotBlank() }?.let { OpenAiCompatProvider(p, it.trimEnd('/')) }
            else -> OpenAiCompatProvider(p, p.base)
        }
    }

    private val ACCOUNT = Regex("[0-9a-fA-F]{32}")

    /** Lists the models the key can use (also proves the key works). The list is kept for the picker (offline too). */
    suspend fun models(c: Context): List<AiModel> {
        val cfg = get(c)
        val p = provider(cfg) ?: throw AiError.NotSetUp()
        if (!cfg.hasKey && cfg.preset != AiPreset.CUSTOM) throw AiError.NotSetUp()
        return p.models(key(c)).also { list -> if (list.isNotEmpty()) prefs(c).edit().putString(modelsKey(cfg.preset), AiCatalog.encodeModels(list)).apply() }
    }

    // region R28 item 15: remembered models, probe results and automatic choice

    private fun modelsKey(p: AiPreset?) = "models_" + (p?.code ?: "none")
    private fun statusKey(p: AiPreset?) = "status_" + (p?.code ?: "none")

    private val _status = MutableStateFlow<Map<String, AiModelStatus>>(emptyMap())
    /** Probe results for the open profile's provider (model id → worked or why not). */
    val status: StateFlow<Map<String, AiModelStatus>> = _status.asStateFlow()

    /** The last model list this key gave (empty before the first load). */
    fun cachedModels(c: Context): List<AiModel> = AiCatalog.decodeModels(prefs(c).getString(modelsKey(get(c).preset), null))

    fun loadStatus(c: Context): Map<String, AiModelStatus> = AiCatalog.decode(prefs(c).getString(statusKey(get(c).preset), null)).also { _status.value = it }

    internal fun putStatus(c: Context, model: String, s: AiModelStatus) {
        val now = loadStatus(c) + (model to s)
        _status.value = now
        prefs(c).edit().putString(statusKey(get(c).preset), AiCatalog.encode(now)).apply()
    }

    private fun clearRemembered(c: Context, p: AiPreset?) {
        prefs(c).edit().remove(modelsKey(p)).remove(statusKey(p)).apply()
        _status.value = emptyMap()
    }

    /** One tiny request to [model]; the answer is remembered as that model's status. Throws only [AiError.KeyRejected]. */
    suspend fun probe(c: Context, model: String, now: Long = System.currentTimeMillis()): AiModelStatus {
        val cfg = get(c)
        val p = provider(cfg) ?: throw AiError.NotSetUp()
        val s = try {
            p.chat(key(c), AiRequest(model, "Reply with the single word: ready", listOf(AiMessage(true, "Are you there?")), maxTokens = 16, temperature = 0.0))
            AiModelStatus(true, "", now)
        } catch (e: AiError.KeyRejected) { throw e } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            AiModelStatus(false, AiCatalog.reason(e), now)
        }
        putStatus(c, model, s)
        return s
    }

    data class AutoResult(val model: String?, val tried: Int, val models: List<AiModel>, val message: String)

    /**
     * After a key is saved: list the key's models, probe the best [AiCatalog.rank] candidates one by one (at most
     * [tries]) and select the first that answers. A model the person picked before and that still answers is kept.
     * [onStep] says what is happening in plain words. Throws [AiError] when the key or the provider fails outright.
     */
    suspend fun autoSetup(c: Context, tries: Int = 4, onStep: (String) -> Unit = {}): AutoResult {
        onStep("Checking the key…")
        val list = models(c)
        val cfg = get(c)
        val chat = AiCatalog.rank(cfg.preset, list)
        if (chat.isEmpty()) return AutoResult(null, 0, list, if (list.isEmpty()) "The key works, but it can't use any model." else "The key works, but none of its models can chat.")
        val order = (listOfNotNull(chat.firstOrNull { it.id == cfg.model }) + chat).distinctBy { it.id }.take(tries)
        order.forEachIndexed { i, m ->
            onStep("Trying ${m.name} (${i + 1} of ${order.size})…")
            val s = probe(c, m.id)
            if (s.ok) {
                setModel(c, m.id)
                markTested(c, s.at)
                return AutoResult(m.id, i + 1, list, "Ready: ${m.name} answers on this key.")
            }
        }
        return AutoResult(null, order.size, list, "The key works, but the ${order.size} best models didn't answer (${loadStatus(c)[order.first().id]?.reason ?: "no answer"}). Pick another model below.")
    }

    /** The selected model's context size from the remembered list (0 = unknown). */
    fun contextTokens(c: Context): Int = get(c).let { cfg -> cachedModels(c).firstOrNull { it.id == cfg.model }?.contextTokens ?: 0 }

    // endregion

    /** "Test connection": one tiny request to the chosen model. */
    suspend fun test(c: Context, now: Long = System.currentTimeMillis()): AiReply {
        val reply = chat(c, AiRequest(get(c).model, "Reply with the single word: ready", listOf(AiMessage(true, "Are you there?")), maxTokens = 16, temperature = 0.0))
        markTested(c, now)
        return reply
    }

    /** One request with the open profile's provider (callers check [Config.forAssistant] / [Config.forSubtitles]). */
    suspend fun chat(c: Context, request: AiRequest): AiReply {
        val cfg = get(c)
        if (!cfg.ready) throw AiError.NotSetUp()
        val p = provider(cfg) ?: throw AiError.NotSetUp()
        val r = request.copy(model = request.model.ifBlank { cfg.model }, webSearch = request.webSearch && cfg.webSearch && cfg.preset?.webSearch == true)
        return p.chat(key(c), r)
    }

    // endregion
}
