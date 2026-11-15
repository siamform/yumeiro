/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.utils.Utils

/**
 * X13-3: NewPipeExtractor calls URLDecoder.decode / URLEncoder.encode with a Charset, which Android
 * has only from API 33. minSdk run 43771214803 (api28): every YouTube stream killed the app with
 * NoSuchMethodError in Utils.decodeUrlUtf8. With desugar_jdk_libs_nio these calls are rewritten to
 * the backport, so they must work on every API level the app supports (phone 28, TV 24).
 */
@RunWith(AndroidJUnit4::class)
class NewPipeCompatDeviceTest {
    @Test fun extractorUrlCodingWorksBelowApi33() {
        assertEquals("a b/ü", Utils.decodeUrlUtf8("a+b%2F%C3%BC"))
        assertEquals("a+b%2F%C3%BC", Utils.encodeUrlUtf8("a b/ü"))
    }

    @Test fun youtubeLinkParsingWorksBelowApi33() {
        // The path in the crash: a watch URL with a list= query goes through getQueryValue → decodeUrlUtf8.
        val id = org.schabi.newpipe.extractor.ServiceList.YouTube.streamLHFactory
            .fromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs").id
        assertEquals("dQw4w9WgXcQ", id)
    }
}
