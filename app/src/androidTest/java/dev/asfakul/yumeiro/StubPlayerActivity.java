/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * F-07 device check: a stand-in external player that follows MX Player's published intent API
 * (https://mx.j2inter.com/api): it reads `position`, `title` and `headers` (key, value, key, value…),
 * and returns `position` / `duration` / `end_by` only when the caller asked with `return_result`.
 * It "plays" for 5 s: the position it returns is the start position + 5000 ms. The duration it returns
 * is 60 000 ms when the Referer header arrived, else 1 ms, so the caller can see the headers got through.
 * It runs in the test APK's process, so it shares no memory with the app: everything goes through the intent.
 * Java on purpose: in its own process the test APK has no Kotlin runtime (device run 44896550636:
 * NoClassDefFoundError kotlin/collections/ArraysKt when it was Kotlin).
 */
public class StubPlayerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        int start = i.getIntExtra("position", -1);
        String[] headers = i.getStringArrayExtra("headers");
        String referer = null;
        if (headers != null) {
            for (int k = 0; k + 1 < headers.length; k += 2) {
                if ("Referer".equals(headers[k])) { referer = headers[k + 1]; break; }
            }
        }
        if (i.getBooleanExtra("return_result", false) && start >= 0) {
            setResult(RESULT_OK, new Intent("com.mxtech.intent.result.VIEW")
                .putExtra("position", start + 5_000)
                .putExtra("duration", "https://site.example/".equals(referer) ? 60_000 : 1)
                .putExtra("end_by", "user"));
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
