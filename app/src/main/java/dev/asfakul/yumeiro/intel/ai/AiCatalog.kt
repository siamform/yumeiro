/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import org.json.JSONObject

/**
 * R28 item 15: what Yumeiro knows about keys and models, so the AI page can pick for the user.
 *
 *  - [AiKeys.guess] reads a pasted key's shape and names its provider (Gemini keys start "AIza", Groq "gsk_",
 *    OpenRouter "sk-or-", xAI "xai-", Anthropic "sk-ant-", Cerebras "csk-", DeepSeek "sk-" + 32 hex, OpenAI the
 *    other "sk-" keys). It never sends the key anywhere; it only decides which provider to try first.
 *  - [AiModelKind] keeps models that can't chat (embeddings, speech, images, moderation) in the list, marked as
 *    not supported, instead of hiding them: people see why a model they know is missing.
 *  - [rank] orders the chat models from "most likely to work well on a free key" down; the page probes the top
 *    few with one tiny request and selects the first that answers ([AiSettings.autoSetup]).
 *
 * Pure (no Android), unit-tested in AiCatalogTest.
 */
enum class AiModelKind { CHAT, UNSUPPORTED }

/** What the last probe of a model said. [reason] is short, plain words ("Free limit used up"). */
data class AiModelStatus(val ok: Boolean, val reason: String = "", val at: Long = 0)

object AiKeys {
    sealed class Guess {
        data class Provider(val preset: AiPreset) : Guess()
        /** A Google Cloud / OAuth token pasted where an AI Studio key belongs ("AQ.…", "ya29.…"). */
        data object GoogleToken : Guess()
        data object Unknown : Guess()
    }

    private val DEEPSEEK = Regex("^sk-[0-9a-f]{32}$")

    fun guess(raw: String): Guess {
        val k = raw.trim()
        return when {
            k.isEmpty() -> Guess.Unknown
            k.startsWith("AIza") -> Guess.Provider(AiPreset.GEMINI)
            k.startsWith("AQ.") || k.startsWith("ya29.") -> Guess.GoogleToken
            k.startsWith("sk-or-") -> Guess.Provider(AiPreset.OPENROUTER)
            k.startsWith("sk-ant-") -> Guess.Provider(AiPreset.ANTHROPIC)
            k.startsWith("gsk_") -> Guess.Provider(AiPreset.GROQ)
            k.startsWith("xai-") -> Guess.Provider(AiPreset.XAI)
            k.startsWith("csk-") -> Guess.Provider(AiPreset.CEREBRAS)
            DEEPSEEK.matches(k) -> Guess.Provider(AiPreset.DEEPSEEK)
            k.startsWith("sk-") -> Guess.Provider(AiPreset.OPENAI)
            else -> Guess.Unknown
        }
    }

    /** The provider to use for [key] when the person chose [chosen] (null = nothing chosen yet). */
    fun providerFor(key: String, chosen: AiPreset?): AiPreset? = when (val g = guess(key)) {
        is Guess.Provider -> if (chosen == null || chosen == AiPreset.CUSTOM) chosen ?: g.preset else chosen
        else -> chosen
    }

    /** A plain-words warning when [key] clearly belongs to another provider than [chosen], else null. */
    fun mismatch(key: String, chosen: AiPreset?): String? = when (val g = guess(key)) {
        is Guess.GoogleToken -> "This looks like a Google Cloud sign-in token, not a Gemini API key. Gemini keys from Google AI Studio start with \"AIza\"."
        is Guess.Provider -> if (chosen != null && chosen != AiPreset.CUSTOM && chosen != g.preset) "This looks like a ${g.preset.label} key, not a ${chosen.label} key." else null
        else -> null
    }
}

object AiCatalog {
    private val NOT_CHAT = Regex("(?i)(embed|whisper|tts|-audio|audio-|transcri|speech|realtime|moderation|guard|dall-e|imagen|image-gen|-image|sora|veo|lyria|rerank|computer-use|aqa|search-preview|clip|stable-diffusion|flux|bge-|ocr)")

    fun kindOf(id: String): AiModelKind = if (NOT_CHAT.containsMatchIn(id)) AiModelKind.UNSUPPORTED else AiModelKind.CHAT
    fun kindOf(m: AiModel): AiModelKind = if (!m.chat) AiModelKind.UNSUPPORTED else kindOf(m.id)

    /** Ids that are known to work well for chat on a free or low-cost key, best first, per provider (prefix match). */
    private val PREFERRED: Map<AiPreset, List<String>> = mapOf(
        AiPreset.GEMINI to listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.5-pro", "gemini-pro-latest"),
        AiPreset.OPENAI to listOf("gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini", "gpt-5", "gpt-4.1", "gpt-4o"),
        AiPreset.XAI to listOf("grok-4-fast", "grok-3-mini", "grok-4", "grok-3"),
        AiPreset.ANTHROPIC to listOf("claude-haiku", "claude-3-5-haiku", "claude-sonnet", "claude-3-7-sonnet"),
        AiPreset.MISTRAL to listOf("mistral-small-latest", "mistral-medium-latest", "open-mistral-nemo", "mistral-large-latest"),
        AiPreset.DEEPSEEK to listOf("deepseek-chat", "deepseek-reasoner"),
        AiPreset.GROQ to listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "meta-llama/llama-4", "qwen/qwen3", "llama-3.1-8b-instant"),
        AiPreset.CEREBRAS to listOf("gpt-oss-120b", "llama-3.3-70b", "qwen-3", "llama3.1-8b"),
        AiPreset.OPENROUTER to listOf("google/gemini-2.0-flash-exp:free", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct:free", "openai/gpt-oss-20b:free", "qwen/"),
        AiPreset.CLOUDFLARE to listOf("@cf/meta/llama-3.3-70b-instruct", "@cf/openai/gpt-oss", "@cf/meta/llama-3.1-8b-instruct", "@cf/mistral"),
    )

