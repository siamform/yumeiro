/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import dev.asfakul.yumeiro.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-013 key classification. BuildConfig is in every APK, so it may only hold class-1 public
 * identifiers and class-2 restricted public values. This fails when a new secret-like field appears
 * or a removed one comes back; extend the ADR table before changing the allowed list.
 */
class ApkSecretsTest {
    private val fields = BuildConfig::class.java.declaredFields.map { it.name }.toSet()

    @Test fun removedTrueSecretsStayOut() {
        val back = fields intersect setOf("TMDB_API_KEY", "TRAKT_CLIENT_SECRET", "KITSU_CLIENT_ID", "KITSU_CLIENT_SECRET")
        assertTrue("back in BuildConfig: $back", back.isEmpty())
    }

    @Test fun everySecretLikeFieldIsClassified() {
        val secretLike = fields.filter { n -> listOf("SECRET", "TOKEN", "KEY", "PASSWORD").any { it in n } }.toSet()
        val classified = setOf(
            "LASTFM_API_KEY", "LASTFM_SHARED_SECRET", "OPENSUBTITLES_API_KEY", "FCM_API_KEY", // class 2
            "UPDATER_TOKEN_BYTES", // empty in release/preview (build guard), debug-only otherwise
            "UPDATER_TOKEN_KEY", // XOR key for the cert digest, not a credential
        )
        assertEquals("unclassified: ${secretLike - classified}", emptySet<String>(), secretLike - classified)
    }
}
