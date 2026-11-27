/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A chat or channel in the signed-in account's main list. */
data class TgChat(val id: Long, val title: String, val kind: Kind, val photoFileId: Int) {
    enum class Kind { CHANNEL, GROUP, PRIVATE }
}

/** A video post: a `messageVideo`, or a `messageDocument` that is a video file (many channels post MKV as files). */
data class TgVideo(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val name: String,
    val mimeType: String,
    val size: Long,
    val durationSec: Int,
    val width: Int,
    val height: Int,
    val date: Long,
    val caption: String,
    val thumbFileId: Int,
    /** Music, voice note or audio file (owner add 2026-11-21): plays in the same player, no picture. */
    val audio: Boolean = false,
)

/** TG-ME-1 (owner list 8A): the signed-in user, as the Telegram page and the chat list show it. */
data class TgMe(val name: String, val username: String, val photoFileId: Int)

/** TG-COUNT-1 (owner list 8B): how many videos, photos and audio a chat holds (Telegram's own counts; -1 = unknown). */
data class TgCounts(val videos: Int, val photos: Int, val audio: Int) {
    /** "1,204 videos · 88 photos · 12 audio"; unknown kinds are left out, and zero kinds too unless all are zero. */
    fun label(): String {
        val parts = listOf(videos to "video", photos to "photo", audio to "audio").filter { it.first > 0 }
            .map { (n, w) -> "%,d".format(n) + " " + if (n == 1 || w == "audio") w else w + "s" }
        return when {
            parts.isNotEmpty() -> parts.joinToString(" · ")
            videos == 0 && photos == 0 && audio == 0 -> tr(R.string.no_videos_photos_or_audio, "No videos, photos or audio")
            else -> ""
        }
    }
}

/** One page of a chat's videos and where the next page starts (0 = no more). */
data class TgVideoPage(val videos: List<TgVideo>, val next: Long)

/**
 * F-02 / ADR-052: the account's chats and a chat's videos, read from TDLib. Parsing is pure (TgLibraryTest feeds it
 * TDLib JSON); network work happens in TDLib's own threads, and callers run these from a coroutine, not the main
 * thread's frame.
 */
