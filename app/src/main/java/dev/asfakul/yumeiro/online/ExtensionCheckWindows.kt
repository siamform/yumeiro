/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import dev.asfakul.yumeiro.online.web.VerificationCenter
import dev.asfakul.yumeiro.online.web.VerificationPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * ADR-059 (VERIFY-1): the security-check windows extensions open themselves, routed through
 * [VerificationCenter] so they behave like one check per site instead of a popup that keeps coming back.
 *
 * Field probe 45345639530 (api36, release build): AnimePahe's own "Quick security check" sheet opened
 * full screen over the extension market, and the page inside was Cloudflare's "Sorry, you have been
 * blocked · You are unable to access animepahe.pw" — a firewall block that no tap can pass. The
 * extension asks again about every minute (its own 60 s throttle) and on every request.
 *
 * A check window is concealed the moment it appears (before it is drawn) and its page is read:
 *  - the site's firewall blocks this network → the window is closed, the site is marked BLOCKED for
 *    [VerificationPolicy.BLOCK_HOLD_MS], one calm note says why; later check windows for it close at once;
 *  - the page passes by itself (the extension closes its window when the cookie arrives) → recorded as
 *    passed, and the user never saw a popup;
 *  - the page wants a person (Turnstile / hCaptcha / reCAPTCHA widget, or still checking after
 *    [REVEAL_AFTER_MS]) → it is shown, framed as "From extension: …", once; a second check window for
 *    the same site while one is open is closed;
 *  - the user closed it without passing → the site is paused (10 min, then 30 min, 2 h, 6 h, a day);
 *    check windows for a paused site close at once. Extensions → Security checks → Check now lifts it.
 * A check that arrives during a video or while the user types waits behind the existing quiet card.
 * Nothing on the page is clicked or solved for the user.
 */
