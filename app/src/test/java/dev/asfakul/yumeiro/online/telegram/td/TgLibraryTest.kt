/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F-02 / ADR-052: chats and video messages from TDLib's JSON, and how much of a file is ready to read. */
class TgLibraryTest {
    private fun file(id: Int, size: Long, offset: Long = 0, prefix: Long = 0, done: Boolean = false) = tdObject("file") {
        put("id", id); put("size", size); put("expected_size", size)
        put("local", tdObject("localFile") {
            put("download_offset", offset); put("downloaded_prefix_size", prefix); put("is_downloading_completed", done)
        })
    }

    private fun message(content: kotlinx.serialization.json.JsonElement) = buildJsonObject {
        put("@type", "message"); put("id", 1048576); put("chat_id", "-1001234567890"); put("date", 1_760_000_000); put("content", content)
    }

    @Test fun parsesChats() {
        fun chat(type: kotlinx.serialization.json.JsonElement, title: String = "Anime") = buildJsonObject {
            put("@type", "chat"); put("id", "-1001234567890"); put("title", title); put("type", type)
            put("photo", tdObject("chatPhotoInfo") { put("small", file(9, 10)) })
        }
        val channel = TgLibrary.parseChat(chat(tdObject("chatTypeSupergroup") { put("supergroup_id", 1); put("is_channel", true) }))!!
        assertEquals(TgChat(-1001234567890, "Anime", TgChat.Kind.CHANNEL, 9), channel)
        assertEquals(TgChat.Kind.GROUP, TgLibrary.parseChat(chat(tdObject("chatTypeSupergroup") { put("is_channel", false) }))!!.kind)
        assertEquals(TgChat.Kind.GROUP, TgLibrary.parseChat(chat(tdObject("chatTypeBasicGroup")))!!.kind)
        assertEquals(TgChat.Kind.PRIVATE, TgLibrary.parseChat(chat(tdObject("chatTypePrivate")))!!.kind)
        assertEquals("Untitled", TgLibrary.parseChat(chat(tdObject("chatTypePrivate"), title = ""))!!.title)
        assertNull(TgLibrary.parseChat(chat(tdObject("chatTypeSomethingNew"))))
        assertNull(TgLibrary.parseChat(tdObject("error") as JsonObject))
    }

    @Test fun parsesVideoMessage() {
        val m = message(tdObject("messageVideo") {
            put("video", tdObject("video") {
                put("duration", 1440); put("width", 1920); put("height", 1080); put("file_name", "ep01.mp4"); put("mime_type", "video/mp4")
                put("thumbnail", tdObject("thumbnail") { put("file", file(5, 100)) })
                put("video", file(42, 734_003_200))
            })
            put("caption", tdObject("formattedText") { put("text", "Episode 1\nmore") })
        })
        val v = TgLibrary.parseVideo(m)!!
        assertEquals(42, v.fileId); assertEquals(1048576L, v.messageId); assertEquals(-1001234567890L, v.chatId)
        assertEquals(734_003_200L, v.size); assertEquals(1440, v.durationSec); assertEquals(1080, v.height)
        assertEquals("ep01.mp4", v.name); assertEquals("Episode 1\nmore", v.caption); assertEquals(5, v.thumbFileId)
    }

    @Test fun parsesMkvDocumentWithoutVideoMime() {
        val m = message(tdObject("messageDocument") {
            put("document", tdObject("document") { put("file_name", "[Sub] Show - 03.MKV"); put("mime_type", "application/octet-stream"); put("document", file(43, 1_500_000_000)) })
        })
        val v = TgLibrary.parseVideo(m)!!
        assertEquals(43, v.fileId)
        assertEquals("application/octet-stream", v.mimeType)
        val noMime = message(tdObject("messageDocument") { put("document", tdObject("document") { put("file_name", "a.mkv"); put("document", file(44, 10)) }) })
        assertEquals("video/x-matroska", TgLibrary.parseVideo(noMime)!!.mimeType)
    }

    @Test fun parsesAudio() {
        val music = TgLibrary.parseVideo(message(tdObject("messageAudio") {
            put("audio", tdObject("audio") { put("duration", 215); put("performer", "Aimer"); put("title", "Kataomoi"); put("mime_type", "audio/flac"); put("audio", file(50, 30_000_000)) })
        }))!!
        assertEquals(true, music.audio); assertEquals("Aimer – Kataomoi", music.name); assertEquals("audio/flac", music.mimeType); assertEquals(215, music.durationSec)
        val voice = TgLibrary.parseVideo(message(tdObject("messageVoiceNote") {
            put("voice_note", tdObject("voiceNote") { put("duration", 9); put("mime_type", "audio/ogg"); put("voice", file(51, 40_000)) })
        }))!!
        assertEquals(true, voice.audio); assertEquals(51, voice.fileId)
        val doc = TgLibrary.parseVideo(message(tdObject("messageDocument") {
            put("document", tdObject("document") { put("file_name", "OST 01.opus"); put("document", file(52, 5_000_000)) })
        }))!!
        assertEquals(true, doc.audio); assertEquals("audio/ogg", doc.mimeType)
        val mkv = TgLibrary.parseVideo(message(tdObject("messageDocument") {
            put("document", tdObject("document") { put("file_name", "a.mkv"); put("mime_type", "video/x-matroska"); put("document", file(53, 10)) })
        }))!!
        assertEquals(false, mkv.audio)
    }

    @Test fun skipsEverythingElse() {
        assertNull(TgLibrary.parseVideo(message(tdObject("messageText"))))
        assertNull(TgLibrary.parseVideo(message(tdObject("messageDocument") {
            put("document", tdObject("document") { put("file_name", "notes.pdf"); put("mime_type", "application/pdf"); put("document", file(45, 10)) })
        })))
        // A video with no size (TDLib not told yet) can't be served with Range: skipped.
        assertNull(TgLibrary.parseVideo(message(tdObject("messageVideo") { put("video", tdObject("video") { put("video", file(46, 0)) }) })))
        assertNull(TgLibrary.parseVideo(buildJsonObject { put("@type", "message") }))
    }

    @Test fun readyBytesFromFileState() {
        assertEquals(0L, TdFileSource.readyFrom(file(1, 1000) as JsonObject, 0))
        assertEquals(300L, TdFileSource.readyFrom(file(1, 1000, prefix = 300) as JsonObject, 0))
        assertEquals(100L, TdFileSource.readyFrom(file(1, 1000, prefix = 300) as JsonObject, 200))
        assertEquals(0L, TdFileSource.readyFrom(file(1, 1000, prefix = 300) as JsonObject, 300))
        assertEquals(50L, TdFileSource.readyFrom(file(1, 1000, offset = 500, prefix = 100) as JsonObject, 550))
        assertEquals(0L, TdFileSource.readyFrom(file(1, 1000, offset = 500, prefix = 100) as JsonObject, 100))
        assertEquals(400L, TdFileSource.readyFrom(file(1, 1000, done = true) as JsonObject, 600))
        assertEquals(0L, TdFileSource.readyFrom(file(1, 1000, done = true) as JsonObject, 2000))
    }

    @Test fun mimeForExtensions() {
        assertEquals("video/x-matroska", TgLibrary.mimeFor("mkv"))
        assertEquals("video/mp4", TgLibrary.mimeFor("m4v"))
    }
}
