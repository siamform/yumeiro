package dev.asfakul.yumeiro.stream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * B-29: TeraBox shares resolved through TeraBox's own share API, without the WebView finder.
 *
 * Measured on 2026-11-21 against a real share (1024terabox.com/s/1qkXq…, a 955 MB AV1 MKV):
 *  - the page carries a `jsToken` (`fn("<hex>")` inside an encoded script);
 *  - `api/shorturlinfo` with that token returns uk / shareid / sign / timestamp / randsk and the files;
 *  - anonymous `share/list` is refused (errno 105) on every mirror, so no original-file link (dlink);
 *  - anonymous `share/streaming` lists only 3 × 10 s segments + `#EXT-X-ENDLIST`: TeraBox's
 *    30-second no-login preview. That is the "TeraBox stops at 30 s" bug (B-3).
 *
 * Competitors (Hikari NetworkStream.kt, terabox-gateway, TeraBridge) all solve it the same way: a
 * TeraBox sign-in cookie (`ndus`). Yumeiro reuses the sign-in the user makes once in its own web
 * view ([cookieSource]), lists the share with sekey + sign, and plays the original file (dlink),
 * falling back to the full HLS transcode. A preview is never played: without a sign-in the result is
 * [Result.NeedsSignIn].
 */
object TeraBoxApi {
    internal const val DEFAULT_BASE = "https://www.terabox.app"
    /** API origin override (tests, mirror switch). Left at [DEFAULT_BASE], the share link's own origin is used (OWN-10). */
    @Volatile internal var base = DEFAULT_BASE

    /** TeraBox-family hosts that serve the same share API on their own origin. */
    private val BOX_HOSTS = listOf("terabox", "1024tera", "4funbox", "mirrobox", "nephobox", "momerybox", "tibibox", "teraboxapp", "freeterabox", "teraboxlink", "terasharelink")

    /**
     * OWN-10: the origin the API calls go to. A share on 1024terabox.com is listed by 1024terabox.com: its cookies and
     * verify state belong there, and terabox.app answered such shares with a human check more often (2026-11-22).
     */
    fun originFor(shareUrl: String): String {
        if (base != DEFAULT_BASE) return base
        val u = shareUrl.trim().toHttpUrlOrNull() ?: return base
        val h = u.host.lowercase()
        return if (BOX_HOSTS.any { h.contains(it) }) "https://" + h else base
    }

