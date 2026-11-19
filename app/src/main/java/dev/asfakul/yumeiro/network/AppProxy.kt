/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import android.os.Looper
import android.util.Log
import android.webkit.HttpAuthHandler
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.security.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.net.URLConnection

/**
 * NBUG-13 (ADR-045): the user's own proxy (HTTP or SOCKS5) for everything Yumeiro loads: the shared
 * OkHttp client (extensions, images, playback, downloads), the five HttpURLConnection calls (subtitles,
 * app update) and WebView pages (androidx ProxyController, where the WebView supports it).
 *
 * Off (the default) changes nothing: [select] returns what the system selector returns, no
 * authenticator is installed and WebView is never touched. Loopback and private-network addresses never
 * go through the proxy (the torrent server, casting and home servers keep working). On, a proxy that
 * can't be reached fails the request; it never silently falls back to a direct connection.
 *
 * The password is sealed with [SecretBox] in its own file, which backups exclude; it never reaches logs.
 */
object AppProxy : ProxySelector(), okhttp3.Authenticator {
    private const val TAG = "AppProxy"
    private const val PREFS = "mt_network"
    const val SECRET_PREFS = "mt_proxy_secret"

    enum class Type(val label: String) { OFF("Off"), HTTP("HTTP"), SOCKS("SOCKS5") }

    data class Config(val type: Type = Type.OFF, val host: String = "", val port: Int = 0, val user: String = "", val pass: String = "") {
        val on: Boolean get() = type != Type.OFF && validate(type, host, port) == null
        val hasLogin: Boolean get() = user.isNotEmpty()
        val summary: String get() = if (!on) "Off" else "${type.label} $host:$port" + if (hasLogin) " · with login" else ""
        // Never print the login.
        override fun toString() = "Config(type=$type, host=$host, port=$port, login=${if (hasLogin) "set" else "none"})"
    }

    enum class WebState { NOT_USED, APPLYING, ON, UNSUPPORTED, FAILED }

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()
    private val _web = MutableStateFlow(WebState.NOT_USED)
    val webState: StateFlow<WebState> = _web.asStateFlow()

    @Volatile private var app: Context? = null
    @Volatile private var loaded = false
    @Volatile private var webApplied: Config? = null
    @Volatile private var javaAuthInstalled = false

    /** Application.onCreate: no disk access. Settings load on the first request or the settings screen. */
    fun attach(context: Context) {
        app = context.applicationContext ?: context
    }

    /** Called once at start when the main thread is idle: only a user with the proxy on pays for WebView set-up. */
    fun applyWhenIdle(context: Context) {
        val c = context.applicationContext ?: context
        runCatching {
            Looper.getMainLooper().queue.addIdleHandler {
                Thread {
                    ensureLoaded()
                    if (_config.value.on) ContextCompat.getMainExecutor(c).execute { applyWebView(c) }
                }.apply { name = "yu-proxy-load"; isDaemon = true }.start()
                false
            }
        }
    }

    fun ensureLoaded() {
        if (!loaded) app?.let { load(it) }
    }

    @Synchronized
    fun load(context: Context): Config {
        val c = context.applicationContext ?: context
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = runCatching { Type.valueOf(p.getString("proxy_type", null) ?: "OFF") }.getOrDefault(Type.OFF)
        val sealed = c.getSharedPreferences(SECRET_PREFS, Context.MODE_PRIVATE).getString("pass", null)
        val cfg = Config(
            type = if (FeatureFlags.on(c, FeatureFlags.Flag.APP_PROXY)) type else Type.OFF,
            host = p.getString("proxy_host", null).orEmpty(),
            port = p.getInt("proxy_port", 0),
            user = p.getString("proxy_user", null).orEmpty(),
            pass = sealed?.let { SecretBox.open(it) }.orEmpty(),
        )
        activate(cfg)
        loaded = true
        app = c
        return cfg
    }

    /** Saves and applies. Returns an error message for the user, or null. */
    @Synchronized
    fun save(context: Context, cfg: Config): String? {
        val c = context.applicationContext ?: context
        val clean = cfg.copy(host = cfg.host.trim(), user = cfg.user.trim())
        if (clean.type != Type.OFF) validate(clean.type, clean.host, clean.port)?.let { return it }
        val sealed = if (clean.pass.isEmpty()) null else SecretBox.seal(clean.pass) ?: return "Couldn't store the password safely on this device."
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("proxy_type", clean.type.name).putString("proxy_host", clean.host)
            .putInt("proxy_port", clean.port).putString("proxy_user", clean.user).commit()
        c.getSharedPreferences(SECRET_PREFS, Context.MODE_PRIVATE).edit().apply { if (sealed == null) remove("pass") else putString("pass", sealed) }.commit()
        activate(clean)
        loaded = true
        app = c
        ContextCompat.getMainExecutor(c).execute { applyWebView(c) }
        return null
    }

