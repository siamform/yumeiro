/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import dev.asfakul.yumeiro.intel.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * R28 item 32: Yumeiro AI as a conversation, not only a recommendation form.
 *
 * One entry point, [respond], decides per message:
 *  - a slash command ([parse]): `/recom …` → the research pipeline ([Assistant.ask]); `/translate …`; `/glossary …`,
 *    `/remember …`, `/forget …`, `/instructions`, `/help` are answered on the phone (no AI call, work offline);
 *  - a plain message that asks for titles ([wantsTitles]) → [Assistant.ask], same as before;
 *  - anything else (follow-ups, "why that one?", "that's a mistranslation, nakama means friend") → [chat], a normal
 *    conversation with the chat rules, the person's preferences and the earlier turns. When the person corrects a
 *    translation the model returns the term and the screen offers "Add to glossary" (never saved silently).
 *
 * Every path reuses the profile's provider ([AiSettings.chat]); failures stay [AiError]s with plain messages.
 * Pure parts are unit-tested in AssistantTalkTest.
 */
object AssistantTalk {
    private const val CHAT_RULES = "intel/assistant_chat_rules.md"

    data class Command(val name: String, val args: String, val description: String)

    /** The commands the composer suggests after "/". */
    val COMMANDS = listOf(
        Command("recom", "what you want", "Recommendations with Yumeiro's research, e.g. /recom yandere female lead manhwa"),
        Command("translate", "text", "Translate a line, using your glossary and style"),
        Command("glossary", "add word = translation", "Add, list or remove glossary terms (/glossary remove word)"),
        Command("remember", "a note", "Remember something about you for later chats"),
        Command("forget", "word", "Forget remembered notes (all of them when empty)"),
        Command("instructions", "", "Show your AI instructions"),
        Command("help", "", "What Yumeiro AI can do"),
    )
    private val ALIASES = mapOf("rec" to "recom", "recommend" to "recom", "suggest" to "recom", "tr" to "translate", "g" to "glossary", "mem" to "remember")

    data class Parsed(val command: String, val args: String)

