/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import java.security.MessageDigest

/**
 * Last.fm's `api_sig`, isolated in its own file (see docs/security/AUDIT.md, "CodeQL suppressions").
 * The format is fixed by Last.fm (https://www.last.fm/api/authspec): MD5 over every parameter
 * except `format` and `callback`, sorted by name, concatenated as `<name><value>`, followed by the
 * shared secret, UTF-8, lowercase hex. It's an interop requirement, not a security choice of ours;
 * nothing of ours is protected by it and the session key stays encrypted at rest.
 */
internal object LastFmSignature {
    private val unsigned = setOf("format", "callback", "api_sig")

    fun sign(params: Map<String, String>, secret: String): String {
        val base = params.filterKeys { it !in unsigned }.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