object ExtensionCheckWindows {
    private const val TAG = "ExtensionChecks"
    private const val POLL_MS = 500L
    /** A check page still running after this long is shown (it is waiting for a person). */
    const val REVEAL_AFTER_MS = 6_000L
    /** Any check window still open after this long is shown, whatever its page. */
    const val SHOW_ANYWAY_MS = 20_000L
    /** How long a concealed check page is watched before it is shown anyway. */
    private const val READ_EVERY_MS = 1_000L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    /** Cookie reads go to their own thread (CookieManager calls wait on WebView's cookie store). */
    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "yu-ext-checks").apply { isDaemon = true } } }

    /** The check window on screen now (at most one), per site. */
    private val shown = ConcurrentHashMap<String, View>()
    /** Last site each extension's check windows were for (a new window's page may not have loaded yet). */
    private val lastHost = ConcurrentHashMap<String, String>()

    enum class Page { BLOCK, HUMAN, CHALLENGE, PAGE, BLANK }
    enum class Step { WAIT, CLOSE_BLOCKED, REVEAL, KEEP, HAND_OFF }

    private val CHECK_WORDS = Regex(
        "(security check|verify (you|that you)|are you (a )?human|not a robot|captcha|turnstile|cloudflare|ddos-guard|" +
            "checking your browser|just a moment|bot check|browser check|protected by)",
        RegexOption.IGNORE_CASE,
    )

    /** A window with a web page and check wording: an extension's own security check. Pure → unit-tested. */
    fun looksLikeCheck(s: ExtensionPopups.Snapshot): Boolean =
        s.hasWeb && !s.hasInput && CHECK_WORDS.containsMatchIn(s.texts.joinToString(" \n "))

    /** What the page in a check window is, from [KIND_JS]'s answer. Pure → unit-tested. */
    fun page(js: String?): Page = when (js?.trim('"', ' ')) {
        "block" -> Page.BLOCK
        "human" -> Page.HUMAN
        "challenge" -> Page.CHALLENGE
        "page" -> Page.PAGE
        else -> Page.BLANK
    }

    /** The next step for a concealed check window. Pure → unit-tested. */
    fun step(page: Page, elapsedMs: Long, mayAsk: Boolean, quiet: Boolean = false): Step = when {
        page == Page.BLOCK -> Step.CLOSE_BLOCKED
        !mayAsk -> Step.KEEP // paused or blocked meanwhile: the caller closes it
        // VERIFY-3 (A5): never shown by itself. A tap-the-box widget goes to Yumeiro's own check at once; a running
        // check gets [SHOW_ANYWAY_MS] to pass unseen (most do), then goes there too.
        quiet && page == Page.HUMAN -> Step.HAND_OFF
        quiet -> if (elapsedMs >= SHOW_ANYWAY_MS) Step.HAND_OFF else Step.WAIT
        page == Page.HUMAN -> Step.REVEAL
        elapsedMs >= REVEAL_AFTER_MS && page != Page.PAGE -> Step.REVEAL
        // An ordinary page normally means the check passed and the extension is about to close its window;
        // one that is still open after [SHOW_ANYWAY_MS] may want the user (a sign-in), so it is shown.
        elapsedMs >= SHOW_ANYWAY_MS -> Step.REVEAL
        else -> Step.WAIT
    }

    /**
     * JS run in the extension's own WebView (read only): a firewall block, an interactive widget a
     * person has to tap, a running check, or ordinary content.
     */
    const val KIND_JS =
        "(function(){try{var t=(document.title||'').toLowerCase();var b=((document.body&&document.body.innerText)||'').toLowerCase().slice(0,6000);" +
            "if(document.getElementById('cf-error-details')||b.indexOf('sorry, you have been blocked')>=0||b.indexOf('you are unable to access')>=0||" +
            "/error code:? ?10(09|10|15|20)/.test(b)||(t.indexOf('access denied')>=0&&b.indexOf('cloudflare')>=0))return 'block';" +
            "if(document.querySelector('iframe[src*=\"challenges.cloudflare.com\"],.cf-turnstile,#turnstile-wrapper,#cf-chl-widget,iframe[src*=\"hcaptcha.com\"],.h-captcha,.g-recaptcha,iframe[src*=\"/recaptcha/\"]'))return 'human';" +
            "if(t.indexOf('just a moment')>=0||t.indexOf('attention required')>=0||t.indexOf('ddos-guard')>=0||document.getElementById('challenge-form')||" +
            "window._cf_chl_opt||b.indexOf('checking your browser')>=0||b.indexOf('verify you are human')>=0)return 'challenge';" +
            "if(b.replace(/\\s/g,'').length<1)return 'blank';return 'page';}catch(e){return 'blank';}})()"

    /** True when [ExtensionWindowWatch] should hand this window here. */
    fun handles(s: ExtensionPopups.Snapshot): Boolean =
        dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.VERIFY_CENTER) && looksLikeCheck(s)

    /**
     * Main thread. [conceal] hides the window in place (returns false when it could not); [reveal] shows
     * it framed; [hold] puts it behind the quiet card (player on screen / user typing).
     */
    internal fun take(
        root: View, lp: WindowManager.LayoutParams, extKey: String, extName: String,
        conceal: (View, WindowManager.LayoutParams) -> Boolean, reveal: (View) -> Unit, holdReason: (() -> Unit)?,
    ) {
        if (!conceal(root, lp)) { Log.w(TAG, "could not hide $extName's check window; it stays as the extension opened it"); return }
        val host0 = webHost(root) ?: lastHost[extKey]
        host0?.let { h ->
            if (VerificationCenter.isBlocked(h)) { close(root, tr(R.string.blocks_this_network, "%1\$s blocks this network", h)); return }
            if (!VerificationCenter.mayAsk(h)) { close(root, tr(R.string.is_paused_after_the_user_closed, "%1\$s is paused after the user closed its check", h)); return }
            shown[h]?.takeIf { it !== root && it.isAttachedToWindow }?.let { close(root, "a check for $h is already open"); return }
        }
        Log.i(TAG, "$extName opened a check window (site ${host0 ?: "not loaded yet"}); reading its page before showing it")
        val start = SystemClock.uptimeMillis()
        val cookieBefore = arrayOfNulls<String>(1)
        host0?.let { h -> io.execute { cookieBefore[0] = clearance(h) } }
        var revealed = false
        var held = false
        var lastRead = 0L
        var reading = false
        var closing = false
        lateinit var tick: Runnable
        fun finish(host: String?) {
            main.removeCallbacks(tick)
            host?.let { h -> if (shown[h] === root) shown.remove(h) }
            if (host == null) return
            // Passed = the site's pass cookie is there and is not the one that was there before.
            io.execute {
                val now = clearance(host)
                val passed = now != null && now != cookieBefore[0]
                val outcome = when {
                    passed -> VerificationCenter.Outcome.PASSED
                    revealed -> VerificationCenter.Outcome.CLOSED_BY_USER
                    else -> null // closed by the extension before anyone saw it: nothing to learn
                }
                outcome?.let { VerificationCenter.report(host, it, extName) }
                Log.i(TAG, "$extName's check window for $host closed: ${outcome ?: "by the extension, unseen"}")
            }
        }
        tick = Runnable {
            val host = webHost(root) ?: host0
            host?.let { lastHost[extKey] = it }
            if (!root.isAttachedToWindow) { finish(host); return@Runnable }
            val elapsed = SystemClock.uptimeMillis() - start
            if (host != null && !revealed && (VerificationCenter.isBlocked(host) || !VerificationCenter.mayAsk(host))) {
                if (!closing) { closing = true; close(root, tr(R.string.is_on_hold, "%1\$s is on hold", host)) }
                main.postDelayed(tick, POLL_MS); return@Runnable
            }
            val web = firstWeb(root)
            if (web != null && !reading && SystemClock.uptimeMillis() - lastRead >= READ_EVERY_MS) {
                reading = true; lastRead = SystemClock.uptimeMillis()
                runCatching {
                    web.evaluateJavascript(KIND_JS) { r ->
                        reading = false
                        if (!root.isAttachedToWindow) return@evaluateJavascript
                        val p = page(r)
                        val h = webHost(root) ?: host
                        when (step(p, SystemClock.uptimeMillis() - start, h == null || VerificationCenter.mayAsk(h), quiet())) {
                            Step.HAND_OFF -> if (!closing) {
                                closing = true
                                val url = runCatching { web.url }.getOrNull()?.takeIf { it.startsWith("http") } ?: h?.let { "https://$it/" }
                                if (h != null) VerificationCenter.report(h, VerificationCenter.Outcome.NEEDS_PERSON, extName)
                                close(root, "handed to Yumeiro's own check (${p.name.lowercase()})")
                                if (h != null && url != null) handOff(root.context.applicationContext, extName, h, url)
                            }
                            Step.CLOSE_BLOCKED -> {
                                if (h != null) {
                                    val before = VerificationCenter.site(h)
                                    val wasHolding = before != null && before.state == VerificationCenter.State.BLOCKED && VerificationPolicy.holding(before, System.currentTimeMillis())
                                    VerificationCenter.report(h, VerificationCenter.Outcome.BLOCKED, extName)
                                    if (!wasHolding) noteBlocked(root, extName, h)
                                }
                                if (!closing) { closing = true; close(root, "the page is a firewall block") }
                            }
                            Step.REVEAL -> if (!revealed && !held) {
                                if (h != null && shown[h]?.let { it !== root && it.isAttachedToWindow } == true) { close(root, "a check for $h is already open"); return@evaluateJavascript }
                                h?.let { VerificationCenter.report(it, VerificationCenter.Outcome.NEEDS_PERSON, extName); shown[it] = root }
                                if (holdReason != null) { held = true; holdReason() } else { revealed = true; reveal(root) }
                                Log.i(TAG, "$extName's check for ${h ?: "?"} needs a person (${p.name.lowercase()}): ${if (held) "waiting behind a card" else "shown"}")
                            }
                            Step.KEEP, Step.WAIT -> Unit
                        }
                        Unit
                    }
                }.onFailure { reading = false }
            }
            // A held window the user opened from the card counts as shown from now on.
            if (held && !revealed && !ExtensionWindowWatch.isConcealed(root)) revealed = true
            if (quiet() && !revealed && !held && web == null && elapsed >= SHOW_ANYWAY_MS && !closing) {
                // VERIFY-3: nothing to read and still open: closed rather than put over the screen.
                closing = true; close(root, "no page to judge after ${SHOW_ANYWAY_MS / 1000} s")
            }
            if (!quiet() && !revealed && !held && web == null && elapsed >= REVEAL_AFTER_MS) {
                // No page to read: show it as the extension built it rather than hide something we can't judge.
                revealed = true; reveal(root)
            }
            main.postDelayed(tick, POLL_MS)
        }
        main.postDelayed(tick, POLL_MS)
    }

    private fun quiet() = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.QUIET_EXT_CHECKS)

    /** Sites whose "needs a quick check" note was shown, and when (one note per site per [HAND_OFF_NOTE_MS]). */
    private val handOffNoteAt = ConcurrentHashMap<String, Long>()
    const val HAND_OFF_NOTE_MS = 30 * 60_000L

    /** Whether a hand-off note may be shown for a site now. Pure → unit-tested. */
    fun mayNoteHandOff(lastAt: Long?, now: Long, onScreen: Boolean): Boolean =
        onScreen && (lastAt == null || now - lastAt !in 0 until HAND_OFF_NOTE_MS)

    /**
     * VERIFY-3: one quiet, non-blocking note with a Verify button. Verify opens Yumeiro's own check page for the site
     * (BrowserCheck.openSite); its pass cookie is shared with the extension through the WebView cookie store, so the
     * extension's next request goes through. Also listed in Extensions › Security checks.
     */
    private fun handOff(ctx: android.content.Context, extName: String, host: String, url: String) {
        val now = System.currentTimeMillis()
        if (!mayNoteHandOff(handOffNoteAt[host], now, dev.asfakul.yumeiro.update.AppVisibility.isForeground)) return
        handOffNoteAt[host] = now
        dev.asfakul.yumeiro.ui.components.AppMessages.showQuiet(ctx, handOffText(extName, host), "Verify") {
            dev.asfakul.yumeiro.online.web.BrowserCheck.openSite(url)
        }
    }

    /**
     * B4: Yumeiro's own silent check could not pass [host] (an interactive widget). Same quiet card as an extension's
     * window ([handOff]), at most once per site per [HAND_OFF_NOTE_MS], only while the app is on screen.
     */
    fun askToVerify(ctx: android.content.Context, host: String, url: String) {
        if (!VerificationCenter.mayAsk(host) || VerificationCenter.isBlocked(host)) return
        main.post { handOff(ctx, host, host, url) }
    }

    /** Pure → unit-tested. */
    fun handOffText(extName: String, host: String) = tr(R.string.needs_a_quick_check_on_tap, "%1\$s needs a quick check on %2\$s. Tap Verify when you're ready.", extName, host)

    /** The site's pass cookie (cf_clearance / DDoS-Guard), or null. Worker thread. */
    private fun clearance(host: String): String? = runCatching { CookieManager.getInstance().getCookie("https://$host/") }.getOrNull()
        ?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("cf_clearance=") || it.startsWith("__ddg2_=") || it.startsWith("__ddgid_=") }

    private fun noteBlocked(root: View, extName: String, host: String) {
        dev.asfakul.yumeiro.ui.components.AppMessages.showQuiet(root.context.applicationContext, blockedText(extName, host))
    }

    /** The one note shown when a check window was a firewall block. Pure → unit-tested. */
    fun blockedText(extName: String, host: String) =
        tr(R.string.cant_reach_the_sites_firewall_blocks, "%1\$s can't reach %2\$s: the site's firewall blocks this network, so its check was closed. Try Wi-Fi or mobile data, or turn off a VPN.", extName, host)

    /**
     * Closes the extension's window the way a person would: its own Close / Cancel button, else Back.
     * The extension's dialog then ends normally (its code sees \"cancelled\"). When neither works the
     * window simply stays concealed until the extension closes it.
     */
    private fun close(root: View, why: String) {
        Log.i(TAG, "closing an extension check window: $why")
        val button = findCloseButton(root)
        if (button != null && runCatching { button.performClick() }.getOrDefault(false)) return
        // Android 13+ with predictive back on (the manifest opts in): a Back key event no longer reaches a
        // dialog, so the extension's DialogFragment (AnimePahe's CfWebViewDialog) is dismissed through
        // the activity's fragment manager instead — the same as the user swiping back.
        if (dismissFragment(root)) return
        runCatching {
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
    }

    private fun dismissFragment(root: View): Boolean = runCatching {
        var c: android.content.Context? = root.context
        while (c is android.content.ContextWrapper && c !is androidx.fragment.app.FragmentActivity) c = c.baseContext
        val fm = (c as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return false
        fun find(m: androidx.fragment.app.FragmentManager, depth: Int): androidx.fragment.app.DialogFragment? {
            if (depth > 3) return null
            for (f in m.fragments) {
                if (f is androidx.fragment.app.DialogFragment && f.dialog?.window?.decorView === root) return f
                if (f.isAdded) find(f.childFragmentManager, depth + 1)?.let { return it }
            }
            return null
        }
        val f = find(fm, 0) ?: return false
        f.dialog?.cancel() // the extension's onCancel runs (it sees "cancelled"), then the fragment is removed
        if (f.isAdded) f.dismissAllowingStateLoss()
        true
    }.getOrDefault(false)

    private val CLOSE_WORDS = setOf("cancel", "close", "✕", "×", "x", "dismiss", "not now", "later")

    /** The window's own close button (text or content description), bounded walk. */
    internal fun findCloseButton(root: View): View? {
        var seen = 0
        var found: View? = null
        fun walk(v: View, depth: Int) {
            if (found != null || seen++ > 400 || depth > 30) return
            if (v is WebView) return // never touch the site's page
            val label = ((v as? TextView)?.text?.toString() ?: v.contentDescription?.toString()).orEmpty().trim().lowercase()
            if (label in CLOSE_WORDS && v.isEnabled && (v.isClickable || (v.parent as? View)?.isClickable == true)) {
                found = if (v.isClickable) v else v.parent as View
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    private fun firstWeb(root: View): WebView? {
        var seen = 0
        fun walk(v: View, depth: Int): WebView? {
            if (seen++ > 400 || depth > 30) return null
            if (v is WebView) return v.also(::harden)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)?.let { return it }
            return null
        }
        return walk(root, 0)
    }

    /**
     * The extension's own WebView (a site's check page): content:// and file access are turned off the first time
     * Yumeiro finds it, so a link on that page cannot read the app's or the device's files. The page itself is
     * left alone (no reload, no script change).
     */
    private fun harden(w: WebView) {
        // Once per WebView, and after the current frame: the check window is polled every tick, and touching a
        // WebView's settings while its renderer is still starting waits on Chromium (B20 low-RAM, run 45957252810).
        if (w.getTag(HARDENED_TAG) != null) return
        w.setTag(HARDENED_TAG, true)
        main.post {
            runCatching {
                val s = w.settings
                if (s.allowContentAccess) s.allowContentAccess = false
                if (s.allowFileAccess) s.allowFileAccess = false
            }
        }
    }
    private val HARDENED_TAG = dev.asfakul.yumeiro.R.id.yu_webview_hardened

    private fun webHost(root: View): String? = firstWeb(root)?.let { w -> runCatching { w.url }.getOrNull() }?.let(VerificationPolicy::hostOf)
}
