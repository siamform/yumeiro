/*
 * Compatibility copy from CloudStream v4.4.0 (GPL-3.0, https://github.com/recloudstream/cloudstream,
 * library/src/commonMain/kotlin/com/lagradost/cloudstream3/extractors/Okrulink.kt).
 * CloudStream library 4.8 removed this extractor; older extensions still create it by name, so
 * without it they fail to load (found by the extension link audit, docs/EXTENSION-COMPAT.md).
 */
@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

data class Okrulinkdata (
    @JsonProperty("status" ) var status : String? = null,
    @JsonProperty("url"    ) var url    : String? = null
)

open class Okrulink: ExtractorApi() {
    override var mainUrl = "https://okru.link"
    override var name = "Okrulink"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val key = url.substringAfter("html?t=")
        val request = app.post("https://apizz.okru.link/decoding", allowRedirects = false,
            data = mapOf("video" to key)
        ).parsedSafe<Okrulinkdata>()
        if (request?.url != null) {
            sources.add(
                ExtractorLink(
                    name,
                    name,
                    request.url!!,
                    "",
                    Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        return sources
    }
}
