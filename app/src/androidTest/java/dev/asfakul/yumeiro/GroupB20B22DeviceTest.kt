/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.BackupCrypt
import dev.asfakul.yumeiro.backup.PendingRestore
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.intel.ai.AiPreset
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.intel.ai.AssistantChats
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.SecretBox
import dev.asfakul.yumeiro.sources.manga.MangaTranslate
import dev.asfakul.yumeiro.subtitles.translate.SubtitleTranslate
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.intel.AssistantScreen
import dev.asfakul.yumeiro.ui.screens.settings.AiSettingsScreen
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.zip.ZipInputStream

/**
 * B20–B22 on a real device, no real AI key (the CI has none):
 * a) Yumeiro AI settings with a custom OpenAI-compatible server (the "Ollama on your network" path) on a local
 *    MockWebServer: provider, address, models listed, model picked, Test connection OK, no key sent;
 * b) the Assistant shows its setup card without AI and its start screen once AI is ready;
 * c) profile isolation: AI settings and key, past chats and taste signals of one profile are invisible in another;
 * d) subtitle translation of the playing episode's English track: on phones with the on-device engine (real ML Kit
 *    pack download) into Bengali, and with Yumeiro AI (the local server) everywhere; on TV without AI it says why;
 * e) a password-locked backup carries the AI key, needs the password, refuses a wrong one, and a restore staging
 *    seals the key again with this device's Keystore; a plain backup never carries it.
 * The server's answers are test doubles for the remote service; everything on the device is the shipping code.
 * Log tag YuB20.
 */
