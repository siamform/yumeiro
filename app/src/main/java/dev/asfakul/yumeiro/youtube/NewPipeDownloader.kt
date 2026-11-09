/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * NewPipeExtractor's HTTP bridge, running on the app's shared OkHttp stack (DNS fallback, DoH,
 * shared cookie jar). Anonymous by default, like NewPipe. When the optional YouTube account is
 * active for this request ([YtAccount.active]), youtube.com calls carry its cookies on a separate
 * client without the shared session-cookie store, so account cookies never leak into it.
 */
internal class NewPipeDownloader(base: OkHttpClient) : Downloader() {

    private val client = base.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val accountClient: OkHttpClient by lazy {
        client.newBuilder().apply { networkInterceptors().removeAll { it is dev.asfakul.yumeiro.network.SessionCookies } }.build()
    }

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", true) }) builder.header("User-Agent", USER_AGENT)

        val existingCookie = request.headers().entries.firstOrNull { it.key.equals("Cookie", true) }?.value?.joinToString("; ")
        val account = YtAccount.headersFor(request.url(), existingCookie)
        account.forEach { (k, v) -> builder.header(k, v) }
        val http = if (account.isEmpty()) client else accountClient

        http.newCall(builder.build()).execute().use { res ->
            if (res.code == 429) {
                throw ReCaptchaException("YouTube asked for a captcha (rate limited)", request.url())
            }
            val text = res.body?.string().orEmpty()
            return Response(res.code, res.message, res.headers.toMultimap(), text, res.request.url.toString())
        }
    }

    companion object {
        /** Desktop Firefox: what NewPipe sends for web endpoints. */
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
