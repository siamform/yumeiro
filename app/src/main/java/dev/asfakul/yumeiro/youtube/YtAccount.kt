/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import android.webkit.CookieManager
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/**
 * Optional YouTube sign-in. Off unless the user signs in, and even then used only when YouTube
 * refuses an anonymous request ("Sign in to confirm you're not a bot", age-restricted videos),
 * the same narrow use PipePipe makes of it. Browsing, search and recommendations stay anonymous.
 *
 * How it works: Google's own sign-in page runs in a WebView (we never see the password); once
 * youtube.com loads, its cookies are copied and sealed with the Android Keystore
 * ([OnlineSettings.setSecret], excluded from backups). Requests to youtube.com then carry the
 * cookies plus the `SAPISIDHASH` header YouTube's web client sends ([YtAuthHash]).
 */
object YtAccount {

    enum class Mode(val label: String, val detail: String) {
        WHEN_BLOCKED("Only when YouTube blocks a video", "Recommended. Everything stays anonymous until YouTube refuses to play something"),
        ALWAYS("Always", "Every YouTube request uses your account. Fewer blocks, but YouTube sees all your viewing"),
    }

    data class State(val signedIn: Boolean = false, val since: Long = 0L, val mode: Mode = Mode.WHEN_BLOCKED)

    private const val PREFS = "yt_account"
    private const val SECRET = "yt_cookie"
    const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var cookie: String? = null
    @Volatile private var loaded = false
    private val forced = ThreadLocal<Boolean>()

