/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Native side of the Mangayomi JavaScript API: every `sendMessage(channel, argsJson)` from a
 * source lands in [handle]. Element handles live for the lifetime of one bridge (one source).
 */
class JsBridge(
    private val context: Context,
    private val client: OkHttpClient,
    private val prefs: JsPreferences,
) {
    private val elements = HashMap<Int, Element?>()
    private var nextKey = 0
    private var lastHtml: String? = null
    private var lastDoc: Document? = null

    /** Frees element handles between top-level calls so memory doesn't grow. */
    fun reset() { elements.clear(); lastHtml = null; lastDoc = null }

    private fun doc(html: String): Document {
        if (html === lastHtml || html == lastHtml) lastDoc?.let { return it }
        return Jsoup.parse(html).also { lastHtml = html; lastDoc = it }
    }

    private fun keep(e: Element?): Int { nextKey++; elements[nextKey] = e; return nextKey }
    private fun keepAll(list: List<Element>): String = JsonArray(list.map { JsonPrimitive(keep(it)) }).toString()
    private fun el(arg: JsonElement?): Element? = (arg as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()?.let { elements[it] }

    fun handle(channel: String, raw: String): Any? = try {
        val a = JsJson.json.parseToJsonElement(raw).let { it as? JsonArray ?: JsonArray(listOf(it)) }
        dispatch(channel, a)
    } catch (e: Throwable) {
        Log.w(TAG, "$channel failed", e)
        if (channel.startsWith("http_")) buildJsonObject { put("statusCode", 0); put("body", ""); put("headers", JsonObject(emptyMap())) }.toString() else ""
    }

    private fun JsonArray.s(i: Int): String = (getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun dispatch(ch: String, a: JsonArray): Any? = when (ch) {
        "log" -> { Log.d(TAG, a.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }); "" }
        "http_get", "http_head", "http_post", "http_put", "http_delete", "http_patch" -> http(ch.removePrefix("http_").uppercase(), a)
        // Document
        "get_doc_element" -> doc(a.s(0)).let { d -> keep(when (a.s(1)) { "body" -> d.body(); "head" -> d.head(); "documentElement" -> d.firstElementChild(); else -> d }) }
        "get_doc_string" -> doc(a.s(0)).let { d -> if (a.s(1) == "text") d.text() else d.outerHtml() }
        "doc_select_first" -> keep(doc(a.s(0)).selectFirst(a.s(1)))
        "doc_select" -> keepAll(doc(a.s(0)).select(a.s(1)))
        "doc_attr" -> doc(a.s(0)).attr(a.s(1))
        "doc_has_attr" -> doc(a.s(0)).hasAttr(a.s(1))
        "doc_get_element_by_id" -> keep(doc(a.s(0)).getElementById(a.s(1)))
        "doc_get_elements_by" -> doc(a.s(0)).let { d -> keepAll(listBy(d, a.s(1), a.s(2))) }
        "doc_xpath_first" -> xpathFirst(doc(a.s(0)), a.s(1))
        "doc_xpath" -> JsonArray(xpath(doc(a.s(0)), a.s(1)).map(::JsonPrimitive)).toString()
        // Element
        "get_element_string" -> el(a.getOrNull(1)).let { e -> elementString(e, a.s(0)) }
        "ele_selectFirst" -> keep(el(a.getOrNull(1))?.selectFirst(a.s(0)))
        "ele_select" -> keepAll(el(a.getOrNull(1))?.select(a.s(0)).orEmpty())
        "ele_attr" -> el(a.getOrNull(1))?.attr(a.s(0)).orEmpty()
        "ele_has_attr" -> el(a.getOrNull(1))?.hasAttr(a.s(0)) ?: false
        "ele_element_sibling" -> keep(el(a.getOrNull(1))?.let { if (a.s(0) == "nextElementSibling") it.nextElementSibling() else it.previousElementSibling() })
        "ele_get_elements_by" -> keepAll(el(a.getOrNull(2))?.let { listBy(it, a.s(0), a.s(1)) }.orEmpty())
        "ele_xpath", "xpath" -> JsonArray(el(a.getOrNull(1))?.let { xpath(it, a.s(0)) }.orEmpty().map(::JsonPrimitive)).toString()
        "ele_xpathFirst", "xpathFirst" -> el(a.getOrNull(1))?.let { xpathFirst(it, a.s(0)) }.orEmpty()
        // Preferences
        "get" -> prefs.get(a.s(0))
        "getString" -> prefs.getString(a.s(0), a.s(1))
        "setString" -> { prefs.setString(a.s(0), a.s(1)); true }
        // Utilities
        "parseDates" -> parseDates(a)
        "unpackJs" -> JsUnpacker.unpack(a.s(0))
        "unpackJsAndCombine" -> JsUnpacker.unpackAll(a.s(0))
        "deobfuscateJsPassword" -> deobfuscate(a.s(0))
        "cryptoHandler" -> JsCrypto.aesCbc(a.s(0), a.s(1), a.s(2), (a.getOrNull(3) as? JsonPrimitive)?.contentOrNull == "true")
        "decryptAESCryptoJS" -> JsCrypto.cryptoJsDecrypt(a.s(0), a.s(1))
        "encryptAESCryptoJS" -> JsCrypto.cryptoJsEncrypt(a.s(0), a.s(1))
        "decryptAESGCM" -> JsCrypto.aesGcm(a.s(0), a.s(1), a.s(2), a.s(3))
        "createHlsProxyUrl" -> a.s(0)
        "evaluateJavascriptViaWebview" -> ""
        else -> if (ch.endsWith("Extractor")) extractor(a.s(0), a.s(1)) else { Log.w(TAG, "Unsupported channel $ch"); "" }
    }

    // region http

    private fun http(method: String, a: JsonArray): String {
        val url = a.s(2)
        val headers = (a.getOrNull(3) as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
        val bodyArg = a.getOrNull(4)
        val ct = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
        val body = when {
            method == "GET" || method == "HEAD" -> null
            bodyArg == null || bodyArg is JsonNull -> ByteArray(0).toRequestBody(null)
            ct?.contains("json") == true -> bodyArg.toString().toRequestBody(ct.toMediaTypeOrNull())
            bodyArg is JsonObject -> bodyArg.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
                .toRequestBody((ct ?: "application/x-www-form-urlencoded").toMediaTypeOrNull())
            else -> ((bodyArg as? JsonPrimitive)?.contentOrNull ?: bodyArg.toString()).toRequestBody(ct?.toMediaTypeOrNull())
        }
        val req = Request.Builder().url(url).headers(headers.toHeaders()).method(method, body).build()
        val timed = (a.getOrNull(1) as? JsonObject)?.get("timeout")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
            ?.let { client.newBuilder().callTimeout(it, TimeUnit.SECONDS).build() } ?: client
        return timed.newCall(req).execute().use { r ->
            buildJsonObject {
                put("statusCode", r.code)
                put("body", if (method == "HEAD") "" else r.body.string())
                put("headers", JsonObject(r.headers.toMultimap().mapValues { JsonPrimitive(it.value.joinToString(",")) }))
                put("isRedirect", r.isRedirect)
                put("reasonPhrase", r.message)
                put("request", buildJsonObject { put("url", r.request.url.toString()); put("method", r.request.method) })
            }.toString()
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // endregion

    // region dom

    private fun listBy(root: Element, type: String, name: String): List<Element> = when (type) {
        "children" -> root.children()
        "getElementsByTagName" -> root.getElementsByTag(name)
        else -> root.getElementsByClass(name)
    }

    private fun elementString(e: Element?, type: String): String {
        e ?: return ""
        return when (type) {
            "text" -> e.text()
            "innerHtml" -> e.html()
            "outerHtml" -> e.outerHtml()
            "className" -> e.className()
            "localName" -> e.tagName()
            "namespaceUri" -> ""
            "getSrc" -> e.attr("src").ifBlank { e.selectFirst("[src]")?.attr("src").orEmpty() }
            "getImg" -> (e.takeIf { it.tagName() == "img" } ?: e.selectFirst("img"))?.let { img ->
                listOf("data-src", "data-lazy-src", "data-original", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
            }.orEmpty()
            "getHref" -> e.attr("href").ifBlank { e.selectFirst("[href]")?.attr("href").orEmpty() }
            else -> e.attr("data-src").ifBlank { e.selectFirst("[data-src]")?.attr("data-src").orEmpty() }
        }
    }

    /** XPath with `/text()` and `/@attr` endings, which return strings like Mangayomi. */
    private fun xpath(root: Element, path: String): List<String> {
        val attr = Regex("""/@([\w:-]+)$""").find(path)?.groupValues?.get(1)
        val text = path.endsWith("/text()")
        val base = path.removeSuffix("/text()").let { if (attr != null) it.substringBeforeLast("/@") else it }
        val nodes = runCatching { root.selectXpath(base) }.getOrDefault(org.jsoup.select.Elements())
        return nodes.map { if (attr != null) it.attr(attr) else if (text) it.ownText() else it.text() }
    }

    private fun xpathFirst(root: Element, path: String) = xpath(root, path).firstOrNull().orEmpty()

    // endregion

    // region utils

    private fun parseDates(a: JsonArray): String {
        val values = (a.getOrNull(0) as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
        val fmt = a.s(1).replace("yyyy", "yyyy").ifBlank { "yyyy-MM-dd" }
        val locale = a.s(2).ifBlank { "en" }.let { Locale.forLanguageTag(it.replace('_', '-')) }
        val sdf = runCatching { SimpleDateFormat(fmt, locale) }.getOrNull()
        return JsonArray(values.map { v -> JsonPrimitive((relative(v) ?: runCatching { sdf?.parse(v.trim())?.time }.getOrNull() ?: 0L).toString()) }).toString()
    }

    private fun relative(v: String): Long? {
        val m = Regex("""(\d+)\s*(sec|min|hour|day|week|month|year)""", RegexOption.IGNORE_CASE).find(v) ?: return when {
            v.contains("today", true) -> System.currentTimeMillis()
            v.contains("yesterday", true) -> System.currentTimeMillis() - 86_400_000L
            else -> null
        }
        val n = m.groupValues[1].toLong()
        val unit = when (m.groupValues[2].lowercase()) {
            "sec" -> 1_000L; "min" -> 60_000L; "hour" -> 3_600_000L; "day" -> 86_400_000L
            "week" -> 604_800_000L; "month" -> 2_592_000_000L; else -> 31_536_000_000L
        }
        return System.currentTimeMillis() - n * unit
    }

    private fun deobfuscate(input: String): String {
        // Mangayomi's helper decodes "Ru" style string-array obfuscation; most sources only need the raw string.
        return input
    }

    /** Every Mangayomi `xxxExtractor(url, …)` is answered by CloudStream's extractor collection. */
    private fun extractor(url: String, prefix: String): String {
        val videos = mutableListOf<JsonObject>()
        val subs = mutableListOf<JsonObject>()
        runCatching {
            runBlocking {
                loadExtractor(url, null, { s -> subs += buildJsonObject { put("file", s.url); put("label", s.lang) } }) { l ->
                    videos += buildJsonObject {
                        put("url", l.url); put("originalUrl", l.url)
                        put("quality", listOf(prefix, l.name, if (l.quality > 0) "${l.quality}p" else "").filter { it.isNotBlank() }.joinToString(" "))
                        put("headers", JsonObject((l.headers + listOfNotNull(l.referer.takeIf { it.isNotBlank() }?.let { "Referer" to it })).mapValues { JsonPrimitive(it.value) }))
                    }
                }
            }
        }
        return JsonArray(videos.map { v -> JsonObject(v + ("subtitles" to JsonArray(subs))) }).toString()
    }

    // endregion

    companion object { private const val TAG = "JsBridge" }
}

/** Dean Edwards' p,a,c,k,e,d unpacker. */
object JsUnpacker {
    private val packed = Regex("""\}\s*\('(.*)',\s*(\d+|\[\]),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)

    fun unpack(src: String): String {
        val m = packed.find(src) ?: return src
        val payload = m.groupValues[1].replace("\\'", "'")
        val radix = m.groupValues[2].toIntOrNull() ?: 36
        val count = m.groupValues[3].toInt()
        val words = m.groupValues[4].split("|")
        if (words.size != count) return src
        return Regex("""\b\w+\b""").replace(payload) { w ->
            val idx = decode(w.value, radix)
            if (idx != null && idx < words.size && words[idx].isNotEmpty()) words[idx] else w.value
        }
    }

    fun unpackAll(src: String): String = Regex("""eval\(function\(p,a,c,k,e,[rd]\).*?\.split\('\|'\).*?\)\)""", RegexOption.DOT_MATCHES_ALL)
        .findAll(src).joinToString("\n") { unpack(it.value) }.ifBlank { src }

    private fun decode(word: String, radix: Int): Int? {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var n = 0
        for (ch in word) { val d = alphabet.indexOf(ch); if (d < 0 || d >= maxOf(radix, 2).coerceAtMost(62)) return null; n = n * radix + d }
        return n
    }
}
