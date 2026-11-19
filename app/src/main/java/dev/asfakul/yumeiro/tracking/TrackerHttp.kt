/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import java.io.IOException

/**
 * ADR-030 rule 2 (IG-10): a tracker answer that may work on a later try (rate limit, server error, timeout,
 * network error = -1) throws, so AnimeSync gives the tracker its key back and retries it (IG-2). Before,
 * these answers returned false and the episode was dropped as if it had been sent.
 */
internal object TrackerHttp {
    fun transient(code: Int): Boolean = code == -1 || code == 408 || code == 429 || code >= 500

    /** Returns [code], or throws when it is [transient]. */
    fun check(service: String, code: Int): Int {
        if (transient(code)) throw IOException("$service HTTP $code (will retry)")
        return code
    }
}