    private fun activate(cfg: Config) {
        _config.value = cfg
        // SOCKS5 logins and HttpURLConnection's HTTPS tunnels ask java.net.Authenticator. It is set only
        // while a proxy with a login is on, and answers only for the proxy's own host and port.
        if (cfg.on && cfg.hasLogin) {
            java.net.Authenticator.setDefault(JavaAuth); javaAuthInstalled = true
        } else if (javaAuthInstalled) {
            java.net.Authenticator.setDefault(null); javaAuthInstalled = false
        }
        Log.i(TAG, "proxy ${cfg.summary.substringBefore(" ·")}")
    }

    /** User-facing check of host and port; null when fine. */
    fun validate(type: Type, host: String, port: Int): String? = when {
        type == Type.OFF -> null
        host.isBlank() -> "Enter the proxy's address."
        host.length > 253 || host.any { it.isWhitespace() || it == '/' || it == '@' || it == '?' || it == '#' } || "://" in host ->
            "Enter only the address, like proxy.example.com or 192.168.1.5 (no http:// or path)."
        port !in 1..65535 -> "Port must be a number from 1 to 65535."
        else -> null
    }

    /** Loopback, link-local and private networks stay direct (local servers, casting, home servers). */
    fun direct(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h == "::1") return true
        if (h.startsWith("fe80:") || h.startsWith("fc") && ':' in h || h.startsWith("fd") && ':' in h) return true
        val p = h.split('.')
        if (p.size != 4) return false
        val n = p.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 127 || n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31) || (n[0] == 169 && n[1] == 254)
    }

    fun javaProxy(cfg: Config): Proxy =
        Proxy(if (cfg.type == Type.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP, InetSocketAddress.createUnresolved(cfg.host, cfg.port))

    override fun select(uri: URI?): List<Proxy> {
        ensureLoaded()
        val cfg = _config.value
        val host = uri?.host
        if (!cfg.on || host == null || direct(host)) return system(uri)
        return listOf(javaProxy(cfg))
    }

    private fun system(uri: URI?): List<Proxy> = runCatching {
        ProxySelector.getDefault()?.takeIf { it !== this }?.select(uri)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}

    /** OkHttp: answers a 407 from the HTTP proxy once with the saved login. */
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 407) return null
        val cfg = _config.value
        if (!cfg.on || cfg.type != Type.HTTP || !cfg.hasLogin) return null
        if (response.request.header("Proxy-Authorization") != null) return null // already tried: wrong login
        return response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(cfg.user, cfg.pass)).build()
    }

    /** For the HttpURLConnection callers (subtitles, app update): same rules as OkHttp. */
    fun open(url: URL): URLConnection {
        val p = runCatching { select(url.toURI()).first() }.getOrDefault(Proxy.NO_PROXY)
        return if (p == Proxy.NO_PROXY) url.openConnection() else url.openConnection(p)
    }

    private object JavaAuth : java.net.Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val cfg = _config.value
            if (!cfg.on || !cfg.hasLogin) return null
            if (!requestingHost.equals(cfg.host, ignoreCase = true) || requestingPort != cfg.port) return null
            return PasswordAuthentication(cfg.user, cfg.pass.toCharArray())
        }
    }

    private var webBusy = false
    private var webAgain = false

    /**
     * WebView pages: proxy override for the whole app's WebViews (Android has no per-WebView proxy).
     * Main thread only. The override is applied asynchronously; a change while one is in flight is applied
     * when it finishes, so the last saved setting always wins.
     */
    @android.annotation.SuppressLint("RequiresFeature")
    fun applyWebView(context: Context) {
        if (webBusy) { webAgain = true; return }
        val cfg = _config.value
        if (!cfg.on && webApplied == null) { _web.value = if (_web.value == WebState.UNSUPPORTED) WebState.UNSUPPORTED else WebState.NOT_USED; return }
        if (cfg.on && webApplied == cfg) { _web.value = WebState.ON; return }
        runCatching {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) { _web.value = WebState.UNSUPPORTED; return }
            val exec = ContextCompat.getMainExecutor(context)
            val done = Runnable {
                webBusy = false
                if (webAgain || _config.value != cfg) { webAgain = false; applyWebView(context) }
            }
            webBusy = true
            if (cfg.on) {
                _web.value = WebState.APPLYING
                val rule = (if (cfg.type == Type.SOCKS) "socks5://" else "") + webHost(cfg.host) + ":" + cfg.port
                val pc = ProxyConfig.Builder().addProxyRule(rule).apply {
                    listOf("localhost", "*.local", "127.0.0.1/8", "[::1]", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16").forEach { addBypassRule(it) }
                }.build()
                ProxyController.getInstance().setProxyOverride(pc, exec) { webApplied = cfg; _web.value = WebState.ON; done.run() }
            } else {
                ProxyController.getInstance().clearProxyOverride(exec) { webApplied = null; _web.value = WebState.NOT_USED; done.run() }
            }
        }.onFailure { webBusy = false; Log.w(TAG, "WebView proxy: ${it.javaClass.simpleName}"); _web.value = WebState.FAILED }
    }

    private fun webHost(h: String) = if (':' in h && !h.startsWith("[")) "[$h]" else h

    /** Called by [SafeWebView.harden] before a page loads: makes sure the override is in place. */
    fun ensureWebView(context: Context) {
        ensureLoaded()
        if (_config.value.on || webApplied != null) applyWebView(context.applicationContext ?: context)
    }

    private val webAuthTries = ArrayDeque<Long>()

    /** WebView asks for the proxy's login: answer with the saved one (at most 3 times a minute, then cancel). */
    fun answerWebAuth(host: String?, handler: HttpAuthHandler): Boolean {
        val cfg = _config.value
        if (!cfg.on || !cfg.hasLogin || host == null || !host.trim('[', ']').equals(cfg.host.trim('[', ']'), ignoreCase = true)) return false
        val now = System.currentTimeMillis()
        synchronized(webAuthTries) {
            while (webAuthTries.isNotEmpty() && now - webAuthTries.first() > 60_000) webAuthTries.removeFirst()
            if (webAuthTries.size >= 3) { handler.cancel(); return true }
            webAuthTries.addLast(now)
        }
        handler.proceed(cfg.user, cfg.pass)
        return true
    }

    /** Settings → Network → Test: one small request through the proxy; the message for the user. */
    fun test(context: Context, cfg: Config): String {
        validate(cfg.type, cfg.host.trim(), cfg.port)?.let { return "Failed · $it" }
        val t0 = System.currentTimeMillis()
        val client = NetworkClient.get(context).newBuilder()
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?) = listOf(javaProxy(cfg.copy(host = cfg.host.trim())))
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
            })
            .proxyAuthenticator { _, r ->
                if (r.code != 407 || !cfg.hasLogin || r.request.header("Proxy-Authorization") != null) null
                else r.request.newBuilder().header("Proxy-Authorization", Credentials.basic(cfg.user.trim(), cfg.pass)).build()
            }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return runCatching {
            client.newCall(Request.Builder().url(TEST_URL).build()).execute().use { r ->
                when {
                    r.code == 407 -> "Failed · the proxy wants a login (or the login is wrong)."
                    r.code in 200..399 -> "Works · through ${cfg.type.label} ${cfg.host.trim()}:${cfg.port} in ${System.currentTimeMillis() - t0} ms"
                    else -> "Failed · the proxy answered HTTP ${r.code}."
                }
            }
        }.getOrElse { e -> "Failed · " + message(e, cfg) }
    }

    @Volatile var TEST_URL = "https://www.gstatic.com/generate_204"
        internal set

    /** What a failed request through the proxy means, in words. */
    fun message(e: Throwable, cfg: Config = _config.value): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("407") || m.contains("Proxy Authentication", true) -> "the proxy wants a login (or the login is wrong)."
            e is java.net.UnknownHostException -> "couldn't find the proxy ${cfg.host}."
            e is java.net.ConnectException || e is java.net.SocketTimeoutException || m.contains("SOCKS", true) -> "couldn't reach the proxy ${cfg.host}:${cfg.port}."
            else -> dev.asfakul.yumeiro.online.OnlineErrors.friendly(e, "no answer")
        }
    }

    internal fun setForTests(cfg: Config) { activate(cfg); loaded = true }
    internal fun resetForTests() { loaded = false; app = null; activate(Config()) }
}
