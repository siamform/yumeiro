/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * ADR-059 (VERIFY-1, owner list 22): one place that knows, per site, where its security check stands.
 *
 * Every path that meets a site's "checking your browser" page reports here: Yumeiro's own check
 * ([BrowserCheck], which CloudStream's CloudflareKiller / DdosGuardKiller and the Aniyomi interceptor
 * use), and the check windows extensions open themselves ([ExtensionCheckWindows], e.g. AnimePahe's
 * "Quick security check"). The state is kept per site across app restarts, so a site the user already
 * passed does not ask again, a site whose firewall refuses this network stops opening windows that
 * can never be passed, and a check the user closed does not come straight back.
 *
 * Nothing is solved on the user's behalf: the site's own page runs in a real WebView, and a person
 * taps a checkbox when the site asks for one. No token is made up and no access rule is defeated.
 */
object VerificationCenter {
    private const val TAG = "Verification"
    private const val PREFS = "verification_center"
    private const val KEY = "sites"

    enum class State {
        /** The site's check was passed; its pass (cookie) is reused until the site asks again. */
        CLEAR,
        /** The site wants a person to tap its check; asked once, not again while one is open. */
        NEEDS_YOU,
        /** The user closed the check: not asked again automatically until [Site.until]. */
        PAUSED,
        /** The site's firewall refuses this network ("Sorry, you have been blocked"): no check can fix it. */
        BLOCKED,
        /** The check did not finish (timed out, page error); retried after a short wait. */
        FAILED,
    }

    /** What happened in one check. */
    enum class Outcome { PASSED, NEEDS_PERSON, CLOSED_BY_USER, BLOCKED, FAILED }

    data class Site(
        val host: String,
        /** Shown to the user: the extension's name when an extension asked, else the host. */
        val label: String,
        val state: State,
        /** When [state] was reached. */
        val at: Long,
        /** PAUSED / BLOCKED / FAILED: automatic checks resume after this time; 0 = no hold. */
        val until: Long = 0L,
        /** Checks closed by the user in a row (grows the pause; reset by a pass). */
        val closes: Int = 0,
        /** Passes recorded for this site (how often the user passed it). */
        val passes: Int = 0,
    )

    private val sites = ConcurrentHashMap<String, Site>()
    private val _list = MutableStateFlow<List<Site>>(emptyList())
    /** Every site with a recorded check, most recent first (the Extensions screen lists them). */
    val list: StateFlow<List<Site>> = _list.asStateFlow()
    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false

    private val _passed = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /**
     * B4 (Group M): every site whose check was just passed (normalized host), whether in Yumeiro's own check page,
     * silently, or in an extension's window. Before, a pass was only recorded: the extension that had failed on the
     * check stayed on "Couldn't load" (feed, search, title page) until the user found Retry, and Retry right after
     * a pass often hit the in-memory failure and quarantine left by the check. Screens listen and reload the
     * extensions of that site ([SitePass.matches]); [dev.asfakul.yumeiro.online.OnlineRepository.onSitePassed] drops
     * what the failed attempt left behind.
     */
    val passed: SharedFlow<String> = _passed.asSharedFlow()

    /** Loads the saved states off the main thread (StrictMode: no disk read in onCreate). */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        Thread({
            val raw = runCatching { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull()
            val now = System.currentTimeMillis()
            VerificationPolicy.decode(raw).forEach { s ->
                // An entry written while the app was running wins over an older saved one.
                if (VerificationPolicy.keep(s, now)) sites.merge(s.host, s) { cur, old -> if (cur.at >= old.at) cur else old }
            }
            loaded = true
            publish()
        }, "yu-verify-load").apply { isDaemon = true }.start()
    }

    fun host(url: String): String? = VerificationPolicy.hostOf(url)

    fun site(host: String): Site? = sites[VerificationPolicy.normalize(host)]

