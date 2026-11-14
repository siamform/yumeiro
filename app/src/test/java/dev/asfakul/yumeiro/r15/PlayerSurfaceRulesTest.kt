/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.player.PlayerSurfaceRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 phase 4: which player changes count as "a new video" (→ full screen out of floating / PiP). */
class PlayerSurfaceRulesTest {
    @Test fun sourceFailoverOfTheSameTitleIsNotANewVideo() {
        assertFalse(PlayerSurfaceRules.isNewVideo("online:1a2b:aa11", "online:1a2b:bb22"))
        assertEquals("online:1a2b", PlayerSurfaceRules.contentKey("online:1a2b:aa11"))
    }

    @Test fun anotherTitleOrSourceIsANewVideo() {
        assertTrue(PlayerSurfaceRules.isNewVideo("online:1a2b:aa11", "online:9f9f:aa11"))
        assertTrue(PlayerSurfaceRules.isNewVideo("online:1a2b:aa11", "yt:dQw4w9WgXcQ"))
        assertTrue(PlayerSurfaceRules.isNewVideo("yt:aaa", "yt:bbb"))
        assertTrue(PlayerSurfaceRules.isNewVideo(null, "stream:x"))
    }

    @Test fun sameIdIsNotNewAndIdsWithoutALinkPartStayWhole() {
        assertFalse(PlayerSurfaceRules.isNewVideo("yt:aaa", "yt:aaa"))
        assertEquals("online:1a2b", PlayerSurfaceRules.contentKey("online:1a2b"))
        assertEquals("stream:http://a/b:8080", PlayerSurfaceRules.contentKey("stream:http://a/b:8080"))
    }
}
