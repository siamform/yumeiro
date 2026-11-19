/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * ADR-014: what happens when extension code opens a window (a dialog) in Yumeiro.
 *
 * Extensions are converted, never blocked: a window that does a job (a login, a captcha, a
 * settings form, a choice) is always shown, framed so the user can see it came from the extension.
 * A promotional window (donate, join our Telegram, rate us) does not interrupt: in Quiet mode
 * (the default) it is held and announced by one quiet, non-modal card, at most once per extension
 * per [PERIOD_MS], never while the player is on screen. The user can still open it from the card
 * or from Extensions → Pop-ups from extensions. Show passes everything through; Block promotional
 * drops promotional windows.
 *
 * The decision logic ([classify], [rateOk], [decide]) is pure and unit-tested; the window plumbing
 * is in [ExtensionWindowWatch].
 */
object ExtensionPopups {
    private const val TAG = "ExtensionPopups"
    private const val PREFS = "extension_popups"
    const val PERIOD_MS = 7L * 24 * 60 * 60 * 1000

    enum class Mode(val label: String) { SHOW("Show"), QUIET("Quiet"), BLOCK_PROMOTIONAL("Block promotional") }
    enum class Kind { FUNCTIONAL, PROMOTIONAL }
    enum class Action { SHOW_FRAMED, HOLD_AND_CARD, HOLD_DEFERRED, DROP }

    /** What a window shows, read from its view tree before it is added. */
    data class Snapshot(val texts: List<String>, val hasInput: Boolean, val hasWeb: Boolean)

    /** A promotional window concealed in place; [ExtensionWindowWatch.reveal] shows it when the user asks. */
    class Held(val id: Long, val extKey: String, val extName: String, val title: String, internal val view: View)

    private val seq = AtomicLong()
    private val _mode = MutableStateFlow(Mode.QUIET)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    private val _held = MutableStateFlow<List<Held>>(emptyList())
    val held: StateFlow<List<Held>> = _held.asStateFlow()
    @Volatile private var appContext: Context? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var appPlayerOnScreen = false

    /** Set by the app shell: true while a video (full screen, mini player or PiP) is playing. */
    var playerOnScreen: Boolean
        get() = testPlayerOnScreen ?: appPlayerOnScreen
        set(value) { val was = playerOnScreen; appPlayerOnScreen = value; afterPlayerChange(was) }

    /** Device tests stand in for a playing video without the app shell overwriting it. */
    @Volatile var testPlayerOnScreen: Boolean? = null
        set(value) { val was = playerOnScreen; field = value; afterPlayerChange(was) }

    private fun afterPlayerChange(was: Boolean) { if (was && !playerOnScreen) main.post { flushDeferred() } }

    /** Device tests mark their fake extension classes as extension code (no dex is loaded for them). */
    @Volatile var testExtensionPrefix: String? = null

    /** Cards waiting for the player to close, per extension key. */
    private val deferred = LinkedHashMap<String, Held>()

    /** Reads the saved mode off the main thread (StrictMode: no disk read in onCreate); Quiet until then. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        Thread({
            val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
            Mode.entries.firstOrNull { it.name == saved }?.let { _mode.compareAndSet(Mode.QUIET, it) }
        }, "ext-popups-prefs").apply { isDaemon = true }.start()
    }

    fun setMode(context: Context, mode: Mode) {
        _mode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }

    // ---- pure decision logic ---------------------------------------------------------------

    private val FUNCTIONAL_WORDS = Regex(
        "\\b(log ?in|sign ?in|password|username|e-?mail|captcha|verify|verification|cookies?|token|" +
            "choose|select|settings?|confirm)\\b",
    )
    private val PROMO_WORDS = Regex(
        "(donat|ko-?fi|patreon|buy ?me ?a ?coffee|paypal|sponsor|tip jar|bitcoin|\\bbtc\\b|\\busdt\\b|\\bupi\\b|" +
            "support (the |our |my |this )?(dev|developer|developers|project|repo|repository|work|us|me|creator)|" +
            "join (our |us on |the )?(telegram|discord|channel|group|community)|telegram|discord|" +
            "rate (us|this|the app)|star (us|the repo|it|this repo) on github|watch an? ad|remove ads|go premium)",
    )

    /**
     * Functional unless the window is plainly promotional: anything with an input field or a web
     * page, or that mentions a login/choice/setting, does a job and is always shown (nothing lost).
     */
    fun classify(s: Snapshot): Kind {
        if (s.hasInput || s.hasWeb) return Kind.FUNCTIONAL
        val text = s.texts.joinToString(" \n ").lowercase()
        if (text.isBlank()) return Kind.FUNCTIONAL
        if (FUNCTIONAL_WORDS.containsMatchIn(text)) return Kind.FUNCTIONAL
        return if (PROMO_WORDS.containsMatchIn(text)) Kind.PROMOTIONAL else Kind.FUNCTIONAL
    }