    fun init(context: Context) {
        if (loaded) return
        OnlineSettings.init(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cookie = OnlineSettings.secret(SECRET)?.takeIf { hasLoginCookies(it) }
        _state.value = State(
            signedIn = cookie != null,
            since = p.getLong("since", 0L),
            mode = Mode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: Mode.WHEN_BLOCKED,
        )
        loaded = true
    }

    /** Saves a captured cookie string. Returns false (and saves nothing) when it isn't a real sign-in. */
    fun save(context: Context, rawCookie: String, now: Long = System.currentTimeMillis()): Boolean {
        init(context)
        val clean = sanitize(rawCookie)
        if (!hasLoginCookies(clean)) return false
        OnlineSettings.setSecret(context, SECRET, clean)
        cookie = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("since", now).apply()
        _state.value = _state.value.copy(signedIn = true, since = now)
        YouTube.invalidateAll()
        return true
    }

    fun setMode(context: Context, mode: Mode) {
        init(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
        _state.value = _state.value.copy(mode = mode)
    }

    /** Forgets the account: sealed cookie, the Google/YouTube cookies in the WebView store, cached streams. */
    fun signOut(context: Context) {
        init(context)
        OnlineSettings.setSecret(context, SECRET, null)
        cookie = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("since").apply()
        _state.value = _state.value.copy(signedIn = false, since = 0L)
        runCatching { expireWebCookies(CookieManager.getInstance()) }
        YouTube.invalidateAll()
    }

    /** Whether the current thread's YouTube requests should carry the account. */
    fun active(): Boolean = cookie != null && (_state.value.mode == Mode.ALWAYS || forced.get() == true)

    /**
     * Runs [block] anonymously; if YouTube refuses with a sign-in wall and an account is saved
     * (mode [Mode.WHEN_BLOCKED]), runs it once more with the account. NewPipe extraction is
     * synchronous, so a thread-local flag scopes the account to exactly this retry.
     */
    fun <T> retryWithAccount(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (cookie == null || _state.value.mode == Mode.ALWAYS || !accountCanHelp(e)) throw e
        forced.set(true)
        try { block() } finally { forced.remove() }
    }

    /** Extra headers for [url] when the account is [active], or an empty map. */
    fun headersFor(url: String, existingCookie: String? = null, nowSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        if (!active()) return emptyMap()
        return accountHeaders(url, cookie ?: return emptyMap(), existingCookie, nowSec)
    }

    // region pure helpers (unit-tested)

    internal fun accountHeaders(url: String, cookie: String, existingCookie: String?, nowSec: Long): Map<String, String> {
        val host = url.toHttpUrlOrNull()?.host ?: return emptyMap()
        if (!appliesTo(host)) return emptyMap()
        val out = linkedMapOf("Cookie" to mergeCookies(existingCookie, cookie))
        sapisid(cookie)?.let { s ->
            val origin = if (host == "music.youtube.com") "https://music.youtube.com" else "https://www.youtube.com"
            out["Authorization"] = YtAuthHash.header(s, origin, nowSec)
            out["X-Origin"] = origin
            out["X-Goog-AuthUser"] = "0"
        }
        return out
    }

    /** Only YouTube's own web hosts ever see the account, never googlevideo, ytimg or other sites. */
    internal fun appliesTo(host: String): Boolean {
        val h = host.lowercase()
        return h == "youtube.com" || h == "www.youtube.com" || h == "m.youtube.com" || h == "music.youtube.com"
    }

    internal fun cookieMap(cookie: String): Map<String, String> = cookie.split(';')
        .mapNotNull { part -> part.trim().takeIf { it.contains('=') }?.let { it.substringBefore('=').trim() to it.substringAfter('=').trim() } }
        .filter { it.first.isNotEmpty() }
        .toMap()

    /** A signed-in YouTube session has an API-SID cookie and a session-ID cookie. */
    internal fun hasLoginCookies(cookie: String): Boolean {
        val m = cookieMap(cookie)
        val apisid = listOf("SAPISID", "__Secure-3PAPISID").any { !m[it].isNullOrBlank() }
        val sid = listOf("SID", "__Secure-1PSID", "__Secure-3PSID").any { !m[it].isNullOrBlank() }
        return apisid && sid
    }

    internal fun sapisid(cookie: String): String? = cookieMap(cookie).let { it["SAPISID"] ?: it["__Secure-3PAPISID"] }?.takeIf { it.isNotBlank() }

    /** Drops empty pairs and anything that could break a header (CR/LF). */
    internal fun sanitize(raw: String): String = raw.replace("\r", "").replace("\n", "")
        .split(';').map { it.trim() }.filter { it.contains('=') && it.substringBefore('=').isNotBlank() }
        .distinctBy { it.substringBefore('=') }.joinToString("; ")

    /** Keeps the extractor's own cookies (consent etc.) and adds account cookies it didn't set. */
    internal fun mergeCookies(existing: String?, account: String): String {
        if (existing.isNullOrBlank()) return account
        val have = cookieMap(existing).keys
        val extra = cookieMap(account).filterKeys { it !in have }.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return if (extra.isEmpty()) existing else "$existing; $extra"
    }

    internal fun accountCanHelp(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.take(8).any { it is SignInConfirmNotBotException || it is AgeRestrictedContentException }

    /** What to tell the user when YouTube put up a sign-in wall. */
    fun blockedHint(): String = when {
        cookie == null -> "YouTube wants a sign-in for this video. Try another network, or sign in under Settings → YouTube account"
        else -> "YouTube still refused this video with your account. Try again later or on another network"
    }

    // endregion

    private val webCookieUrls = listOf(
        "https://www.youtube.com", "https://youtube.com", "https://m.youtube.com", "https://music.youtube.com",
        "https://accounts.google.com", "https://www.google.com", "https://google.com",
    )

    /** Captures the merged YouTube cookie string from the WebView store after sign-in. */
    fun captureFromWebView(cm: CookieManager = CookieManager.getInstance()): String? {
        val parts = listOf("https://www.youtube.com", "https://music.youtube.com")
            .mapNotNull { runCatching { cm.getCookie(it) }.getOrNull() }
        if (parts.isEmpty()) return null
        val merged = sanitize(parts.joinToString("; "))
        return merged.takeIf { hasLoginCookies(it) }
    }

    /** Expires only Google/YouTube cookies, leaving other sites' sessions (Cloudflare checks) alone. */
    private fun expireWebCookies(cm: CookieManager) {
        for (u in webCookieUrls) {
            val names = cookieMap(cm.getCookie(u).orEmpty()).keys
            val host = u.substringAfter("://")
            val domain = "." + host.removePrefix("www.").removePrefix("m.").removePrefix("music.").removePrefix("accounts.")
            for (n in names) {
                cm.setCookie(u, "$n=; Max-Age=0; Path=/")
                cm.setCookie(u, "$n=; Max-Age=0; Path=/; Domain=$domain")
            }
        }
        cm.flush()
    }
}