@RunWith(AndroidJUnit4::class)
class GroupB20B22DeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuB20", s)
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val calls = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val tag = System.nanoTime().toString().takeLast(6)
    private var extraProfile: String? = null
    private var startProfile: String = Profiles.DEFAULT

    private fun has(t: String) = compose.onAllNodesWithTag(t, true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("gb20_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("timed out: $what", ok)
    }
    private fun tap(t: String) {
        runCatching { compose.onNodeWithTag(t, true).performScrollTo() }
        compose.waitForIdle()
        compose.onNodeWithTag(t, true).performClick()
        compose.waitForIdle()
    }

    private val vtt = """WEBVTT

00:00:01.000 --> 00:00:03.000
Good morning.

00:00:04.000 --> 00:00:06.000
Where is the station?

00:00:07.000 --> 00:00:09.000
Thank you very much.
"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls += request
                val p = request.url.encodedPath
                return when {
                    p == "/v1/models" -> json(JSONObject().put("data", JSONArray().put(JSONObject().put("id", "yu-local").put("context_length", 8192))))
                    p == "/v1/chat/completions" -> json(chatReply(JSONObject(request.body?.utf8().orEmpty())))
                    p == "/sub/en.vtt" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/vtt").body(vtt).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        Profiles.init(ctx)
        startProfile = Profiles.currentId(ctx)
        AiSettings.clear(ctx)
    }

    @After fun tearDown() {
        runCatching { SubtitleTranslate.cancel() }
        runCatching { compose.runOnUiThread { OnlineSession.setStateForTest(null) } }
        runCatching { PendingRestore.discard(ctx) }
        runCatching { Profiles.select(ctx, startProfile) }
        extraProfile?.let { runCatching { Profiles.remove(ctx, it) } }
        runCatching { AiSettings.clear(ctx) }
        BackupCrypt.iterationsForTests = null
        runCatching { server.close() }
    }

    private fun json(o: JSONObject) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(o.toString()).build()

    /** The local server's answer: subtitle lines come back marked "[bn] …"; anything else answers "ready". */
    private fun chatReply(req: JSONObject): JSONObject {
        val msgs = req.optJSONArray("messages") ?: JSONArray()
        val system = (0 until msgs.length()).map { msgs.getJSONObject(it) }.firstOrNull { it.optString("role") == "system" }?.optString("content").orEmpty()
        val user = (0 until msgs.length()).map { msgs.getJSONObject(it) }.lastOrNull { it.optString("role") == "user" }?.optString("content").orEmpty()
        val content = if (system.contains("subtitles", true) && user.startsWith("{")) {
            val lines = JSONObject(user).optJSONArray("lines") ?: JSONArray()
            JSONObject().put("t", JSONArray((0 until lines.length()).map { "[bn] " + lines.optString(it) })).toString()
        } else "ready"
        return JSONObject().put("model", "yu-local").put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))))
    }

    private fun localAi() {
        AiSettings.setProvider(ctx, AiPreset.CUSTOM)
        AiSettings.setExtra(ctx, "$base/v1")
        AiSettings.setModel(ctx, "yu-local")
    }

    @Test fun a_aiSettingsWithALocalServer() {
        // TV: the address is set with the call the Save button makes. Typing opens the TV's full-screen keyboard,
        // after which Compose's test link never goes idle (tv-api34 run 46129916277); D-pad typing is MANUAL M-B20-TV-AI.
        val tv = !MangaTranslate.allowedOn(ctx)
        if (tv) { AiSettings.setProvider(ctx, AiPreset.CUSTOM); AiSettings.setExtra(ctx, "$base/v1") }
        compose.setContent { Box(Modifier.fillMaxSize()) { AiSettingsScreen(onBack = {}); AppMessageHost() } }
        wait("AI settings") { has("ai_settings") }
        if (!tv) {
            assertFalse("no key field before a provider is chosen", has("ai_key_field"))
            tap("ai_provider_custom")
            wait("address field") { has("ai_extra") }
            compose.onNodeWithTag("ai_extra", true).performTextReplacement("$base/v1")
            tap("ai_extra_save")
        }
        wait("models from the server", 30_000) { has("ai_model_yu-local") }
        tap("ai_model_yu-local")
        wait("test button") { has("ai_test") }
        FirstRun.shot("gb20_ai_settings_local")
        tap("ai_test")
        wait("connection OK", 30_000) { has("ai_ok") }
        val seen = synchronized(calls) { calls.toList() }
        log("GB20 ai calls=${seen.map { it.url.encodedPath }}")
        assertTrue(seen.any { it.url.encodedPath == "/v1/models" })
        assertTrue(seen.any { it.url.encodedPath == "/v1/chat/completions" })
        assertTrue("no key: no Authorization header", seen.none { it.headers["Authorization"] != null })
        assertTrue(AiSettings.get(ctx).ready)
        log("GB20_RESULT_A PASS")
    }

    @Test fun b_assistantSetupThenReady() {
        compose.setContent { Box(Modifier.fillMaxSize()) { AssistantScreen(initialCategory = null, onBack = {}, onOpenSettings = {}, onOpenRec = {}) } }
        wait("setup card without AI") { has("assistant_setup") && has("assistant_setup_open") }
        FirstRun.shot("gb20_assistant_setup")
        compose.runOnUiThread { localAi() }
        wait("start screen once AI is ready") { has("assistant_intro") && !has("assistant_setup") }
        assertTrue("the message field is there", has("assistant_input"))
        FirstRun.shot("gb20_assistant_ready")
        log("GB20_RESULT_B PASS")
    }

    @Test fun c_profilesSeeOnlyTheirOwnAiChatsAndTaste() {
        val a = Profiles.currentId(ctx)
        localAi()
        AiSettings.setKey(ctx, "yu-b20-local-$tag")
        val chatId = "yu-b20-$tag"
        AssistantChats.put(ctx, AssistantChats.Chat(chatId, "B20 chat $tag", MediaCategory.ANIME, listOf(AssistantChats.Turn(true, "hello", at = 1)), System.currentTimeMillis()))
        val ref = TasteSignals.Ref("o|YuB20|https://b20/$tag", MediaCategory.ANIME, "B20 Show $tag")
        TasteSignals.opened(ctx, ref)
        TasteSignals.flush()

        val b = Profiles.add(ctx, "B20 $tag", AccentTone.GOLD).id.also { extraProfile = it }
        assertTrue(Profiles.select(ctx, b))
        val cfgB = AiSettings.get(ctx)
        AssistantChats.ensure(ctx)
        log("GB20 profile B: preset=${cfgB.preset} hasKey=${cfgB.hasKey} chats=${AssistantChats.chats.value.size} taste=${TasteSignals.entries(ctx).size}")
        assertEquals(null, cfgB.preset)
        assertFalse("the key belongs to profile A", cfgB.hasKey)
        assertTrue(AssistantChats.chats.value.none { it.id == chatId })
        assertTrue(TasteSignals.entries(ctx).none { it.key == ref.key })
        TasteSignals.flush()

        assertTrue(Profiles.select(ctx, a))
        val cfgA = AiSettings.get(ctx)
        AssistantChats.ensure(ctx)
        assertEquals(AiPreset.CUSTOM, cfgA.preset)
        assertTrue(cfgA.hasKey)
        assertTrue(AssistantChats.chats.value.any { it.id == chatId })
        assertTrue(TasteSignals.entries(ctx).any { it.key == ref.key })
        AssistantChats.delete(ctx, chatId)
        TasteSignals.forget(ctx) { it.key == ref.key }
        log("GB20_RESULT_C PASS")
    }

    private fun playingWithEnglish() {
        val ep = OnlineEpisode("$base/ep1-$tag", "Ep 1", 1, 1, null, null, null)
        val detail = OnlineDetail(
            api = "YuB20", url = "$base/show-$tag", name = "B20 Show", kind = OnlineKind.SERIES, type = "TvSeries", posterUrl = null, backgroundUrl = null,
            posterHeaders = emptyMap(), plot = null, year = 2026, tags = emptyList(), durationMin = null, score = null, contentRating = null,
            playData = null, episodes = mapOf("" to listOf(ep)), seasonNames = emptyMap(), recommendations = emptyList(),
        )
        val s = OnlineSession.State("YuB20", detail, "", listOf(ep), 0, phase = OnlineSession.Phase.PLAYING,
            subtitles = listOf(OnlineSubtitle("English", "$base/sub/en.vtt", emptyMap())), videoId = "yu-b20-$tag")
        compose.runOnUiThread { OnlineSession.setStateForTest(s) }
    }

    private fun translate(engine: SubtitleTranslate.Engine?, ms: Long): SubtitleTranslate.Status {
        compose.runOnUiThread { SubtitleTranslate.start(ctx, 0L, SubtitleTranslate.Settings("bn", SubtitleTranslate.Mode.NATIVE, engine)) }
        runCatching { compose.waitUntil(ms) { SubtitleTranslate.status.value.let { it is SubtitleTranslate.Status.Ready || it is SubtitleTranslate.Status.Failed } } }
        return SubtitleTranslate.status.value
    }

    private fun translatedTrack(): OnlineSubtitle? = OnlineSession.state.value?.subtitles?.lastOrNull { it.auto }

    @Test fun d_subtitlesAreTranslated() {
        playingWithEnglish()
        if (MangaTranslate.allowedOn(ctx)) {
            val st = translate(SubtitleTranslate.Engine.DEVICE, 300_000)
            log("GB20 device translation: $st")
            assertTrue("on-device translation finished: $st", st is SubtitleTranslate.Status.Ready)
            val text = File(android.net.Uri.parse(translatedTrack()!!.url).path!!).readText()
            log("GB20 device vtt: ${text.replace('\n', '|').take(300)}")
            assertTrue("Bengali text in the new track", text.any { it in '\u0980'..'\u09FF' })
            assertTrue("timings kept", text.contains("00:00:04.000 --> 00:00:06.000"))
        } else {
            val st = translate(null, 10_000)
            log("GB20 TV without AI: $st")
            assertTrue("TV says what's needed: $st", st is SubtitleTranslate.Status.Failed && st.message.contains("Yumeiro AI"))
        }
        localAi()
        val st = translate(SubtitleTranslate.Engine.AI, 60_000)
        log("GB20 AI translation: $st")
        assertTrue("AI translation finished: $st", st is SubtitleTranslate.Status.Ready)
        val track = translatedTrack()!!
        val text = File(android.net.Uri.parse(track.url).path!!).readText()
        assertTrue(text.contains("[bn] Where is the station?"))
        assertTrue("the track was added to the playing episode", track.auto && track.language.contains("translated"))
        log("GB20_RESULT_D PASS")
    }

    private fun entries(f: File, password: CharArray?): List<String> {
        val (plain, _) = Backup.plain(f.inputStream(), password)
        return ZipInputStream(plain.buffered()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
    }

    @Test fun e_lockedBackupCarriesTheKeyOnlyWithThePassword() {
        localAi()
        val key = "yu-b20-backup-$tag-not-a-real-key"
        AiSettings.setKey(ctx, key)
        val pw = "yumeiro b20 $tag".toCharArray()
        val dir = File(ctx.cacheDir, "b20-backup").apply { deleteRecursively(); mkdirs() }
        val locked = File(dir, "locked.ymbk")
        val plainFile = File(dir, "plain.zip")
        val t0 = System.currentTimeMillis()
        runBlocking { locked.outputStream().use { Backup.export(ctx, it, password = pw.copyOf()) } }
        log("GB20 locked export ${locked.length()} B in ${System.currentTimeMillis() - t0} ms (600k PBKDF2 rounds)")
        runBlocking { plainFile.outputStream().use { Backup.export(ctx, it) } }
        assertFalse("the key is never readable in the locked file", String(locked.readBytes(), Charsets.ISO_8859_1).contains(key))
        assertFalse("a plain backup has no keys", entries(plainFile, null).any { it.contains("secrets") })
        assertTrue("a locked backup has them", entries(locked, pw.copyOf()).any { it.contains("secrets") })

        val noPw = runCatching { runBlocking { Backup.inspect({ locked.inputStream() }) } }.exceptionOrNull()
        assertTrue("needs the password: $noPw", noPw is BackupCrypt.NeedsPassword)
        val wrong = runCatching { runBlocking { Backup.inspect({ locked.inputStream() }, "wrong password 1".toCharArray()) } }.exceptionOrNull()
        assertTrue("wrong password refused: $wrong", wrong is BackupCrypt.WrongPassword)
        val info = runBlocking { Backup.inspect({ locked.inputStream() }, pw.copyOf()) }
        assertTrue(info.locked)
        assertTrue(Backup.Section.SECRETS in info.sections)

        runBlocking { Backup.stage(ctx, { locked.inputStream() }, setOf(Backup.Section.SECRETS), pw.copyOf()) }
        val staged = File(PendingRestore.dir(ctx), "secrets.sealed.json")
        assertTrue("staged secrets", staged.isFile)
        val raw = staged.readText()
        assertFalse("never staged opened", raw.contains(key))
        val sealed = JSONObject(raw).getJSONObject("secrets").optString("secret:ai_key_p${Profiles.currentId(ctx)}")
        assertTrue(SecretBox.isSealed(sealed))
        assertEquals("sealed again with this device's Keystore", key, SecretBox.open(sealed))
        PendingRestore.discard(ctx)
        dir.deleteRecursively()
        log("GB20_RESULT_E PASS")
    }
}
