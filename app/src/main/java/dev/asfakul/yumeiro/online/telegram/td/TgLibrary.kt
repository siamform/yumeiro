/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

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
)

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
     * chat. Both kinds of video post are kept, everything else is skipped. [from] is the previous page's `next`.
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

    /** Local path of a small file (thumbnail, chat photo), downloading it first at low priority. Null if it can't. */
    suspend fun smallFile(fileId: Int): String? {
        if (fileId == 0) return null
        val f = runCatching {
            client.call("downloadFile", timeoutMs = 30_000) { put("file_id", fileId); put("priority", 1); put("offset", 0); put("limit", 0); put("synchronous", true) }
        }.getOrNull() ?: return null
        return f.obj("local")?.takeIf { it.bool("is_downloading_completed") }?.str("path")?.takeIf { it.isNotEmpty() }
    }

    companion object {
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

        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "ts", "3gp", "flv", "wmv", "mpg", "mpeg")

        private fun fileOf(m: JsonObject): JsonObject? {
            val c = m.obj("content") ?: return null
            return when (c.type()) {
                "messageVideo" -> c.obj("video")?.obj("video")
                "messageDocument" -> c.obj("document")?.obj("document")
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
                "messageDocument" -> {
                    val d = c.obj("document") ?: return null
                    val f = d.obj("document") ?: return null
                    val name = d.str("file_name")
                    val mime = d.str("mime_type")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!mime.startsWith("video/") && ext !in VIDEO_EXT) return null
                    TgVideo(
                        chatId = m.long("chat_id"), messageId = m.long("id"), fileId = f.int("id"),
                        name = name.ifBlank { "Video" }, mimeType = mime.ifBlank { mimeFor(ext) },
                        size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size"),
                        durationSec = 0, width = 0, height = 0, date = m.long("date"), caption = caption,
                        thumbFileId = d.obj("thumbnail")?.obj("file")?.int("id") ?: 0,
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
            "ts" -> "video/mp2t"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }
}
