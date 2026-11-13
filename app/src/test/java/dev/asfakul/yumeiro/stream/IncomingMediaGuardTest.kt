/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.anime.OAuthGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IncomingMediaGuardTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun otherAppsCannotOpenOurPrivateFiles() {
        val own = java.io.File(ctx.filesDir, "../shared_prefs/secrets.xml")
        assertTrue(IncomingMedia.pointsIntoPrivateStorage(ctx, Uri.fromFile(own)))
        assertTrue(IncomingMedia.pointsIntoPrivateStorage(ctx, Uri.parse("content://${ctx.packageName}.provider/x")))
        assertFalse(IncomingMedia.pointsIntoPrivateStorage(ctx, Uri.parse("file:///sdcard/Movies/a.mp4")))
        assertFalse(IncomingMedia.pointsIntoPrivateStorage(ctx, Uri.parse("content://media/external/video/1")))
        assertFalse(IncomingMedia.pointsIntoPrivateStorage(ctx, Uri.parse("https://example.com/a.m3u8")))
        assertFalse(IncomingMedia.handle(ctx, android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.fromFile(own))))
    }

    @Test fun oauthRedirectNeedsAFreshSignInFromThisApp() {
        assertFalse(OAuthGuard.consume(ctx, "anilist"))              // nobody tapped Sign in
        OAuthGuard.begin(ctx, "anilist", now = 1_000L)
        assertTrue(OAuthGuard.consume(ctx, "anilist", now = 2_000L))
        assertFalse(OAuthGuard.consume(ctx, "anilist", now = 2_001L)) // one use
        OAuthGuard.begin(ctx, "mal", now = 0L + 1)
        assertFalse(OAuthGuard.consume(ctx, "mal", now = OAuthGuard.WINDOW_MS + 10)) // expired
    }

    @Test fun backupRestoreOnlyWritesKnownPlaces() {
        val root = java.io.File(ctx.cacheDir, "guard").apply { mkdirs() }
        val b = dev.asfakul.yumeiro.backup.Backup
        assertTrue(b.safeTarget(root, "files/extensions/x.cs3") != null)
        assertTrue(b.safeTarget(root, "files/streams/a.json") != null)
        assertTrue(b.safeTarget(root, "files/evil.so") == null)
        assertTrue(b.safeTarget(root, "files/random/x.bin") == null)
        assertTrue(b.safeTarget(root, "prefs/sub/x.xml") == null)
        assertTrue(b.isCode("files/js/p.js") && b.isCode("files/tachiyomi/a.ext") && !b.isCode("files/streams/a.json"))
    }
}
