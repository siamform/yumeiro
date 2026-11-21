/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * F-07 device check: a stand-in external player that follows MX Player's published intent API
 * (https://mx.j2inter.com/api): it reads `position`, `title` and `headers` (key, value, key, value…),
 * and returns `position` / `duration` / `end_by` only when the caller asked with `return_result`.
 * It "plays" for 5 s: the position it returns is the start position + 5000 ms. The duration it returns
 * is 60 000 ms when the Referer header arrived, else 1 ms, so the caller can see the headers got through.
 * It runs in the test APK's process, so it shares no memory with the app: everything goes through the intent.
 */
class StubPlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val start = i.getIntExtra("position", -1)
        val headers = i.getStringArrayExtra("headers")?.toList().orEmpty()
        val referer = headers.indexOf("Referer").takeIf { it >= 0 }?.let { headers.getOrNull(it + 1) }
        if (i.getBooleanExtra("return_result", false) && start >= 0) {
            setResult(RESULT_OK, Intent("com.mxtech.intent.result.VIEW")
                .putExtra("position", start + 5_000)
                .putExtra("duration", if (referer == "https://site.example/") 60_000 else 1)
                .putExtra("end_by", "user"))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
