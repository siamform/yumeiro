/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * B21 S4: the user's own AI provider (bring your own key, optional). Nothing in Yumeiro needs it: For You, Similar by
 * story and the why sheet are metadata matching. With a key the assistant and AI subtitle translation work.
 *
 * Two shapes cover the free options researched (R27-B20-RESEARCH.md): Google Gemini (generateContent, optional Google
 * Search grounding) and the OpenAI-compatible Chat Completions API (OpenRouter, Groq, Cloudflare Workers AI, a custom
 * URL such as Ollama). The key goes only to the provider's own host ([AiHttp.scoped]) and is never logged.
 */
data class AiModel(val id: String, val name: String = id, val contextTokens: Int = 0)

data class AiMessage(val user: Boolean, val text: String)

data class AiRequest(
    val model: String,
    val system: String,
    val messages: List<AiMessage>,
    /** Ask for one JSON object back. */
    val json: Boolean = false,
    /** Gemini only: let the model search Google (sources come back in [AiReply.sources]). */
    val webSearch: Boolean = false,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.4,
)

data class AiSource(val title: String, val url: String)

data class AiReply(val text: String, val model: String, val sources: List<AiSource> = emptyList(), val searched: Boolean = false)

/** What went wrong, in words the user can act on. */
sealed class AiError(message: String) : Exception(message) {
    class KeyRejected : AiError("The provider rejected this key. Check it in Settings → Yumeiro AI.")
    class LimitReached : AiError("This key's free limit is used up for now. Try again later.")
    class ModelGone(model: String) : AiError("The model \"$model\" isn't available any more. Pick another in Settings → Yumeiro AI.")
    class Offline : AiError("Couldn't reach the AI provider. Check your connection.")
    class NotSetUp : AiError("Add an AI key in Settings → Yumeiro AI to use this.")
    class Failed(detail: String) : AiError(detail)
}

interface AiProvider {
    val preset: AiPreset
    suspend fun models(key: String): List<AiModel>
    suspend fun chat(key: String, request: AiRequest): AiReply
}

/** The providers offered in Settings (all have a free tier or run locally). */
enum class AiPreset(val code: String, val label: String, val base: String, val keyUrl: String, val note: String, val webSearch: Boolean = false) {
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "https://aistudio.google.com/apikey",
        "Free tier: Google may use free-tier prompts to improve its products.", webSearch = true),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/keys",
        "Free models end in \":free\"; their providers may log prompts."),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/keys",
        "Free tier with per-minute limits."),
    CLOUDFLARE("cloudflare", "Cloudflare Workers AI", "https://api.cloudflare.com/client/v4/accounts/{account}/ai/v1", "https://dash.cloudflare.com/profile/api-tokens",
        "Free daily allowance. Needs your account ID and an API token with Workers AI access."),
    CUSTOM("custom", "Custom (OpenAI-compatible)", "", "",
        "Any OpenAI-compatible server, such as Ollama on your network (http://<computer>:11434/v1).");

    companion object { fun of(code: String?): AiPreset? = entries.firstOrNull { it.code == code } }
}

/** HTTP for the providers: one client, the key kept to the provider's host, errors mapped to [AiError]. */
internal object AiHttp {
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val SECRET_IN_TEXT = Regex("(?i)(key|token)=[^&\\s]+")
    private val THINK = Regex("(?s)<think>.*?</think>")

    /** Drops a reasoning model's <think>…</think> block before the answer. */
    fun withoutThinking(text: String): String = text.replace(THINK, "").trim()
    /** Tests replace the client (MockWebServer). */
    @Volatile internal var client: () -> OkHttpClient = { dev.asfakul.yumeiro.network.NetworkClient.get() }

    /** Plain HTTP is only for a server on this device or the home network (Ollama); a key never crosses the internet in clear. */
    fun hostAllowed(url: HttpUrl): Boolean = url.isHttps || isLocal(url.host)