    /** True when [extKey] may show a card again ([last] = when its previous card showed, 0 = never). */
    fun rateOk(last: Long, now: Long, period: Long = PERIOD_MS): Boolean = last <= 0L || now - last >= period || now < last

    fun decide(kind: Kind, mode: Mode, playerOnScreen: Boolean, rateOk: Boolean): Action = when {
        kind == Kind.FUNCTIONAL || mode == Mode.SHOW -> Action.SHOW_FRAMED
        mode == Mode.BLOCK_PROMOTIONAL -> Action.DROP
        !rateOk -> Action.DROP
        playerOnScreen -> Action.HOLD_DEFERRED
        else -> Action.HOLD_AND_CARD
    }

    /** Reads the texts, input fields and web views of a window's view tree (bounded). */
    fun snapshot(root: View): Snapshot {
        val texts = ArrayList<String>()
        var input = false
        var web = false
        var seen = 0
        fun walk(v: View, depth: Int) {
            if (seen++ > 400 || depth > 30) return
            when (v) {
                is EditText -> input = true
                is WebView -> web = true
                is TextView -> v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it.take(300) }
            }
            v.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it.take(120) }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        return Snapshot(texts, input, web)
    }

    // ---- state used by the window manager --------------------------------------------------

    internal fun lastCard(extKey: String): Long =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getLong("card_$extKey", 0L) ?: 0L

    private fun markCard(extKey: String, now: Long) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putLong("card_$extKey", now)?.apply()
    }

    /** Called on the main thread by [ExtensionWindowWatch] for a promotional window it concealed. */
    internal fun hold(extKey: String, extName: String, snap: Snapshot, view: View, deferredCard: Boolean): Held {
        val title = snap.texts.firstOrNull().orEmpty().take(80)
        val h = Held(seq.incrementAndGet(), extKey, extName, title, view)
        _held.value = (_held.value.filterNot { it.extKey == extKey } + h).takeLast(10)
        markCard(extKey, System.currentTimeMillis())
        if (deferredCard) deferred[extKey] = h else card(h)
        Log.i(TAG, "held a promotional window from $extName (${if (deferredCard) "card after playback" else "card shown"})")
        return h
    }

    private fun flushDeferred() {
        val list = deferred.values.toList(); deferred.clear()
        list.filter { h -> _held.value.any { it.id == h.id } }.lastOrNull()?.let { card(it) }
    }

    private fun card(h: Held) {
        dev.asfakul.yumeiro.ui.components.AppMessages.show(
            appContext, cardText(h), actionLabel = "Show", action = { reveal(h.id) },
            // G10 ER-29: the card waits for a message on screen instead of replacing it.
            quiet = true,
        )
    }

    /** The extension closed (dismissed) a window we held: forget it. */
    internal fun forget(view: View) {
        if (_held.value.any { it.view === view }) _held.value = _held.value.filterNot { it.view === view }
        deferred.entries.removeAll { it.value.view === view }
    }

    /** Shows a held window now, as the extension built it (the user asked for it). */
    fun reveal(id: Long) {
        val h = _held.value.firstOrNull { it.id == id } ?: return
        _held.value = _held.value.filterNot { it.id == id }
        dropCard(h)
        ExtensionWindowWatch.reveal(h.view, h.extName)
    }

    fun discard(id: Long) {
        val h = _held.value.firstOrNull { it.id == id } ?: return
        _held.value = _held.value.filterNot { it.id == id }
        dropCard(h)
    }

    /**
     * The Extensions screen is listing every held message with its own Show / Dismiss. The quiet
     * card would be a duplicate, and it sits over the bottom of that list: run 43823291873 (both
     * phones) tapped Dismiss and hit the card's Show above it, revealing the donation dialog.
     * Called by the list while it is composed; a card that arrives while it is shown goes too.
     */
    fun onListShown() { _held.value.forEach { dropCard(it) } }

    /** Opened or discarded from the Extensions screen: its card must not stay on screen. */
    private fun dropCard(h: Held) {
        deferred.entries.removeAll { it.value.id == h.id }
        dev.asfakul.yumeiro.ui.components.AppMessages.dropWaiting(cardText(h))
        val m = dev.asfakul.yumeiro.ui.components.AppMessages.current.value ?: return
        if (m.text == cardText(h)) dev.asfakul.yumeiro.ui.components.AppMessages.dismiss(m.id)
    }

    private fun cardText(h: Held) = "${h.extName} has a message"

    /** The activity is going away: held windows belong to it and must not outlive it. */
    fun clear() {
        _held.value = emptyList(); deferred.clear()
        ExtensionWindowWatch.clear()
    }
}
