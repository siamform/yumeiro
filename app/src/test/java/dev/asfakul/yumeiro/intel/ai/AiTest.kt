/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.intel.Aspect
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.Tag
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B21 S4: the AI providers (Gemini, OpenAI-compatible), the per-profile key and the taste context. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val realClient = AiHttp.client
    private var server: MockWebServer? = null

    @Before fun setUp() {
        dev.asfakul.yumeiro.AppStartupForTests.await()
        Profiles.resetForTests(ctx)
        AiSettings.clear(ctx); AiSettings.unload()
        AiHttp.client = { OkHttpClient() }
    }

    @After fun tearDown() {
        server?.close()
        AiHttp.client = realClient
        AiSettings.providerForTests = null
        AiSettings.clear(ctx); AiSettings.unload()
        Profiles.resetForTests(ctx)
    }

    private fun mock(vararg bodies: Pair<Int, String>): MockWebServer = MockWebServer().also { s ->
        s.start(); server = s
        bodies.forEach { (code, body) -> s.enqueue(MockResponse.Builder().code(code).body(body).build()) }
    }
    private fun base(s: MockWebServer) = s.url("/v1").toString().trimEnd('/')

    // region errors and helpers

    @Test fun httpErrorsBecomePlainMessagesWithoutTheKey() {
        assertTrue(AiHttp.mapError(401, "", null) is AiError.KeyRejected)
        assertTrue(AiHttp.mapError(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}""", "m") is AiError.KeyRejected)
        assertTrue(AiHttp.mapError(429, "", "m") is AiError.LimitReached)
        assertTrue(AiHttp.mapError(400, """{"error":{"message":"Quota exceeded for metric"}}""", "m") is AiError.LimitReached)
        val gone = AiHttp.mapError(404, "", "gemini-1.0-pro")
        assertTrue(gone is AiError.ModelGone && gone.message!!.contains("gemini-1.0-pro"))
        assertTrue(AiHttp.mapError(503, "", "m").message!!.contains("503"))
        val leak = AiHttp.mapError(400, """{"error":{"message":"bad url ?key=AIzaSECRET123&x=1"}}""", "m")
        assertFalse("a key in an error body is never shown", leak.message!!.contains("AIzaSECRET123"))
    }

    @Test fun firstJsonFindsTheObjectInsideFencesAndProse() {
        assertEquals(1, AiHttp.firstJson("""Sure! ```json
{"a":1,"b":{"c":"}"}}
``` hope it helps""")!!.getInt("a"))
        assertNull(AiHttp.firstJson("no json here"))
        assertEquals("ok", AiHttp.withoutThinking("<think>long\nthoughts</think>\n ok"))
    }

    @Test fun plainHttpOnlyForThisDeviceOrTheHomeNetwork() {
        listOf("localhost", "127.0.0.1", "192.168.1.20", "10.0.0.5", "172.20.1.1", "nas.local", "100.80.1.2").forEach { assertTrue(it, AiHttp.isLocal(it)) }
        listOf("8.8.8.8", "example.com", "172.40.0.1", "192.169.1.1").forEach { assertFalse(it, AiHttp.isLocal(it)) }
        try { AiHttp.url("http://example.com/v1", "/models"); fail("plain internet http is refused") } catch (_: AiError.Failed) {}
        assertEquals("api.groq.com", AiHttp.url("https://api.groq.com/openai/v1/", "/models").host)
    }

    // endregion

    // region Gemini

    @Test fun geminiListsOnlyChatModels() {
        val o = JSONObject("""{"models":[
            {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
            {"name":"models/gemini-embedding-001","supportedGenerationMethods":["generateContent"]},
            {"name":"models/imagen-4","supportedGenerationMethods":["predict"]}]}""")
        val m = GeminiProvider.parseModels(o)
        assertEquals(listOf("gemini-2.5-flash"), m.map { it.id })
        assertEquals("Gemini 2.5 Flash", m[0].name)
    }

    @Test fun geminiBodyAsksForJsonOnlyWithoutTheSearchTool() {
        val r = AiRequest("gemini-2.5-flash", "rules", listOf(AiMessage(true, "hi"), AiMessage(false, "hello"), AiMessage(true, "more")), json = true, webSearch = true)
        val withMime = GeminiProvider.body(r, jsonMime = true)
        assertEquals("rules", withMime.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("model", withMime.getJSONArray("contents").getJSONObject(1).getString("role"))
        assertEquals("application/json", withMime.getJSONObject("generationConfig").getString("responseMimeType"))
        assertTrue(withMime.getJSONArray("tools").getJSONObject(0).has("google_search"))
        assertFalse(GeminiProvider.body(r, jsonMime = false).getJSONObject("generationConfig").has("responseMimeType"))
    }

    @Test fun geminiReplySkipsThoughtsAndKeepsGroundingSources() {
        val o = JSONObject("""{"modelVersion":"gemini-2.5-flash-001","candidates":[{"content":{"parts":[
            {"text":"thinking…","thought":true},{"text":"Watch "},{"text":"Mushishi."}]},
            "groundingMetadata":{"webSearchQueries":["mushishi review"],"groundingChunks":[
              {"web":{"uri":"https://example.org/a","title":"Review A"}},{"web":{"uri":"https://example.org/a","title":"dup"}},{"web":{"uri":"http://insecure.example/b"}}]}}]}""")
        val r = GeminiProvider.parseReply(o, "gemini-2.5-flash")
        assertEquals("Watch Mushishi.", r.text)
        assertEquals("gemini-2.5-flash-001", r.model)
        assertEquals(listOf("https://example.org/a"), r.sources.map { it.url })
        assertTrue(r.searched)
        try { GeminiProvider.parseReply(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}"""), "m"); fail() } catch (e: AiError.Failed) { assertTrue(e.message!!.contains("SAFETY")) }
    }

    @Test fun geminiSendsTheKeyAsAHeaderNeverInTheUrl() = runBlocking {
        val s = mock(200 to """{"candidates":[{"content":{"parts":[{"text":"ready"}]}}]}""")
        val reply = GeminiProvider(base(s)).chat("AIzaTESTKEY", AiRequest("gemini-2.5-flash", "", listOf(AiMessage(true, "ping"))))
        assertEquals("ready", reply.text)
        val req = s.takeRequest()
        assertEquals("AIzaTESTKEY", req.headers[GeminiProvider.HEADER])
        assertFalse(req.url.toString().contains("AIzaTESTKEY"))
        assertTrue(req.url.encodedPath.endsWith("/models/gemini-2.5-flash:generateContent"))
    }

    @Test fun geminiRetriesWithoutJsonModeWhenAnOldModelRefusesIt() = runBlocking {
        val s = mock(400 to """{"error":{"message":"Tool use with a response mime type: 'application/json' is unsupported"}}""",
            200 to """{"candidates":[{"content":{"parts":[{"text":"{\"a\":1}"}]}}]}""")
        val r = GeminiProvider(base(s)).chat("k", AiRequest("gemini-2.0-flash", "", listOf(AiMessage(true, "q")), json = true, webSearch = false))
        assertEquals("{\"a\":1}", r.text)
        assertTrue(JSONObject(s.takeRequest().body!!.utf8()).getJSONObject("generationConfig").has("responseMimeType"))
        assertFalse(JSONObject(s.takeRequest().body!!.utf8()).getJSONObject("generationConfig").has("responseMimeType"))
    }

    // endregion

    // region OpenAI-compatible

    @Test fun openRouterListsFreeModelsFirst() {
        val o = JSONObject("""{"data":[{"id":"openai/gpt-5","name":"GPT-5"},{"id":"meta-llama/llama-4:free","name":"Llama 4"},{"id":"openai/whisper-1"}]}""")
        val m = OpenAiCompatProvider.parseModels(o, AiPreset.OPENROUTER)
        assertEquals(listOf("meta-llama/llama-4:free", "openai/gpt-5"), m.map { it.id })
        assertEquals("Llama 4 (free)", m[0].name)
        val cf = OpenAiCompatProvider.parseCloudflareModels(JSONObject("""{"result":[{"name":"@cf/meta/llama-3.1-8b-instruct"},{"name":"other"}]}"""))
        assertEquals(listOf("@cf/meta/llama-3.1-8b-instruct"), cf.map { it.id })
    }

    @Test fun openAiBodyAndReply() {
        val b = OpenAiCompatProvider.body(AiRequest("m", "sys", listOf(AiMessage(true, "q"), AiMessage(false, "a")), json = true), jsonMode = true)
        val msgs = b.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"))
        assertEquals("json_object", b.getJSONObject("response_format").getString("type"))
        val r = OpenAiCompatProvider.parseReply(JSONObject("""{"model":"x","choices":[{"message":{"content":"<think>hm</think>Answer"}}]}"""), "m")
        assertEquals("Answer", r.text)
        try { OpenAiCompatProvider.parseReply(JSONObject("""{"error":{"message":"No endpoints found"}}"""), "m"); fail() } catch (e: AiError.Failed) { assertTrue(e.message!!.contains("No endpoints")) }
    }

    @Test fun bearerKeyGoesOnlyToTheProvidersHostAndARejectedKeyIsSaid() = runBlocking {
        val s = mock(200 to """{"choices":[{"message":{"content":"hi"}}]}""", 401 to """{"error":{"message":"Invalid API Key"}}""")
        val p = OpenAiCompatProvider(AiPreset.GROQ, base(s))
        assertEquals("hi", p.chat("gsk_TEST", AiRequest("llama", "", listOf(AiMessage(true, "q")))).text)
        val req = s.takeRequest()
        assertEquals("Bearer gsk_TEST", req.headers["Authorization"])
        assertEquals("/v1/chat/completions", req.url.encodedPath)
        try { p.chat("bad", AiRequest("llama", "", listOf(AiMessage(true, "q")))); fail() } catch (_: AiError.KeyRejected) {}
    }

    @Test fun aLocalServerWithoutAKeySendsNoAuthorization() = runBlocking {
        val s = mock(200 to """{"data":[{"id":"qwen3:8b"}]}""")
        assertEquals(listOf("qwen3:8b"), OpenAiCompatProvider(AiPreset.CUSTOM, base(s)).models("").map { it.id })
        assertNull(s.takeRequest().headers["Authorization"])
    }

    @Test fun anUnreachableServerIsOffline() = runBlocking {
        val s = mock(); val url = base(s); s.close(); server = null
        try { OpenAiCompatProvider(AiPreset.CUSTOM, url).models(""); fail() } catch (_: AiError.Offline) {}
    }

    // endregion

    // region settings per profile

    @Test fun keysArePerProfileAndRemovingAProfileRemovesItsKey() {
        AiSettings.setProvider(ctx, AiPreset.GEMINI)
        AiSettings.setKey(ctx, "  AIzaOWNERKEY1234 ")
        AiSettings.setModel(ctx, "gemini-2.5-flash")
        assertTrue(AiSettings.get(ctx).ready)
        assertEquals("1234", AiSettings.keyHint(ctx))
        assertEquals("AIzaOWNERKEY1234", AiSettings.key(ctx))

        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        Profiles.select(ctx, kid.id)
        val k = AiSettings.get(ctx)
        assertNull("the other profile sees no provider", k.preset)
        assertFalse(k.hasKey)
        assertEquals("", AiSettings.key(ctx))
        AiSettings.setProvider(ctx, AiPreset.GROQ); AiSettings.setKey(ctx, "gsk_KIDKEY99")

        Profiles.select(ctx, Profiles.DEFAULT)
        assertEquals(AiPreset.GEMINI, AiSettings.get(ctx).preset)
        assertEquals("AIzaOWNERKEY1234", AiSettings.key(ctx))

        Profiles.remove(ctx, kid.id)
        dev.asfakul.yumeiro.online.OnlineSettings.init(ctx)
        assertNull("a removed profile's key is gone", dev.asfakul.yumeiro.online.OnlineSettings.secret(AiSettings.keyName(kid.id)))
    }

    @Test fun changingProviderForgetsTheKeyAndWebSearchIsGeminiOnly() {
        AiSettings.setProvider(ctx, AiPreset.GEMINI); AiSettings.setKey(ctx, "AIzaKEYKEYKEY"); AiSettings.setWebSearch(ctx, true)
        assertTrue(AiSettings.get(ctx).webSearch)
        AiSettings.setProvider(ctx, AiPreset.OPENROUTER)
        assertFalse(AiSettings.get(ctx).hasKey)
        AiSettings.setWebSearch(ctx, true)
        assertFalse(AiSettings.get(ctx).webSearch)
        AiSettings.setProvider(ctx, AiPreset.CLOUDFLARE); AiSettings.setKey(ctx, "cftokencftoken"); AiSettings.setModel(ctx, "@cf/x")
        AiSettings.setExtra(ctx, "not-an-account")
        assertNull("a bad account ID gives no provider", AiSettings.provider(AiSettings.get(ctx)))
        AiSettings.setExtra(ctx, "0123456789abcdef0123456789abcdef")
        assertTrue(AiSettings.provider(AiSettings.get(ctx)) is OpenAiCompatProvider)
    }

    @Test fun chatNeedsSetupAndClearRemovesEverything() = runBlocking {
        try { AiSettings.chat(ctx, AiRequest("", "", listOf(AiMessage(true, "q")))); fail() } catch (_: AiError.NotSetUp) {}
        AiSettings.setProvider(ctx, AiPreset.GROQ); AiSettings.setKey(ctx, "gsk_abcdefgh"); AiSettings.setModel(ctx, "llama")
        var seen: AiRequest? = null
        AiSettings.providerForTests = object : AiProvider {
            override val preset = AiPreset.GROQ
            override suspend fun models(key: String) = emptyList<AiModel>()
            override suspend fun chat(key: String, request: AiRequest): AiReply { seen = request; assertEquals("gsk_abcdefgh", key); return AiReply("ready", request.model) }
        }
        assertEquals("ready", AiSettings.test(ctx).text)
        assertEquals("llama", seen!!.model)
        assertFalse("web search is off for a provider without it", AiSettings.chat(ctx, AiRequest("", "", listOf(AiMessage(true, "q")), webSearch = true)).searched)
        assertFalse(seen!!.webSearch)
        assertTrue(AiSettings.get(ctx).testedAt > 0)
        AiSettings.clear(ctx)
        assertNull(AiSettings.get(ctx).preset)
        assertEquals("", AiSettings.key(ctx))
    }

    // endregion

    // region context

    private val now = 1_800_000_000_000L
    private fun entry(key: String, cat: MediaCategory, tags: List<Tag>, completed: Boolean = true, feedback: TasteSignals.Feedback? = null) =
        TasteSignals.Entry(
            key, cat, key.replaceFirstChar { it.uppercase() }, year = 2020, tags = tags, metaAt = now, opens = 1, lastOpenAt = now,
            parts = mapOf("_" to TasteSignals.Part(1f, true, now)), completions = if (completed) 1 else 0, completedAt = if (completed) now else 0,
            firstAt = now, lastAt = now, feedback = feedback, feedbackAt = if (feedback != null) now else 0,
        )
    private val dark = listOf(Tag("Psychological", Aspect.THEMES, 0.9f, false), Tag("Drama", Aspect.GENRE, 1f, false))

    @Test fun contextHoldsOnlyTheAskedCategoryAndStaysSmall() {
        val entries = listOf(entry("monster", MediaCategory.ANIME, dark), entry("erased", MediaCategory.ANIME, dark, feedback = TasteSignals.Feedback.LOVED),
            entry("berserk", MediaCategory.MANGA, dark))
        val anime = AiContext.build(entries, MediaCategory.ANIME, now, artists = listOf("Aimer"), channels = listOf("Gigguk"))
        val a = anime.json
        assertTrue(a.has("anime"))
        assertFalse("another category's titles stay out", a.toString().contains("Berserk"))
        assertFalse("music artists only for music or an overview", a.has("music_artists"))
        assertTrue(a.getJSONObject("anime").toString().contains("Monster (2020)"))
        assertTrue(a.getJSONObject("anime").getJSONObject("taste").toString().contains("Psychological"))

        val all = AiContext.build(entries, null, now, artists = listOf("Aimer"))
        assertTrue(all.json.has("manga") && all.json.has("music_artists"))

        val many = (1..400).map { entry("title number $it with a long name", MediaCategory.ANIME, dark) }
        assertTrue(AiContext.build(many, MediaCategory.ANIME, now).json.toString().length <= 4_096)
        assertTrue(AiContext.build(emptyList(), MediaCategory.ANIME, now).empty)
    }

    // endregion
}
