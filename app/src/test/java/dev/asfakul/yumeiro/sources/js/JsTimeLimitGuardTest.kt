/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SRC-1: QuickJS has no time limit unless one is set. A looping script then holds the source's
 * mutex forever. QuickJS native code is Android-only in this project, so the real loop is
 * proven by JsTimeLimitDeviceTest; this source guard keeps all three runtimes bounded.
 */
class JsTimeLimitGuardTest {
    private fun src(p: String) = File("src/main/java/$p").readText()
    private val limit = Regex("""evaluationTimeoutMillis\s*=\s*[A-Z_0-9]+""")

    @Test fun mangayomiEngineHasATimeLimitAndResetsAfterIt() {
        val s = src("dev/asfakul/yumeiro/sources/js/JsEngine.kt")
        assertTrue("JsEngine runtime has no evaluation time limit", limit.containsMatchIn(s))
        assertTrue("an interrupted runtime must be closed and rebuilt", s.contains("QuickJsInterruptedException") && s.contains("close()"))
    }

    @Test fun extensionHelperEnginesHaveATimeLimit() {
        assertTrue("tachiyomi JavaScriptEngine has no time limit", limit.containsMatchIn(src("eu/kanade/tachiyomi/network/JavaScriptEngine.kt")))
        assertTrue("app.cash QuickJs shim has no time limit", limit.containsMatchIn(src("app/cash/quickjs/QuickJs.kt")))
    }
}