    /** Records what happened in a check for [host]. Thread-safe; saves in the background. */
    fun report(host: String, outcome: Outcome, label: String? = null, now: Long = System.currentTimeMillis()): Site {
        val h = VerificationPolicy.normalize(host)
        var before: Site? = null
        val next = sites.compute(h) { _, cur -> before = cur; VerificationPolicy.next(cur, h, label, outcome, now) }!!
        if (outcome == Outcome.NEEDS_PERSON) BrowserCheck.noteChallenge(h, now)
        // B4: a pass is announced even when the site was already CLEAR (a pass that ran out and was earned again).
        if (outcome == Outcome.PASSED) {
            Log.i(TAG, "$h: passed, reloading its extensions")
            // Off the caller's thread (it may be the main thread; the phone's copy of an empty answer is a file),
            // and the reload is announced only once what the failed attempt left behind is gone.
            Thread({
                runCatching { dev.asfakul.yumeiro.online.OnlineRepository.onSitePassed(h) }.onFailure { Log.w(TAG, "after pass", it) }
                _passed.tryEmit(h)
            }, "yu-verify-pass").apply { isDaemon = true }.start()
        }
        // A blocked site answers every request the same way: only a real change is logged, shown and saved.
        if (!VerificationPolicy.changed(before, next)) return next
        Log.i(TAG, "$h: $outcome -> ${next.state}${if (next.until > 0) " until +" + ((next.until - now) / 1000) + " s" else ""}")
        publish(); save()
        return next
    }

    /** True while [host]'s firewall is known to refuse this network (no check window helps). */
    fun isBlocked(host: String, now: Long = System.currentTimeMillis()): Boolean =
        site(host)?.let { VerificationPolicy.holding(it, now) && it.state == State.BLOCKED } == true

    /** May a check page for [host] be put in front of the user now (not blocked, not paused)? */
    fun mayAsk(host: String, now: Long = System.currentTimeMillis()): Boolean = VerificationPolicy.mayAsk(site(host), now)

    /** The user asked to forget a site's state (Extensions → Security checks → Forget). */
    fun forget(host: String) {
        sites.remove(VerificationPolicy.normalize(host)); BrowserCheck.forget(VerificationPolicy.normalize(host))
        publish(); save()
    }

    /**
     * The user asked to check a site now: its page opens in Yumeiro's browser sheet (never an outside
     * browser), the hold is lifted, and the result is recorded when they finish.
     */
    fun checkNow(host: String) {
        val h = VerificationPolicy.normalize(host)
        sites.computeIfPresent(h) { _, s -> s.copy(until = 0L) }
        publish()
        BrowserCheck.openSite(BrowserCheck.checkPageFor(h))
    }

    private fun publish() {
        _list.value = sites.values.sortedByDescending { it.at }.take(VerificationPolicy.MAX_SITES)
    }

    private fun save() {
        val ctx = appContext ?: return
        if (!loaded) return // the saved copy is still being read; the next report saves both
        val text = VerificationPolicy.encode(sites.values.toList())
        // SharedPreferences.apply writes on its own thread; building the string here is a few hundred bytes.
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply() }
    }

    /** Device tests start from a clean state. */
    fun resetForTest() { sites.clear(); publish() }
}

/** The rules of [VerificationCenter]. Pure → unit-tested (VerificationPolicyTest). */
object VerificationPolicy {
    const val MAX_SITES = 40

    /** A closed check is not reopened automatically for this long: 10 min, 30 min, 2 h, 6 h, then a day. */
    private val PAUSES_MS = longArrayOf(10 * 60_000L, 30 * 60_000L, 2 * 3_600_000L, 6 * 3_600_000L, 24 * 3_600_000L)

    /** A firewall block is not tried again for this long (the network may change: Wi-Fi, mobile data, VPN). */
    const val BLOCK_HOLD_MS = 30 * 60_000L

    /** A check that did not finish is retried after this long (same as [BrowserCheckPolicy.FAILED_RETRY_MS]). */
    const val FAILED_HOLD_MS = 60_000L

    /** Saved entries older than this are dropped on load (a site not seen for a month starts fresh). */
    const val FORGET_AFTER_MS = 30L * 24 * 3_600_000L

    fun pauseFor(closes: Int): Long = PAUSES_MS[(closes - 1).coerceIn(0, PAUSES_MS.size - 1)]

    fun normalize(host: String): String = host.trim().lowercase().removePrefix("www.").trimEnd('.')

    fun hostOf(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return null
        return runCatching { java.net.URI(u).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::normalize)
    }

