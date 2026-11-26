/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 (owner): not every Telegram video played. Kinds that were dropped before the fix:
 *  - account chats: GIF-style clips (`messageAnimation`, MP4) and round video messages (`messageVideoNote`);
 *    files named .m2ts / .mts / .ogv / .vob;
 *  - public preview: round video messages (their own player markup) and videos sent as files with no preview
 *    player (listed now without a file address, so they play with the user's account instead of vanishing).
 */
class GroupNTelegramKindsTest {
    private fun file(id: Int, size: Long) = tdObject("file") {
        put("id", id); put("size", size); put("expected_size", size)
        put("local", tdObject("localFile") { put("download_offset", 0); put("downloaded_prefix_size", 0); put("is_downloading_completed", false) })
    }

    private fun message(content: kotlinx.serialization.json.JsonElement) = buildJsonObject {
        put("@type", "message"); put("id", 2097152); put("chat_id", "-1009876543210"); put("date", 1_760_000_000); put("content", content)
    }

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("telegram/$name")!!.readText()

    @Test fun accountAnimationPlays() {
        val v = TgLibrary.parseVideo(message(tdObject("messageAnimation") {
            put("animation", tdObject("animation") {
                put("duration", 7); put("width", 480); put("height", 270); put("file_name", "reaction.mp4"); put("mime_type", "video/mp4")
                put("animation", file(60, 900_000))
            })
            put("caption", tdObject("formattedText") { put("text", "lol") })
        }))
        assertNotNull("a GIF-style clip is a video", v)
        assertEquals(60, v!!.fileId); assertEquals("video/mp4", v.mimeType); assertEquals(7, v.durationSec); assertEquals(false, v.audio)
        // Telegram also sends real GIFs as animations; those carry image/gif and play as MP4 conversions only.
        val gif = TgLibrary.parseVideo(message(tdObject("messageAnimation") {
            put("animation", tdObject("animation") { put("mime_type", "image/gif"); put("animation", file(61, 10_000)) })
        }))!!
        assertEquals("video/mp4", gif.mimeType)
    }

    @Test fun accountRoundVideoPlays() {
        val v = TgLibrary.parseVideo(message(tdObject("messageVideoNote") {
            put("video_note", tdObject("videoNote") { put("duration", 12); put("length", 384); put("video", file(62, 1_200_000)) })
        }))
        assertNotNull("a round video message is a video", v)
        assertEquals(62, v!!.fileId); assertEquals(12, v.durationSec); assertEquals(384, v.width); assertEquals("video/mp4", v.mimeType)
    }

    @Test fun accountMoreFileKinds() {
        for ((name, mime) in listOf("Show - 05.m2ts" to "video/mp2t", "clip.MTS" to "video/mp2t", "a.ogv" to "video/ogg", "VTS_01_1.VOB" to "video/mpeg")) {
            val v = TgLibrary.parseVideo(message(tdObject("messageDocument") {
                put("document", tdObject("document") { put("file_name", name); put("document", file(63, 6_100_000_000)) })
            }))
            assertNotNull("$name is a video file", v)
            assertEquals(name, mime, v!!.mimeType)
            assertEquals("large files keep their size", 6_100_000_000L, v.size)
        }
        assertNull(TgLibrary.parseVideo(message(tdObject("messageDocument") {
            put("document", tdObject("document") { put("file_name", "subs.srt"); put("document", file(64, 100)) })
        })))
    }

    @Test fun previewRoundVideoAndFileVideos() {
        val page = TelegramPublic.parsePage(fixture("b10-kinds.html"), "kinds")
        val ids = page.posts.map { it.id }
        assertEquals("round video, two video files; the PDF is not a video", listOf(20, 21, 23), ids)
        val round = page.posts.first { it.id == 20 }
        assertEquals("https://cdn.example.test/file/20.mp4", round.videoUrl)
        assertEquals("0:12", round.duration)
        val mkv = page.posts.first { it.id == 21 }
        assertNull("no preview player: plays with the account", mkv.videoUrl)
        assertEquals("Movie.2024.2160p.MKV", mkv.text)
        assertEquals("3.8 GB", mkv.size)
        assertNull(mkv.duration)
        assertEquals("Episode 5 remux", page.posts.first { it.id == 23 }.text)
        assertEquals(2, page.withheld)
        assertTrue(TelegramPublic.pick(page, "kinds", 20) is TelegramPublic.Result.Ok)
        assertTrue(TelegramPublic.pick(page, "kinds", 21) is TelegramPublic.Result.Failed)
    }

    @Test fun existingFixtureUnchanged() {
        // A file with a preview player stays one entry (no duplicate from its document block).
        val page = TelegramPublic.parsePage(fixture("channel.html"), "yutest")
        assertEquals(listOf(6, 7, 8, 9, 10), page.posts.map { it.id })
    }
}