    /** Pure: "/recom suggest …" → Parsed("recom", "suggest …"); null when [text] isn't a known command. */
    fun parse(text: String): Parsed? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val name = t.drop(1).takeWhile { !it.isWhitespace() }.lowercase()
        val cmd = ALIASES[name] ?: name
        if (COMMANDS.none { it.name == cmd }) return null
        return Parsed(cmd, t.drop(1 + name.length).trim())
    }

    /** Pure: the commands matching what's typed after "/" (empty when not typing a command). */
    fun suggestions(text: String): List<Command> {
        if (!text.startsWith("/") || text.contains(' ')) return emptyList()
        val p = text.drop(1).lowercase()
        return COMMANDS.filter { it.name.startsWith(p) || ALIASES.any { (a, n) -> n == it.name && a.startsWith(p) && p.isNotEmpty() } }
    }

    private val TITLES = Regex(
        "(?i)\\b(recommend|recommendation|suggest|suggestion|similar to|something like|anything like|shows? like|series like|anime like|manga like|" +
            "what (?:should|can|could) i (?:watch|read|listen|play)|give me (?:some|a few|more|\\d+)|hidden gems?|underrated|surprise me|" +
            "looking for (?:a |an |some )?(?:anime|manga|manhwa|manhua|novel|movie|film|show|series|song|music)|" +
            "(?:more|other|new) (?:ones|picks|titles)|find me)\\b",
    )
    private val CORRECTION = Regex("(?i)(mistranslat|wrong translation|translated wrong|should (?:be|say) |means |isn'?t translated|not translated as|translate .{1,40} as )")

    /** Pure: does this message ask for titles (→ the research pipeline)? */
    fun wantsTitles(q: String): Boolean = TITLES.containsMatchIn(q)

    /** Pure: does this message correct a translation (→ ask the model for a glossary term)? */
    fun isCorrection(q: String): Boolean = CORRECTION.containsMatchIn(q)

    /** Pure: how many characters of conversation fit (about 3 characters per token, a quarter kept for the answer). */
    fun budget(contextTokens: Int): Int = if (contextTokens <= 0) 24_000 else (contextTokens * 3 * 3 / 4).coerceIn(6_000, 120_000)

    /** Pure: the newest earlier turns that fit in [budget] next to [fixed] characters (oldest dropped first). */
    fun fit(history: List<AiMessage>, fixed: Int, budget: Int): List<AiMessage> {
        var left = budget - fixed
        val keep = ArrayList<AiMessage>()
        for (m in history.asReversed()) {
            left -= m.text.length + 16
            if (left < 0) break
            keep += m
        }
        return keep.asReversed()
    }

    /** Pure: the chat reply JSON → text, follow-ups and an optional glossary term; plain prose works too. */
    fun parseChat(text: String): Triple<String, List<String>, AiPersonal.Term?> {
        val o = runCatching { JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) }.getOrNull()
            ?: Regex("\\{[\\s\\S]*\\}").find(text)?.value?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return Triple(text.trim(), emptyList(), null)
        val reply = o.optString("reply").trim().ifBlank { text.trim() }
        val follow = o.optJSONArray("follow_ups")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.length in 2..120 } }.orEmpty().take(3)
        val term = o.optJSONObject("glossary")?.let { AiPersonal.cleanTerm(it.optString("source"), it.optString("target"), it.optString("note")) }
        return Triple(reply, follow, term)
    }

    /**
     * Pure: starting suggestions built from this profile's own titles (R28 item 16): the newest loved, liked or
     * favourite titles, the latest finished one, the strongest tag; [fallback] fills up to [max]. No title from another
     * category appears when [cat] is set.
     */
    fun starters(cat: MediaCategory?, entries: List<dev.asfakul.yumeiro.intel.TasteSignals.Entry>, fallback: List<String>, max: Int = 5): List<String> {
        val inCat = entries.filter { (cat == null || it.category == cat) && it.dismissedAt == 0L && it.title.isNotBlank() && it.title.length <= 60 }
        val liked = inCat.filter { it.favorite || it.feedback == dev.asfakul.yumeiro.intel.TasteSignals.Feedback.LOVED || it.feedback == dev.asfakul.yumeiro.intel.TasteSignals.Feedback.LIKED }
            .sortedByDescending { maxOf(it.feedbackAt, it.lastAt) }
        val finished = inCat.filter { it.completions > 0 && it.feedback == null }.maxByOrNull { it.completedAt }
        val noun = when (cat) { null -> "titles"; MediaCategory.MUSIC -> "artists"; MediaCategory.YOUTUBE -> "channels"; else -> cat.label.lowercase() }
        val out = LinkedHashSet<String>()
        liked.getOrNull(0)?.let { out += "Recommend $noun similar to ${it.title}" }
        finished?.let { out += "What should I try after finishing ${it.title}?" }
        liked.getOrNull(1)?.let { out += "Hidden gems for fans of ${it.title}" }
        val tag = liked.take(20).flatMap { e -> e.tags.filter { !it.spoiler } }.groupBy { it.name }
            .maxByOrNull { (_, l) -> l.sumOf { it.weight.toDouble() } }?.key
        tag?.let { out += "Underrated $noun with ${it.lowercase()}" }
        fallback.forEach { if (out.size < max) out += it }
        return out.take(max)
    }

    private fun chatRules(c: Context): String = runCatching { c.assets.open(CHAT_RULES).bufferedReader().use { it.readText() } }.getOrDefault("")

    /**
     * Answers [text] (see the class note). [history] = earlier turns as text. Commands that only touch this phone
     * work without a key; everything else throws [AiError] like [Assistant.ask].
     */
    suspend fun respond(c: Context, text: String, category: MediaCategory?, history: List<AiMessage>, recent: List<String>, onStep: (String) -> Unit = {}): Assistant.Answer {
        val cmd = parse(text)
        if (cmd != null) local(c, cmd)?.let { return it }
        val q = cmd?.args ?: text
        if (cmd?.command == "recom") {
            if (q.isBlank()) return localAnswer("Tell me what you're in the mood for after /recom, for example: /recom yandere female lead manhwa")
            return Assistant.ask(c, q, category, history, recent, onStep)
        }
        if (cmd?.command == "translate") {
            if (q.isBlank()) return localAnswer("Add the text after /translate, for example: /translate 仲間を守る")
            return chat(c, "Translate this. If no target language is named, use the language I write to you in (English when unsure). Reply with the translation, then one short line on any word choice worth knowing.\n\n$q", category, history, onStep, translate = true)
        }
        val cfg = AiSettings.get(c)
        if (!cfg.ready || !cfg.forAssistant) throw AiError.NotSetUp()
        return if (wantsTitles(q)) Assistant.ask(c, q, category, history, recent, onStep) else chat(c, q, category, history, onStep)
    }

    private fun localAnswer(text: String) = Assistant.Answer(text, kind = "local")

    /** Commands answered on the phone; null = needs the model. */
    private fun local(c: Context, cmd: Parsed): Assistant.Answer? {
        val p = AiPersonal.get(c)
        return when (cmd.command) {
            "help" -> localAnswer(buildString {
                append("Ask in your own words: what to watch, read or listen to next, why a title fits, or to explain or translate something.\n\nCommands:\n")
                COMMANDS.forEach { append("/").append(it.name).append(if (it.args.isNotBlank()) " ${it.args}" else "").append(" — ").append(it.description).append('\n') }
                append("\nYour instructions, style and glossary are in Settings → Yumeiro AI → Personalization.")
            })
            "instructions" -> localAnswer(
                if (p.instructions.isBlank()) "You haven't written any instructions yet. Add them in Settings → Yumeiro AI → Personalization (for example: \"Answer in Banglish\")."
                else "Your instructions:\n\n${p.instructions}\n\nChange them in Settings → Yumeiro AI → Personalization.",
            )
            "remember" -> localAnswer(
                when {
                    cmd.args.isBlank() -> if (p.memory.isEmpty()) "Nothing remembered yet. Try: /remember I watch with Bangla subtitles" else "I remember:\n" + p.memory.joinToString("\n") { "• $it" }
                    dev.asfakul.yumeiro.security.Privacy.incognito.value -> "Incognito is on, so I won't remember that."
                    AiPersonal.remember(c, cmd.args) -> "Got it. I'll keep that in mind in later chats. (/forget removes it.)"
                    else -> "That note is too short to remember."
                },
            )
            "forget" -> AiPersonal.forget(c, cmd.args).let { n -> localAnswer(if (n == 0) "There was nothing like that to forget." else "Forgot $n note${if (n == 1) "" else "s"}.") }
            "glossary" -> glossary(c, cmd.args)
            else -> null
        }
    }

    private val TERM = Regex("^(.+?)\\s*(?:=|→|->|:)\\s*(.+)$")

    private fun glossary(c: Context, args: String): Assistant.Answer {
        val a = args.trim()
        val lower = a.lowercase()
        return when {
            a.isBlank() || lower == "list" -> AiPersonal.get(c).glossary.let { g ->
                localAnswer(if (g.isEmpty()) "Your glossary is empty. Add a term: /glossary add nakama = friend" else "Your glossary (${g.size}):\n" + g.joinToString("\n") { "• ${it.source} → ${it.target}" })
            }
            lower.startsWith("remove ") || lower.startsWith("delete ") -> a.substringAfter(' ').trim().let { w ->
                localAnswer(if (AiPersonal.removeTerm(c, w)) "Removed \"$w\" from your glossary." else "\"$w\" isn't in your glossary.")
            }
            else -> {
                val body = if (lower.startsWith("add ")) a.drop(4).trim() else a
                val m = TERM.find(body) ?: return localAnswer("Write it as: /glossary add word = translation")
                val (s, t) = m.destructured
                localAnswer(if (AiPersonal.addTerm(c, s, t)) "Added: ${s.trim()} → ${t.trim()}. Subtitle translation and chat will use it." else "That term couldn't be added.")
            }
        }
    }

    /** A normal conversation turn with the chat rules, the person's preferences and the earlier turns. */
    private suspend fun chat(c: Context, q: String, category: MediaCategory?, history: List<AiMessage>, onStep: (String) -> Unit, translate: Boolean = false): Assistant.Answer {
        val cfg = AiSettings.get(c)
        if (!cfg.ready || !cfg.forAssistant) throw AiError.NotSetUp()
        val profile = if (translate) "" else withContext(Dispatchers.IO) { runCatching { AiContext.slice(c, category).json.toString() }.getOrDefault("") }.take(6_000)
        val personal = AiPersonal.get(c)
        val system = chatRules(c) +
            (if (profile.isNotBlank()) "\n\nPROFILE (this person's library and taste; use it, never recite it):\n$profile" else "") +
            "\n\nACTIVE CATEGORY: " + (category?.label ?: "any") +
            (if (isCorrection(q)) "\n\nThe person is correcting a translation: put the corrected term in \"glossary\"." else "") +
            AiPersonal.block(personal, AiPersonal.Purpose.CHAT, if (translate) q else null)
        onStep("Asking ${cfg.preset?.label}…")
        val earlier = fit(history.takeLast(12), system.length + q.length, budget(AiSettings.contextTokens(c)))
        val request = AiRequest("", system, earlier + AiMessage(true, q.take(4_000)), json = true, maxTokens = 2048, temperature = if (translate) 0.2 else 0.7)
        val reply = try { AiSettings.chat(c, request) } catch (e: AiError.TooLong) {
            if (earlier.isEmpty()) throw e
            onStep("Shortening the chat and asking again…")
            AiSettings.chat(c, request.copy(messages = listOf(AiMessage(true, q.take(4_000)))))
        }
        val (text, follow, term) = parseChat(reply.text)
        return Assistant.Answer(text, sources = reply.sources, model = reply.model, followUps = follow,
            offer = term?.takeIf { t -> personal.glossary.none { it.source.equals(t.source, true) && it.target == t.target } }, kind = "chat")
    }
}
