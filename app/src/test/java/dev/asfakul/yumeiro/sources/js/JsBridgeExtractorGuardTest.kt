package dev.asfakul.yumeiro.sources.js

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * X1-3: `JsBridge.extractor` blocks the JS thread with runBlocking. Without a time limit one stuck
 * extractor host hangs the whole JS source. Source guard: the blocking call must stay bounded.
 */
class JsBridgeExtractorGuardTest {
    private val src = File("src/main/java/dev/asfakul/yumeiro/sources/js/JsBridge.kt").readText()

    @Test fun extractorRunBlockingIsBounded() {
        val body = src.substringAfter("private fun extractor(").substringBefore("// endregion")
        assertTrue("runBlocking must wrap withTimeoutOrNull", Regex("""runBlocking\s*\{\s*withTimeoutOrNull\(EXTRACTOR_TIMEOUT_MS\)""").containsMatchIn(body))
        assertTrue("callbacks may arrive on several threads", body.contains("synchronizedList"))
    }

    @Test fun timeoutIsSane() {
        assertTrue(JsBridge.EXTRACTOR_TIMEOUT_MS in 5_000L..60_000L)
    }
}
