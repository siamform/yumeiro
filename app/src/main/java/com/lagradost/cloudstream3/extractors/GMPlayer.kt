/*
 * Compatibility copy from CloudStream v4.4.0 (GPL-3.0, https://github.com/recloudstream/cloudstream,
 * library/src/commonMain/kotlin/com/lagradost/cloudstream3/extractors/GMPlayer.kt).
 * CloudStream library 4.8 removed this extractor; older extensions still create it by name, so
 * without it they fail to load (found by the extension link audit, docs/EXTENSION-COMPAT.md).
 */
@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class GMPlayer : ExtractorApi() {
    override val name = "GM Player"
    override val mainUrl = "https://gmplayer.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val ref = referer ?: return null
        val id = url.substringAfter("/video/").substringBefore("/")

        val m3u8 = app.post(
            "$mainUrl/player/index.php?data=$id&do=getVideo",
            mapOf(
                "accept" to "*/*",
                "referer" to ref,
                "x-requested-with" to "XMLHttpRequest",
                "origin" to mainUrl
            ),
            data = mapOf("hash" to id, "r" to ref)
        ).parsed<GmResponse>().videoSource ?: return null

        return listOf(
            ExtractorLink(
                this.name,
                this.name,
                m3u8,
                ref,
                Qualities.Unknown.value,
                headers = mapOf("accept" to "*/*"),
                isM3u8 = true
            )
        )
    }

    private data class GmResponse(
        val videoSource: String? = null
    )
}