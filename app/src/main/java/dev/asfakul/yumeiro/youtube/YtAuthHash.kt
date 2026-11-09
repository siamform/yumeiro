/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import java.security.MessageDigest

/**
 * YouTube's `SAPISIDHASH` request signature, isolated in its own file (see docs/security/AUDIT.md,
 * "CodeQL suppressions"). The format is fixed by YouTube's web client: SHA-1 over
 * "<unix seconds> <SAPISID cookie> <origin>". It's an interop requirement, not a security choice
 * of ours. The secret is the cookie itself, which stays encrypted at rest.
 */
internal object YtAuthHash {
    fun header(sapisid: String, origin: String, nowSec: Long): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$nowSec $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${nowSec}_" + digest.joinToString("") { "%02x".format(it) }
    }
}
