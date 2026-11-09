/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.nicehttp.NiceFile
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * CloudStream's `app` helper with link repair: every get/post/put an extension makes goes through
 * [custom], where protocol-relative, relative and escaped URLs are made absolute (see [UrlFix]).
 */
class SafeRequests(from: Requests, client: OkHttpClient) : Requests(
    client, from.defaultHeaders, from.defaultReferer, from.defaultData, from.defaultCookies,
    from.defaultCacheTime, from.defaultCacheTimeUnit, from.defaultTimeOut, from.responseParser,
) {
    override suspend fun custom(
        method: String, url: String, headers: Map<String, String>, referer: String?,
        params: Map<String, String>, cookies: Map<String, String>, data: Map<String, String>?,
        files: List<NiceFile>?, json: Any?, requestBody: RequestBody?, allowRedirects: Boolean,
        cacheTime: Int, cacheUnit: TimeUnit, timeout: Long, interceptor: Interceptor?, verify: Boolean,
        responseParser: ResponseParser?,
    ): NiceResponse {
        val fixed = if (UrlFix.isHttp(url)) url else {
            val base = referer?.takeIf { UrlFix.isHttp(it) }
                ?: headers.entries.firstOrNull { it.key.equals("referer", true) }?.value?.takeIf { UrlFix.isHttp(it) }
                ?: coroutineContext[ProviderBase]?.mainUrl
            UrlFix.normalize(url, base)
        }
        return super.custom(method, fixed, headers, referer?.let { UrlFix.normalize(it) }, params, cookies, data, files,
            json, requestBody, allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser)
    }
}