class TgLibrary(private val client: TdClient) {
    /** The main chat list (TDLib's order: pinned first, then by last message). */
    suspend fun chats(limit: Int = 200): List<TgChat> {
        // loadChats answers 404 when everything is loaded; that's fine.
        runCatching { client.call("loadChats", timeoutMs = 20_000) { put("chat_list", tdObject("chatListMain")); put("limit", limit) } }
        val ids = client.call("getChats") { put("chat_list", tdObject("chatListMain")); put("limit", limit) }
            .let { o -> (o["chat_ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNull } }.orEmpty()
        val gate = Semaphore(8)
        return coroutineScope {
            ids.map { id -> async { gate.withPermit { runCatching { parseChat(client.call("getChat") { put("chat_id", id) }) }.getOrNull() } } }
                .mapNotNull { it.await() }
        }
    }

    /**
     * Videos of [chatId], newest first. No [query]: the chat's history; with a query: Telegram's search in that
     * chat. Videos, video files, music, voice notes and audio files are kept; everything else is skipped. [from] is the previous page's `next`.
     */
    suspend fun videos(chatId: Long, query: String = "", from: Long = 0, pageSize: Int = 50): TgVideoPage {
        val q = query.trim()
        val answer = if (q.isEmpty()) {
            client.call("getChatHistory") { put("chat_id", chatId); put("from_message_id", from); put("offset", 0); put("limit", pageSize); put("only_local", false) }
        } else {
            client.call("searchChatMessages") { put("chat_id", chatId); put("query", q); put("from_message_id", from); put("offset", 0); put("limit", pageSize) }
        }
        val messages = (answer["messages"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val vids = messages.mapNotNull { m -> parseVideo(m)?.also { v -> fileOf(m)?.let(client::remember) } }
        val next = when {
            q.isNotEmpty() -> answer.long("next_from_message_id")
            messages.isEmpty() -> 0L
            else -> messages.last().long("id")
        }
        return TgVideoPage(vids, next)
    }

    /**
     * GROUP F (owner items 4 and 6): one post of a public channel, read with the signed-in account. Telegram keeps big
     * videos out of its web preview; the account can still open them, so they play here instead of "only in the app".
     * Null when the post has no video or audio.
     */
    suspend fun postVideo(channel: String, id: Int): TgVideo? {
        val info = client.call("getMessageLinkInfo") { put("url", postLink(channel, id)) }
        val m = info.obj("message") ?: return null
        return parseVideo(m)?.also { fileOf(m)?.let(client::remember) }
    }

    /** TG-ME-1: the signed-in user (getMe). Null when TDLib can't answer. */
    suspend fun me(): TgMe? = runCatching { parseMe(client.call("getMe")) }.getOrNull()

    /**
     * TG-COUNT-1: the chat's video, photo and audio counts. getChatMessageCount with return_local=false asks the server,
     * so the numbers cover the whole chat, not just what this phone has loaded. Music, voice notes and audio files count
     * as audio, like the list below.
     */
    suspend fun counts(chatId: Long): TgCounts = coroutineScope {
        fun count(vararg filters: String) = async {
            filters.sumOf { f ->
                runCatching {
                    client.call("getChatMessageCount", timeoutMs = 20_000) {
                        put("chat_id", chatId); put("filter", tdObject(f)); put("return_local", false)
                    }.int("count")
                }.getOrElse { return@async -1 }
            }
        }
        val v = count("searchMessagesFilterVideo")
        val ph = count("searchMessagesFilterPhoto")
        val a = count("searchMessagesFilterAudio", "searchMessagesFilterVoiceNote")
        TgCounts(v.await(), ph.await(), a.await())
    }

    /** Local path of a small file (thumbnail, chat photo), downloading it first at low priority. Null if it can't. */
    suspend fun smallFile(fileId: Int): String? {
        if (fileId == 0) return null
        val f = runCatching {
            client.call("downloadFile", timeoutMs = 30_000) { put("file_id", fileId); put("priority", 1); put("offset", 0); put("limit", 0); put("synchronous", true) }
        }.getOrNull() ?: return null
        return f.obj("local")?.takeIf { it.bool("is_downloading_completed") }?.str("path")?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** The public post address TDLib resolves (pure). */
        fun postLink(channel: String, id: Int): String = "https://t.me/$channel/$id"

        /** The video in a getMessageLinkInfo answer (pure; TgLibraryTest feeds it TDLib JSON). */
        fun videoOfLinkInfo(info: JsonObject): TgVideo? = info.obj("message")?.let { m -> parseVideo(m) }

        fun parseMe(o: JsonObject): TgMe? {
            if (o.type() != "user") return null
            val name = listOf(o.str("first_name"), o.str("last_name")).filter { it.isNotBlank() }.joinToString(" ")
            val username = (o.obj("usernames")?.get("active_usernames") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
            return TgMe(name.ifBlank { username.ifBlank { tr(R.string.telegram_user, "Telegram user") } }, username, o.obj("profile_photo")?.obj("small")?.int("id") ?: 0)
        }

        fun parseChat(o: JsonObject): TgChat? {
            if (o.type() != "chat") return null
            val type = o.obj("type")
            val kind = when (type?.type()) {
                "chatTypeSupergroup" -> if (type?.bool("is_channel") == true) TgChat.Kind.CHANNEL else TgChat.Kind.GROUP
                "chatTypeBasicGroup" -> TgChat.Kind.GROUP
                "chatTypePrivate", "chatTypeSecret" -> TgChat.Kind.PRIVATE
                else -> return null
            }
            return TgChat(o.long("id"), o.str("title").ifBlank { "Untitled" }, kind, o.obj("photo")?.obj("small")?.int("id") ?: 0)
        }

        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "alac", "ape")

        private val VIDEO_EXT = dev.asfakul.yumeiro.online.telegram.TelegramPublic.VIDEO_FILE_EXT

        private fun fileOf(m: JsonObject): JsonObject? {
            val c = m.obj("content") ?: return null
            return when (c.type()) {
                "messageVideo" -> c.obj("video")?.obj("video")
                "messageAnimation" -> c.obj("animation")?.obj("animation")
                "messageVideoNote" -> c.obj("video_note")?.obj("video")
                "messageDocument" -> c.obj("document")?.obj("document")
                "messageAudio" -> c.obj("audio")?.obj("audio")
                "messageVoiceNote" -> c.obj("voice_note")?.obj("voice")
                else -> null
            }
        }

        fun parseVideo(m: JsonObject): TgVideo? {
            val c = m.obj("content") ?: return null
            val caption = c.obj("caption")?.str("text").orEmpty()
            return when (c.type()) {
                "messageVideo" -> {
                    val v = c.obj("video") ?: return null
                    val f = v.obj("video") ?: return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = v.str("file_name").ifBlank { caption.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Video" },
                        mimeType = v.str("mime_type").ifBlank { "video/mp4" },
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = v.int("duration"), width = v.int("width"), height = v.int("height"),
                        date = m.long("date"), caption = caption, thumbFileId = v.obj("thumbnail")?.obj("file")?.int("id") ?: 0,
                    )
                }
                // B10: GIF-style clips (sent as MP4) and round video messages are videos too.
                "messageAnimation" -> {
                    val a = c.obj("animation") ?: return null
                    val f = a.obj("animation") ?: return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = a.str("file_name").ifBlank { caption.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Clip" },
                        mimeType = a.str("mime_type").takeIf { it.startsWith("video/") } ?: "video/mp4",
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = a.int("duration"), width = a.int("width"), height = a.int("height"),
                        date = m.long("date"), caption = caption, thumbFileId = a.obj("thumbnail")?.obj("file")?.int("id") ?: 0,
                    )
                }
                "messageVideoNote" -> {
                    val vn = c.obj("video_note") ?: return null
                    val f = vn.obj("video") ?: return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = "Video message", mimeType = "video/mp4",
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = vn.int("duration"), width = vn.int("length"), height = vn.int("length"),
                        date = m.long("date"), caption = caption, thumbFileId = vn.obj("thumbnail")?.obj("file")?.int("id") ?: 0,
                    )
                }
                "messageDocument" -> {
                    val d = c.obj("document") ?: return null
                    val f = d.obj("document") ?: return null
                    val name = d.str("file_name")
                    val mime = d.str("mime_type")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isAudio = mime.startsWith("audio/") || (!mime.startsWith("video/") && ext in AUDIO_EXT)
                    if (!isAudio && !mime.startsWith("video/") && ext !in VIDEO_EXT) return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = name.ifBlank { "Video" }, mimeType = mime.ifBlank { mimeFor(ext) },
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = 0, width = 0, height = 0, date = m.long("date"), caption = caption,
                        thumbFileId = d.obj("thumbnail")?.obj("file")?.int("id") ?: 0, audio = isAudio,
                    )
                }
                "messageAudio" -> {
                    val a = c.obj("audio") ?: return null
                    val f = a.obj("audio") ?: return null
                    val title = listOf(a.str("performer"), a.str("title")).filter { it.isNotBlank() }.joinToString(" – ")
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = title.ifBlank { a.str("file_name") }.ifBlank { "Audio" }, mimeType = a.str("mime_type").ifBlank { "audio/mpeg" },
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = a.int("duration"), width = 0, height = 0, date = m.long("date"), caption = caption,
                        thumbFileId = a.obj("album_cover_thumbnail")?.obj("file")?.int("id") ?: 0, audio = true,
                    )
                }
                "messageVoiceNote" -> {
                    val vn = c.obj("voice_note") ?: return null
                    val f = vn.obj("voice") ?: return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = "Voice message", mimeType = vn.str("mime_type").ifBlank { "audio/ogg" },
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = vn.int("duration"), width = 0, height = 0, date = m.long("date"), caption = caption,
                        thumbFileId = 0, audio = true,
                    )
                }
                else -> null
            }?.takeIf { it.fileId != 0 && it.size > 0 }
        }

        fun mimeFor(ext: String): String = when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "ogv" -> "video/ogg"
            "flv" -> "video/x-flv"
            "vob", "mpg", "mpeg" -> "video/mpeg"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "alac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "oga", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "video/mp4"
        }
    }
}