    /** OWN-10: a per-session `browserid` cookie, as TeraBox's own clients send (random; hex works on API 24 TVs). */
    fun freshBrowserId(): String {
        val b = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    /** OWN-10: the share needs an extraction code (password); Yumeiro can't play it without one. */
    fun locked(info: JsonObject): Boolean =
        listOf("need_password", "needpassword", "need_pwd", "is_encrypt").any { k -> info.str(k).let { it == "1" || it == "true" } } ||
            info.str("errno") in setOf("-9", "-12")
    private const val QS = "app_id=250528&web=1&channel=dubox&clienttype=0"

    /** Cookies the user's WebView holds for [url] (a TeraBox sign-in lives here). Set in MTApplication. */
    @Volatile var cookieSource: (String) -> String? = { null }

    const val SIGN_IN_REASON = "TeraBox only gives the full video to signed-in accounts; without one it plays a 30-second preview, " +
        "so Yumeiro won't use that. Sign in to your free TeraBox account on this page once, then go back and play the link again."

    /** Whether this device has any AV1 video decoder (Android 10+ ships one). Set in MTApplication. */
    @Volatile var canPlayAv1: () -> Boolean = { true }

    /** An AV1 original on a phone with no AV1 decoder plays TeraBox's H.264 transcode instead. */
    private val RX_AV1 = Regex("""(^|[^a-z0-9])av1([^a-z0-9]|$)""", RegexOption.IGNORE_CASE)

    fun preferTranscode(fileName: String): Boolean =
        RX_AV1.containsMatchIn(fileName) && !runCatching { canPlayAv1() }.getOrDefault(false)

    sealed interface Result {
        /** [hls] = TeraBox's transcode playlist; otherwise the original file. */
        data class Ok(val streamUrl: String, val fileName: String, val cookie: String?, val userAgent: String, val hls: Boolean) : Result
        /** TeraBox only gives the full video to a signed-in account (anonymous = 30 s preview). */
        data object NeedsSignIn : Result
        /** [retry] = worth one more try with a fresh session (OWN-10). */
        data class Failed(val reason: String, val retry: Boolean = false) : Result
        /**
         * OWN-10: TeraBox wants a human check first (errno 400210 "need verify_v2", 460030 "dragdrop" slider, both
         * seen on 2026-11-22). The user opens the share page in the app's browser and does the check themselves; the
         * cookies it leaves are reused on the next try. Yumeiro never solves or skips the check.
         */
        data object NeedsVerify : Result
    }

    /** OWN-10: answers that mean "do TeraBox's human check on its page first". */
    internal val VERIFY_ERRNOS = setOf("400210", "460030", "400141")

    const val VERIFY_REASON = "TeraBox wants you to confirm you're a person before it lists this file. " +
        "Do TeraBox's check on this page, then go back and play the link again."

    /** `1qkXq…` from `…/s/1qkXq…` or `…?surl=qkXq…` (surl drops the leading "1"). */
    fun shortUrl(url: String): String? {
        val u = url.trim().toHttpUrlOrNull() ?: return null
        val segs = u.pathSegments
        val i = segs.indexOf("s")
        if (i >= 0 && i + 1 < segs.size && segs[i + 1].isNotBlank()) return segs[i + 1]
        u.queryParameter("surl")?.takeIf { it.isNotBlank() }?.let { return "1$it" }
        return null
    }

    private val RX_TOKEN = Regex("""fn(?:%28|\()(?:%22|")([0-9A-Fa-f]{32,})(?:%22|")""")

    fun jsToken(html: String): String? = RX_TOKEN.find(html)?.groupValues?.get(1)

    /** Total length of a media playlist in seconds (sum of #EXTINF). */
    fun playlistSeconds(m3u8: String): Double = m3u8.lineSequence()
        .filter { it.startsWith("#EXTINF:") }
        .sumOf { it.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0 }

    /**
     * True when the playlist is TeraBox's short no-login preview: a closed list of at most a minute
     * for a file far larger than a minute of video could be (> 40 MB).
     */
    fun isPreview(m3u8: String, fileBytes: Long): Boolean =
        "#EXT-X-ENDLIST" in m3u8 && playlistSeconds(m3u8) <= 60.0 && fileBytes > 40L * 1024 * 1024

    private fun JsonObject.str(k: String): String? = (this[k])?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    /** The desktop-client identity TeraBox answers API callers with (same as Hikari's reference client). */
    const val DESKTOP_UA = "terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox"

    fun signedIn(cookie: String?): Boolean =
        cookie?.split(';')?.any { it.substringBefore('=').trim().equals("ndus", true) && it.substringAfter('=', "").isNotBlank() } == true

    /** sekey for share/list: the shorturlinfo `randsk`, URL-decoded. */
    fun sekey(randsk: String?): String? = randsk?.takeIf { it.isNotBlank() }?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private fun q(s: String?) = java.net.URLEncoder.encode(s.orEmpty(), "UTF-8")

    fun resolve(client: OkHttpClient, shareUrl: String, userAgent: String = DESKTOP_UA): Result {
        val short = shortUrl(shareUrl) ?: return Result.Failed("Not a TeraBox share link")
        val origin = originFor(shareUrl)
        val first = attempt(client, shareUrl, short, origin, userAgent)
        // OWN-10: a "verify" or refused list is often just a stale session: read the page again once with a fresh
        // session (only the user's saved sign-in, a new browserid). Never more than one retry, never solving a check.
        if (first !is Result.NeedsVerify && !(first is Result.Failed && first.retry)) return first
        runCatching { android.util.Log.i(TAG, "retry with a fresh session after ${first::class.simpleName}") }
        val second = attempt(client, shareUrl, short, origin, userAgent)
        return if (second is Result.Failed && second.retry && first is Result.NeedsVerify) first else second
    }

    private const val TAG = "TeraBox"

    private fun attempt(client: OkHttpClient, shareUrl: String, short: String, origin: String, userAgent: String): Result {
        val jar = linkedMapOf<String, String>()
        listOf(origin, base, "https://www.terabox.com", "https://www.1024terabox.com").distinct().mapNotNull { runCatching { cookieSource(it) }.getOrNull() }
            .joinToString(";").split(';').forEach { c -> c.split('=', limit = 2).takeIf { it.size == 2 }?.let { jar.putIfAbsent(it[0].trim(), it[1].trim()) } }
        fun cookie() = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { null }
        fun get(url: String, referer: String = "$origin/"): String? {
            val b = Request.Builder().url(url).header("User-Agent", userAgent).header("Referer", referer)
            cookie()?.let { b.header("Cookie", it) }
            return runCatching {
                client.newCall(b.build()).execute().use { r ->
                    r.headers("Set-Cookie").forEach { sc ->
                        sc.substringBefore(';').split('=', limit = 2).takeIf { it.size == 2 }?.let { if (it[0] !in jar) jar[it[0]] = it[1] }
                    }
                    if (r.isSuccessful) r.body?.string() else null
                }
            }.getOrNull()
        }
        fun json(body: String?) = body?.let { runCatching { Json.parseToJsonElement(it) as JsonObject }.getOrNull() }
        if (!signedIn(cookie())) return Result.NeedsSignIn
        val page = get("$origin/wap/share/filelist?surl=${short.removePrefix("1")}")
        var token = page?.let { jsToken(it) }
        if (token == null) token = get("$origin/main")?.let { jsToken(it) }
        if (token == null) return Result.Failed(if (page == null) "TeraBox page didn't load" else "TeraBox changed its page (no token)", retry = page == null)
        if (jar.keys.none { it.equals("browserid", true) }) jar["browserid"] = freshBrowserId()
        val info = json(get("$origin/api/shorturlinfo?$QS&jsToken=$token&shorturl=$short&root=1", referer = shareUrl))
            ?: return Result.Failed("TeraBox didn't list the share", retry = true)
        if (info.str("errno") in VERIFY_ERRNOS) return Result.NeedsVerify
        if (locked(info)) return Result.Failed("This TeraBox share needs an extraction code; open it on TeraBox's page instead")
        if (info.str("errno") != "0") return Result.Failed("TeraBox refused the share (error ${info.str("errno")})", retry = true)
        val file = (info["list"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("category") == "1" && it.str("isdir") != "1" }
            ?: return Result.Failed("This TeraBox share has no video file")
        val name = file.str("server_filename") ?: "TeraBox video"
        // OWN-10: some shares carry the original-file link in shorturlinfo itself; no list call is needed then.
        file.str("dlink")?.takeIf { it.startsWith("http") && !preferTranscode(name) }?.let { return Result.Ok(it, name, cookie(), userAgent, hls = false) }
        val listQuery = "share/list?$QS&jsToken=$token&sekey=${q(sekey(info.str("randsk")))}&sign=${info.str("sign")}" +
            "&timestamp=${info.str("timestamp")}&shareid=${info.str("shareid")}&uk=${info.str("uk")}&shorturl=$short" +
            "&by=name&order=asc&num=1000&page=1&dir=&root=1"
        var list = json(get("$origin/$listQuery", referer = shareUrl))
        if (list?.str("errno") != "0" && origin != base && list?.str("errno") !in VERIFY_ERRNOS) {
            // OWN-10: the main site sometimes lists a mirror's share the mirror refused.
            json(get("https://www.terabox.com/$listQuery", referer = shareUrl))?.takeIf { it.str("errno") == "0" }?.let { list = it }
        }
        if (list?.str("errno") in VERIFY_ERRNOS) return Result.NeedsVerify
        val listed = list
        if (listed?.str("errno") == "0" && !preferTranscode(name)) {
            val dlink = (listed["list"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it.str("fs_id") == file.str("fs_id") }?.str("dlink")?.takeIf { it.startsWith("http") }
            if (dlink != null) return Result.Ok(dlink, name, cookie(), userAgent, hls = false)
        }
        val sq = "$QS&jsToken=$token&uk=${info.str("uk")}&shareid=${info.str("shareid")}&fid=${file.str("fs_id")}" +
            "&sign=${info.str("sign")}&timestamp=${info.str("timestamp")}&esl=1&isplayer=1&ehps=1"
        for (type in listOf("M3U8_AUTO_720", "M3U8_AUTO_480", "M3U8_FLV_264_480")) {
            val url = "$origin/share/streaming?$sq&type=$type"
            val body = get(url) ?: continue
            if (!body.trimStart().startsWith("#EXTM3U")) continue
            // A sign-in that TeraBox no longer honours still gets the preview: never play it.
            if (isPreview(body, file.str("size")?.toLongOrNull() ?: 0L)) return Result.NeedsSignIn
            return Result.Ok(url, name, cookie(), userAgent, hls = true)
        }
        return Result.Failed("TeraBox has no stream for this file yet")
    }
}