    internal fun isLocal(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h.endsWith(".local") || h.endsWith(".lan")) return true
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31) || (p[0] == 100 && p[1] in 64..127)
    }

    /** [client] whose every hop keeps [header] only on [base]'s host (and never over plain internet HTTP). */
    fun scoped(base: HttpUrl, header: String): OkHttpClient = client().newBuilder()
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .addNetworkInterceptor(dev.asfakul.yumeiro.online.subtitles.SubtitleNet.KeyScope(header) { u -> u.host.equals(base.host, true) && hostAllowed(u) })
        .build()

    fun url(base: String, path: String): HttpUrl =
        (base.trimEnd('/') + path).toHttpUrlOrNull()?.takeIf { hostAllowed(it) } ?: throw AiError.Failed("That server address isn't valid (use https, or http only on your own network).")

    suspend fun call(base: HttpUrl, header: String, value: String, url: HttpUrl, body: JSONObject?, model: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (value.isNotBlank()) b.header(header, value)
        if (body != null) b.post(body.toString().toRequestBody(JSON_TYPE))
        val res = try { scoped(base, header).newCall(b.build()).execute() } catch (e: java.io.IOException) { throw AiError.Offline() }
        res.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw mapError(r.code, text, model)
            runCatching { JSONObject(text) }.getOrElse { throw AiError.Failed("The provider sent an answer Yumeiro couldn't read.") }
        }
    }

    /** Pure: HTTP status + error body → [AiError] (the body's own message only when it names no key). */
    internal fun mapError(code: Int, body: String, model: String?): AiError {
        val msg = runCatching { JSONObject(body).let { o -> o.optJSONObject("error")?.optString("message") ?: o.optString("message") } }.getOrNull().orEmpty()
        val lower = msg.lowercase()
        return when {
            code == 401 || code == 403 || lower.contains("api key not valid") || lower.contains("api_key_invalid") || lower.contains("invalid api key") -> AiError.KeyRejected()
            code == 429 || lower.contains("quota") || lower.contains("rate limit") -> AiError.LimitReached()
            code == 404 && model != null -> AiError.ModelGone(model)
            code >= 500 -> AiError.Failed("The AI provider had a problem ($code). Try again in a moment.")
            else -> AiError.Failed(msg.take(160).replace(SECRET_IN_TEXT, "$1=…").ifBlank { "The AI provider refused the request ($code)." })
        }
    }

    /** The first JSON object in [text] (models sometimes wrap JSON in ``` fences or a sentence). */
    fun firstJson(text: String): JSONObject? {
        runCatching { return JSONObject(text.trim()) }
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
            when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull() }
            }
        }
        return null
    }
}

/** Google Gemini (AI Studio key): `x-goog-api-key`, `models` + `generateContent`. */
class GeminiProvider(private val base: String = AiPreset.GEMINI.base) : AiProvider {
    override val preset = AiPreset.GEMINI
    private val baseUrl: HttpUrl get() = AiHttp.url(base, "/")

    override suspend fun models(key: String): List<AiModel> {
        val out = ArrayList<AiModel>()
        var page: String? = null
        repeat(4) {
            val url = AiHttp.url(base, "/models").newBuilder().addQueryParameter("pageSize", "200").apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val o = AiHttp.call(baseUrl, HEADER, key, url, null)
            out += parseModels(o)
            page = o.optString("nextPageToken").ifBlank { null } ?: return out
        }
        return out
    }

    override suspend fun chat(key: String, request: AiRequest): AiReply {
        val url = AiHttp.url(base, "/models/${request.model.removePrefix("models/")}:generateContent")
        val o = try {
            AiHttp.call(baseUrl, HEADER, key, url, body(request, jsonMime = request.json && !request.webSearch), request.model)
        } catch (e: AiError.Failed) {
            // Older models refuse JSON mode together with a tool: ask again with JSON asked for in words only.
            if (request.json && e.message.orEmpty().contains("mime", true)) AiHttp.call(baseUrl, HEADER, key, url, body(request, jsonMime = false), request.model) else throw e
        }
        return parseReply(o, request.model)
    }

    companion object {
        const val HEADER = "x-goog-api-key"

        internal fun parseModels(o: JSONObject): List<AiModel> {
            val a = o.optJSONArray("models") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val m = a.optJSONObject(i) ?: return@mapNotNull null
                val methods = m.optJSONArray("supportedGenerationMethods")
                if (methods == null || (0 until methods.length()).none { methods.optString(it) == "generateContent" }) return@mapNotNull null
                val id = m.optString("name").removePrefix("models/").ifBlank { return@mapNotNull null }
                if (id.contains("embedding") || id.contains("imagen") || id.contains("-tts") || id.contains("image-generation")) return@mapNotNull null
                AiModel(id, m.optString("displayName").ifBlank { id }, m.optInt("inputTokenLimit"))
            }
        }

        internal fun body(r: AiRequest, jsonMime: Boolean): JSONObject = JSONObject().apply {
            if (r.system.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", r.system))))
            put("contents", JSONArray().apply {
                r.messages.forEach { m -> put(JSONObject().put("role", if (m.user) "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", m.text)))) }
            })
            put("generationConfig", JSONObject().put("temperature", r.temperature).put("maxOutputTokens", r.maxTokens).apply { if (jsonMime) put("responseMimeType", "application/json") })
            if (r.webSearch) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }

        internal fun parseReply(o: JSONObject, model: String): AiReply {
            val c = o.optJSONArray("candidates")?.optJSONObject(0)
            if (c == null) {
                val blocked = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw AiError.Failed(if (blocked.isNotBlank()) "The provider declined to answer this ($blocked)." else "The provider sent no answer.")
            }
            val parts = c.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.let { p -> (0 until p.length()).mapNotNull { p.optJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") } }.orEmpty()
            if (text.isBlank()) throw AiError.Failed(if (c.optString("finishReason") == "SAFETY") "The provider declined to answer this." else "The provider sent an empty answer.")
            val g = c.optJSONObject("groundingMetadata")
            val sources = g?.optJSONArray("groundingChunks")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("web") }.mapNotNull { w ->
                    val u = w.optString("uri").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    AiSource(w.optString("title").ifBlank { u.toHttpUrlOrNull()?.host ?: u }, u)
                }.distinctBy { it.url }
            }.orEmpty()
            val searched = g?.optJSONArray("webSearchQueries")?.length()?.let { it > 0 } == true || sources.isNotEmpty()
            return AiReply(text, o.optString("modelVersion").ifBlank { model }, sources, searched)
        }
    }
}