    private val VERSION = Regex("(\\d+(?:\\.\\d+)?)")

    /** Pure: chat models of [models], best candidate first (see the class note). */
    fun rank(preset: AiPreset?, models: List<AiModel>): List<AiModel> {
        val prefs = PREFERRED[preset].orEmpty()
        fun score(m: AiModel): Double {
            val id = m.id.lowercase()
            var s = 0.0
            val p = prefs.indexOfFirst { id.startsWith(it.lowercase()) || id.contains("/" + it.lowercase()) }
            if (p >= 0) s += 100.0 - p * 6
            if (id.endsWith(":free")) s += 25
            if (id.contains("flash") || id.contains("mini") || id.contains("instant") || id.contains("fast") || id.contains("haiku") || id.contains("small")) s += 6
            if (id.contains("preview") || id.contains("-exp") || id.contains("experimental")) s -= 8
            if (id.contains("vision") || id.contains("thinking") || id.contains("reasoner") || id.contains("deep-research")) s -= 5
            if (id.contains("nano") || id.contains("1b") || id.contains("3b")) s -= 10
            VERSION.find(id)?.groupValues?.get(1)?.toDoubleOrNull()?.let { s += it.coerceAtMost(9.0) }
            return s
        }
        return models.filter { kindOf(it) == AiModelKind.CHAT }.sortedByDescending { score(it) }
    }

    /** One group of rows in the model picker. */
    data class Group(val title: String, val models: List<AiModel>, val selectable: Boolean = true)

    /**
     * Pure: the picker's groups for [query]: "Working on this key" (probed OK), "Recommended" (the top of [rank] not yet
     * probed), "Free", "Other chat models", "Didn't answer" (probed, failed) and "Not for chat" (can't be chosen).
     * A model appears once, in the first group it fits.
     */
    fun groups(preset: AiPreset?, models: List<AiModel>, status: Map<String, AiModelStatus>, query: String = ""): List<Group> {
        val q = query.trim()
        val shown = models.filter { q.isBlank() || it.id.contains(q, true) || it.name.contains(q, true) }
        val chat = rank(preset, shown)
        val used = HashSet<String>()
        fun take(list: List<AiModel>) = list.filter { used.add(it.id) }
        val working = take(chat.filter { status[it.id]?.ok == true })
        val failed = chat.filter { status[it.id]?.ok == false }.map { it.id }.toSet()
        val recommended = take(chat.filter { it.id !in failed }.take(3))
        val free = take(chat.filter { it.id !in failed && (it.id.endsWith(":free") || it.name.contains("(free)")) })
        val other = take(chat.filter { it.id !in failed })
        val broken = take(chat.filter { it.id in failed })
        val unsupported = take(shown.filter { kindOf(it) == AiModelKind.UNSUPPORTED }.sortedBy { it.name.lowercase() })
        return listOf(
            Group("Working on this key", working), Group("Recommended", recommended), Group("Free", free),
            Group("Other chat models", other), Group("Didn't answer", broken), Group("Not for chat", unsupported, selectable = false),
        ).filter { it.models.isNotEmpty() }
    }

    /** Plain words for a failed probe. */
    fun reason(e: Throwable): String = when (e) {
        is AiError.LimitReached -> "Free limit used up for now"
        is AiError.ModelGone -> "Not available on this key"
        is AiError.KeyRejected -> "Key rejected"
        is AiError.Offline -> "Couldn't reach the provider"
        is AiError -> e.message.orEmpty().take(80).ifBlank { "Didn't answer" }
        else -> "Didn't answer"
    }

    // region status JSON (stored per profile and provider)

    fun encode(status: Map<String, AiModelStatus>): String = JSONObject().apply {
        status.entries.take(200).forEach { (id, s) -> put(id, JSONObject().put("ok", s.ok).put("r", s.reason).put("at", s.at)) }
    }.toString()

    fun decode(text: String?): Map<String, AiModelStatus> = runCatching {
        val o = JSONObject(text ?: return emptyMap())
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { AiModelStatus(it.optBoolean("ok"), it.optString("r"), it.optLong("at")) } }
    }.getOrDefault(emptyMap())

    fun encodeModels(models: List<AiModel>): String = org.json.JSONArray().apply {
        models.take(500).forEach { put(JSONObject().put("i", it.id).put("n", it.name).put("c", it.contextTokens).put("t", it.chat)) }
    }.toString()

    fun decodeModels(text: String?): List<AiModel> = runCatching {
        val a = org.json.JSONArray(text ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map { AiModel(it.getString("i"), it.optString("n").ifBlank { it.getString("i") }, it.optInt("c"), it.optBoolean("t", true)) }
    }.getOrDefault(emptyList())

    // endregion
}
