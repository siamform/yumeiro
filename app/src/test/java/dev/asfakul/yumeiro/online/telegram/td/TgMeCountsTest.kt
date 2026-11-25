/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TG-ME-1 / TG-COUNT-1 (owner list 8A, 8B): the signed-in user from getMe and a chat's media counts. */
class TgMeCountsTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun meFromTdlibUser() {
        // Shape of TDLib's `user` (td_api.tl: first_name, last_name, usernames.active_usernames, profile_photo.small.id).
        val me = TgLibrary.parseMe(json("""{"@type":"user","id":42,"first_name":"Asfakul","last_name":"Siam",
            "usernames":{"@type":"usernames","active_usernames":["siamform"],"editable_username":"siamform"},
            "profile_photo":{"@type":"profilePhoto","id":"9","small":{"@type":"file","id":77},"big":{"@type":"file","id":78}}}"""))
        assertEquals(TgMe("Asfakul Siam", "siamform", 77), me)
    }

    @Test fun meWithoutNameOrPhotoStillHasALabel() {
        assertEquals(TgMe("solo", "solo", 0), TgLibrary.parseMe(json("""{"@type":"user","first_name":"","last_name":"","usernames":{"active_usernames":["solo"]}}""")))
        assertEquals("Telegram user", TgLibrary.parseMe(json("""{"@type":"user","first_name":""}"""))!!.name)
        assertNull(TgLibrary.parseMe(json("""{"@type":"error","code":401,"message":"Unauthorized"}""")))
    }

    @Test fun countsLabelShowsOnlyWhatTheChatHas() {
        assertEquals("1,204 videos · 88 photos · 12 audio", TgCounts(1204, 88, 12).label())
        assertEquals("1 video · 3 audio", TgCounts(1, 0, 3).label())
        assertEquals("No videos, photos or audio", TgCounts(0, 0, 0).label())
        // Unknown (-1) kinds are never shown as numbers.
        assertEquals("5 photos", TgCounts(-1, 5, -1).label())
        assertEquals("", TgCounts(-1, -1, -1).label())
    }
}