/** OpenAI-compatible Chat Completions (OpenRouter, Groq, Cloudflare Workers AI, Ollama / any custom server). */
class OpenAiCompatProvider(override val preset: AiPreset, private val base: String) : AiProvider {
    private val baseUrl: HttpUrl get() = AiHttp.url(base, "/")

    override suspend fun models(key: String): List<AiModel> {
        if (preset == AiPreset.CLOUDFLARE) {
            // Workers AI lists models under /ai/models/search, beside the OpenAI-compatible /ai/v1.
            val url = AiHttp.url(base.removeSuffix("/").removeSuffix("/v1"), "/models/search").newBuilder().addQueryParameter("task", "Text Generation").addQueryParameter("per_page", "100").build()
            return parseCloudflareModels(AiHttp.call(baseUrl, HEADER, bearer(key), url, null))
        }
        return parseModels(AiHttp.call(baseUrl, HEADER, bearer(key), AiHttp.url(base, "/models"), null), preset)
    }

    override suspend fun chat(key: String, request: AiRequest): AiReply {
        val url = AiHttp.url(base, "/chat/completions")
        val o = try {
            AiHttp.call(baseUrl, HEADER, bearer(key), url, body(request, jsonMode = request.json), request.model)
        } catch (e: AiError.Failed) {
            // Some models don't take response_format: the prompt still asks for JSON.
            if (request.json) AiHttp.call(baseUrl, HEADER, bearer(key), url, body(request, jsonMode = false), request.model) else throw e
        }
        return parseReply(o, request.model)
    }

    private fun bearer(key: String) = if (key.isBlank()) "" else "Bearer $key"

    companion object {
        const val HEADER = "Authorization"

        internal fun parseModels(o: JSONObject, preset: AiPreset): List<AiModel> {
            val a = o.optJSONArray("data") ?: o.optJSONArray("models") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val m = a.optJSONObject(i) ?: return@mapNotNull null
                val id = m.optString("id").ifBlank { m.optString("name") }.ifBlank { return@mapNotNull null }
                if (id.contains("whisper") || id.contains("embed") || id.contains("tts") || id.contains("guard")) return@mapNotNull null
                // OpenRouter lists paid and free models together: free ones are marked and listed first.
                val name = m.optString("name").ifBlank { id }
                AiModel(id, if (preset == AiPreset.OPENROUTER && id.endsWith(":free") && !name.contains("free", true)) "$name (free)" else name, m.optInt("context_length").takeIf { it > 0 } ?: m.optInt("context_window"))
            }.sortedWith(compareByDescending<AiModel> { it.id.endsWith(":free") }.thenBy { it.name.lowercase() })
        }

        internal fun parseCloudflareModels(o: JSONObject): List<AiModel> {
            val a = o.optJSONArray("result") ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { m ->
                val id = m.optString("name").takeIf { it.startsWith("@cf/") || it.startsWith("@hf/") } ?: return@mapNotNull null
                AiModel(id, id.substringAfterLast('/'))
            }
        }

        internal fun body(r: AiRequest, jsonMode: Boolean): JSONObject = JSONObject().apply {
            put("model", r.model)
            put("messages", JSONArray().apply {
                if (r.system.isNotBlank()) put(JSONObject().put("role", "system").put("content", r.system))
                r.messages.forEach { m -> put(JSONObject().put("role", if (m.user) "user" else "assistant").put("content", m.text)) }
            })
            put("temperature", r.temperature)
            put("max_tokens", r.maxTokens)
            if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
        }

        internal fun parseReply(o: JSONObject, model: String): AiReply {
            o.optJSONObject("error")?.let { throw AiError.Failed(it.optString("message").take(160).ifBlank { "The provider refused the request." }) }
            val msg = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: throw AiError.Failed("The provider sent no answer.")
            val text = msg.optString("content").takeIf { it.isNotBlank() && it != "null" } ?: throw AiError.Failed("The provider sent an empty answer.")
            // Reasoning models may put their thinking in <think>…</think> before the answer.
            return AiReply(AiHttp.withoutThinking(text), o.optString("model").ifBlank { model })
        }
    }
}