    /** The state after [outcome]. */
    fun next(cur: VerificationCenter.Site?, host: String, label: String?, outcome: VerificationCenter.Outcome, now: Long): VerificationCenter.Site {
        val name = label?.takeIf { it.isNotBlank() } ?: cur?.label ?: host
        val closes = cur?.closes ?: 0
        val passes = cur?.passes ?: 0
        return when (outcome) {
            VerificationCenter.Outcome.PASSED ->
                VerificationCenter.Site(host, name, VerificationCenter.State.CLEAR, now, 0L, closes = 0, passes = passes + 1)
            VerificationCenter.Outcome.NEEDS_PERSON ->
                // A site already paused or blocked keeps its hold: being asked again is not a reason to ask the user again.
                if (cur != null && holding(cur, now)) cur.copy(label = name)
                else VerificationCenter.Site(host, name, VerificationCenter.State.NEEDS_YOU, now, 0L, closes, passes)
            VerificationCenter.Outcome.CLOSED_BY_USER ->
                VerificationCenter.Site(host, name, VerificationCenter.State.PAUSED, now, now + pauseFor(closes + 1), closes + 1, passes)
            VerificationCenter.Outcome.BLOCKED ->
                VerificationCenter.Site(host, name, VerificationCenter.State.BLOCKED, now, now + BLOCK_HOLD_MS, closes, passes)
            VerificationCenter.Outcome.FAILED ->
                if (cur != null && holding(cur, now) && cur.state != VerificationCenter.State.FAILED) cur.copy(label = name)
                else VerificationCenter.Site(host, name, VerificationCenter.State.FAILED, now, now + FAILED_HOLD_MS, closes, passes)
        }
    }

    /** True while [s] holds automatic checks back (paused, blocked or failed, and not yet expired). */
    fun holding(s: VerificationCenter.Site, now: Long): Boolean =
        s.until > 0L && now < s.until && now >= s.at - 60_000L // a clock moved back by more than a minute releases the hold

    fun mayAsk(s: VerificationCenter.Site?, now: Long): Boolean = s == null || !holding(s, now)

    /** Worth saving: a new state, label or count, or a hold moved by a minute or more. */
    fun changed(before: VerificationCenter.Site?, after: VerificationCenter.Site): Boolean =
        before == null || before.state != after.state || before.label != after.label || before.closes != after.closes ||
            before.passes != after.passes || kotlin.math.abs(before.until - after.until) >= 60_000L

    fun keep(s: VerificationCenter.Site, now: Long): Boolean = now - s.at in 0 until FORGET_AFTER_MS || now < s.at

    // ---- persistence: one site per line, tab-separated (host, label, state, at, until, closes, passes) ----

    fun encode(list: List<VerificationCenter.Site>): String =
        list.sortedByDescending { it.at }.take(MAX_SITES).joinToString("\n") { s ->
            listOf(s.host, s.label.replace('\t', ' ').replace('\n', ' '), s.state.name, s.at, s.until, s.closes, s.passes).joinToString("\t")
        }

    fun decode(raw: String?): List<VerificationCenter.Site> = raw.orEmpty().lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 7) return@mapNotNull null
        val state = VerificationCenter.State.entries.firstOrNull { it.name == p[2] } ?: return@mapNotNull null
        val host = normalize(p[0]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        VerificationCenter.Site(
            host, p[1].ifBlank { host }, state, p[3].toLongOrNull() ?: return@mapNotNull null,
            p[4].toLongOrNull() ?: 0L, p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0,
        )
    }.toList()

    /** One line for the Extensions screen: what the state means and what happens next. */
    fun describe(s: VerificationCenter.Site, now: Long): String {
        val left = (s.until - now).coerceAtLeast(0L)
        val holdText = when {
            left >= 3_600_000L -> "${(left + 1_800_000L) / 3_600_000L} h"
            left >= 60_000L -> "${(left + 30_000L) / 60_000L} min"
            else -> "a moment"
        }
        return when (s.state) {
            VerificationCenter.State.CLEAR -> "Verified. Yumeiro reuses this pass until the site asks again."
            VerificationCenter.State.NEEDS_YOU -> "Waiting for you to tap the site's check."
            VerificationCenter.State.PAUSED ->
                if (holding(s, now)) "You closed the check. It won't open by itself for $holdText." else "You closed the check. It can ask again."
            VerificationCenter.State.BLOCKED ->
                if (holding(s, now)) "The site's firewall blocks this network, so no check is shown. Try Wi-Fi or mobile data, or turn off a VPN."
                else "The site's firewall blocked this network earlier. It will be tried again."
            VerificationCenter.State.FAILED -> "The last check didn't finish. It will be tried again."
        }
    }
}
