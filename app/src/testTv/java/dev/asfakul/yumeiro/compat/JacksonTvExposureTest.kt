/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.compat

import com.fasterxml.jackson.databind.DeserializationFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G12 X13-1, owner condition 1 (ADR-015). The TV build carries Jackson 2.13.5, which is affected by
 * known advisories that later lines fixed. These tests pin the facts the exposure analysis relies on,
 * so a change that widens the exposure fails here:
 *  - the shared CloudStream mapper has no default (polymorphic) typing, so the gadget / allowlist
 *    advisories (CVE-2026-54512, -54513, -83557) need an extension that opts in itself;
 *  - deeply nested hostile JSON (CVE-2025-52999 class) only throws inside the call, and the
 *    home-loading pattern (`runCatching`, which also catches Errors) contains it.
 */
class JacksonTvExposureTest {
    private val mapper get() = com.lagradost.cloudstream3.mapper

    @Test fun sharedMapperHasNoDefaultTyping() {
        val m = mapper
        assertNull("default typing turns on polymorphic gadget attacks",
            m.deserializationConfig.getDefaultTyper(m.constructType(Any::class.java)))
        assertNull(m.serializationConfig.getDefaultTyper(m.constructType(Any::class.java)))
        assertFalse(m.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    }

    @Test fun deepNestingIsContainedByTheCallerGuard() {
        val hostile = "[".repeat(200_000) + "]".repeat(200_000)
        val outcomes = mutableMapOf<String, Throwable?>()
        var reachedEnd = false
        // Same shape as OnlineRepository.homeBatch: the extension call sits inside runCatching.
        val t = Thread(null, {
            outcomes["tree"] = runCatching { mapper.readTree(hostile) }.exceptionOrNull()
            outcomes["untyped"] = runCatching { mapper.readValue(hostile, Any::class.java) }.exceptionOrNull()
            outcomes["treeToString"] = runCatching { mapper.readTree(hostile).toString() }.exceptionOrNull()
            reachedEnd = true
        }, "ext-worker", 512L * 1024)
        t.start(); t.join()
        // readTree is iterative in 2.13. Untyped binding hits the depth guard that 2.13.5 has in
        // UntypedObjectDeserializer.Vanilla ("JSON is too deeply nested", ~1000 levels; checked in
        // the 2.13.5 jar and on CI 43825446008), so it fails with a parse error, not a stack overflow.
        // JsonNode.toString recurses (CVE-2026-50193 class): whether it overflows depends on the
        // JVM's frame sizes. Either way only the one call fails and the worker runs to the end.
        assertNull("tree parse: ${outcomes["tree"]}", outcomes["tree"])
        val untyped = outcomes["untyped"]
        assertTrue("untyped: $untyped", untyped is com.fasterxml.jackson.core.JsonProcessingException || untyped is StackOverflowError)
        val str = outcomes["treeToString"]
        assertTrue("treeToString: $str", str == null || str is StackOverflowError)
        assertTrue("the worker did not survive the hostile input", reachedEnd)
        assertFalse(t.isAlive)
    }

    @Test fun ordinaryProviderJsonStillParses() {
        val node = mapper.readTree("""{"a":[1,2,{"b":"c"}],"extra":true}""")
        assertTrue(node["a"].size() == 3)
    }
}
