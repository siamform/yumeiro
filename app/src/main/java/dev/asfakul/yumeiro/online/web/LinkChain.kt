/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/**
 * ADCHAIN-1 (owner list 23, ADR-060): walks a link-shortener chain (AdLinkFly style: srnky/shrinkearn,
 * shrinkme …) to the real link inside Yumeiro's browser.
 *
 * What it does, and what it never does:
 *  - `assets/js/link_chain.js` reads the page: is it a chain step, what does it wait for (a person,
 *    the site's own timer, or a button), and where is the site's own next button. On chain pages
 *    only, it defuses full-screen ad layers (kept in the page, but unseen and untappable).
 *  - The site's timer always runs out by itself. Yumeiro never changes timers, never fills a captcha
 *    and never makes a token: a captcha step asks the person to tick the box.
 *  - The last "Get link" buttons only accept a real (trusted) tap, so Yumeiro taps the site's own
 *    button with a real touch on the page, in view of the user, with a banner that says what happens
 *    and a Stop button.
 */
object LinkChain {
    @Volatile private var script: String? = null

    /** The page script. Reads the asset once; call off the main thread. */
    fun script(context: Context): String = script ?: context.applicationContext.assets.open("js/link_chain.js")
        .bufferedReader().use { it.readText() }.also { script = it }

    enum class Step { CAPTCHA, TIMER, SCROLL, COVERED, READY, NONE }

    /** One reading of the page by link_chain.js. Positions are CSS pixels of a [vw]×[vh] viewport. */
    data class Scan(
        val chain: Boolean,
        val step: Step = Step.NONE,
        val label: String? = null,
        val final: Boolean = false,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val vw: Double = 0.0,
        val vh: Double = 0.0,
        val secs: Int? = null,
        val stepText: String? = null,
        val defused: Int = 0,
        /** ADCHAIN-2: the destination the site itself answered (its /links/go JSON or the filled Get Link href). */
        val dest: String? = null,
    )

    /** Parses what `evaluateJavascript` returned (a JSON string holding the scan's JSON). */
    fun parse(result: String?): Scan? = runCatching {
        if (result.isNullOrBlank() || result == "null") return null
        val inner = JSONTokener(result).nextValue()
        val o = when (inner) {
            is String -> JSONObject(inner)
            is JSONObject -> inner
            else -> return null
        }
        if (!o.optBoolean("chain", false)) return Scan(chain = false)
        val step = when (o.optString("step")) {
            "captcha" -> Step.CAPTCHA
            "timer" -> Step.TIMER
            "scroll" -> Step.SCROLL
            "covered" -> Step.COVERED
            "ready" -> Step.READY
            else -> Step.NONE
        }
        Scan(
            chain = true,
            step = step,
            label = o.optString("label").takeIf { it.isNotBlank() && o.has("label") },
            final = o.optBoolean("final", false),
            x = o.optDouble("x", 0.0),
            y = o.optDouble("y", 0.0),
            vw = o.optDouble("vw", 0.0),
            vh = o.optDouble("vh", 0.0),
            secs = if (o.has("secs") && !o.isNull("secs")) o.optInt("secs") else null,
            stepText = o.optString("stepText").takeIf { it.isNotBlank() && o.has("stepText") && !o.isNull("stepText") },
            defused = o.optInt("removed", 0),
            dest = o.optString("dest").takeIf { o.has("dest") && (it.startsWith("http://", true) || it.startsWith("https://", true)) },
        )
    }.getOrNull()

    /**
     * A real tap on the page at CSS point ([cssX], [cssY]) of a [vw]-wide viewport: a touch DOWN and UP
     * on the web view, which the page sees as the person's own (trusted) tap. Main thread.
     */
    fun tap(web: WebView, cssX: Double, cssY: Double, vw: Double, vh: Double): Boolean {
        if (vw <= 0 || vh <= 0 || web.width <= 0 || web.height <= 0) return false
        val px = (cssX * web.width / vw).toFloat()
        val py = (cssY * web.height / vh).toFloat()
        if (px < 0 || py < 0 || px > web.width || py > web.height) return false
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, px, py, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        return try {
            web.dispatchTouchEvent(down)
            web.dispatchTouchEvent(up)
            true
        } finally {
            down.recycle(); up.recycle()
        }
    }
}

/** ADCHAIN-1: what to do after each scan. Pure (no Android), so it is unit tested. */
object LinkChainPolicy {
    /** Wait this long on a button before tapping it (pages enable buttons, then move them in). */
    const val SETTLE_MS = 1_000L
    /** Never tap faster than this. */
    const val TAP_GAP_MS = 1_200L
    /** Taps for one whole chain; real chains need 4–8. */
    const val MAX_TAPS = 14
    /** The same button tapped this often without the page changing: the person takes over. */
    const val SAME_BUTTON_TAPS = 3
    /** No change for this long: stop and say so. */
    const val STUCK_MS = 180_000L
    /** A page without chain signals this many scans in a row, on a new host, is the destination. */
    const val DEST_SCANS = 2

    data class State(
        val active: Boolean = false,
        val stopped: Boolean = false,
        val chainHosts: Set<String> = emptySet(),
        val pages: List<String> = emptyList(),
        val taps: Int = 0,
        val lastTapAt: Long = 0,
        val readyKey: String? = null,
        val readySince: Long = 0,
        val tappedKey: String? = null,
        val sameKeyTaps: Int = 0,
        val progressKey: String? = null,
        val lastProgressAt: Long = 0,
        val destScans: Int = 0,
        val destination: String? = null,
        val defused: Int = 0,
        /** ADCHAIN-2: the real link as the site revealed it (kept across ad pages that hijack the page). */
        val revealed: String? = null,
        /** ADCHAIN-2: times Yumeiro left an ad page that took over (bounded, so a loop can't trap the page). */
        val recoveries: Int = 0,
    ) {
        /** "Step 2" (or the site's own "2/3"). */
        val stepNumber: Int get() = pages.size.coerceAtLeast(1)
    }

    sealed interface Action {
        /** Not a chain page (or nothing to do): no banner. */
        data object Idle : Action
        data class Wait(val text: String) : Action
        data class AskUser(val text: String) : Action
        data class Tap(val x: Double, val y: Double, val vw: Double, val vh: Double, val label: String) : Action
        /** [url]: the destination's address when Yumeiro knows it (revealed by the site or the page now open). */
        data class Done(val host: String, val url: String? = null) : Action
        /** ADCHAIN-2: an ad page took over after the site revealed the real link: open [url] instead. */
        data class Open(val url: String, val text: String) : Action
        /** ADCHAIN-2: an ad page took over before the real link was known: go back one page. */
        data class Back(val text: String) : Action
        data class GiveUp(val text: String) : Action
    }

    /** Ad pages that took over the chain Yumeiro leaves at most this often (then the person decides). */
    const val MAX_RECOVERIES = 3

    /**
     * [adPage] = the page now open is on an ad host ([AdFilter]'s list): with the filter on that is an ad
     * that took over, never the destination.
     */
    fun next(st: State, scan: LinkChain.Scan?, url: String, host: String, now: Long, adPage: Boolean = false): Pair<State, Action> {
        if (st.stopped || st.destination != null) return st to Action.Idle
        if (scan == null) return st to (if (st.active) Action.Wait(tr(R.string.reading_the_page, "Reading the page")) else Action.Idle)
        if (!scan.chain) {
            if (!st.active) return st to Action.Idle
            // Still on a chain host (the next step is loading) or an ad page in between.
            if (host in st.chainHosts) return st.copy(destScans = 0) to Action.Wait(tr(R.string.opening_the_next_step, "Opening the next step"))
            val revealed = st.revealed
            if (adPage) {
                if (st.recoveries >= MAX_RECOVERIES) return st.copy(stopped = true) to Action.GiveUp(tr(R.string.an_ad_page_keeps_taking_over, "An ad page keeps taking over. Use Back, or turn the filter off for this page."))
                val s = st.copy(recoveries = st.recoveries + 1, destScans = 0)
                return if (revealed != null) s to Action.Open(revealed, tr(R.string.an_ad_took_over_opening_the, "An ad took over; opening the real link"))
                else s to Action.Back(tr(R.string.an_ad_took_over_going_back, "An ad took over; going back"))
            }
            val n = st.destScans + 1
            val destUrl = if (revealed != null && ChainDestination.sameSite(revealed, url)) revealed else url
            return if (n >= DEST_SCANS) st.copy(destScans = n, destination = host) to Action.Done(host, destUrl)
            else st.copy(destScans = n) to Action.Wait(tr(R.string.opening_the_link, "Opening the link"))
        }
        val page = url.substringBefore('#').substringBefore('?')
        var s = st.copy(
            active = true,
            chainHosts = st.chainHosts + host,
            pages = if (st.pages.lastOrNull() == page) st.pages else st.pages + page,
            destScans = 0,
            defused = maxOf(st.defused, scan.defused),
            revealed = scan.dest ?: st.revealed,
        )
        val pKey = "$page|${scan.step}|${scan.label}|${scan.secs}"
        // A captcha waits for the person, however long they take; only a page that stops moving is "stuck".
        if (pKey != s.progressKey || s.lastProgressAt == 0L || scan.step == LinkChain.Step.CAPTCHA) s = s.copy(progressKey = pKey, lastProgressAt = now)
        if (now - s.lastProgressAt > STUCK_MS) {
            return s.copy(stopped = true) to Action.GiveUp(tr(R.string.this_link_stopped_moving_you_can, "This link stopped moving. You can go on by hand on the page."))
        }
        return when (scan.step) {
            LinkChain.Step.CAPTCHA -> s.copy(readyKey = null) to Action.AskUser(tr(R.string.tick_the_box_on_the_page, "Tick the box on the page to continue"))
            LinkChain.Step.TIMER -> s.copy(readyKey = null) to Action.Wait(
                scan.secs?.takeIf { it > 0 }?.let { tr(R.string.waiting_s_the_sites_timer, "Waiting %1\$s s (the site's timer)", it) } ?: tr(R.string.waiting_for_the_sites_timer, "Waiting for the site's timer"),
            )
            LinkChain.Step.SCROLL, LinkChain.Step.COVERED, LinkChain.Step.NONE -> s.copy(readyKey = null) to Action.Wait(tr(R.string.reading_the_page, "Reading the page"))
            LinkChain.Step.READY -> ready(s, scan, page, now)
        }
    }

    private fun ready(s: State, scan: LinkChain.Scan, page: String, now: Long): Pair<State, Action> {
        val label = scan.label ?: "Continue"
        val key = "$page|$label"
        if (s.readyKey != key) return s.copy(readyKey = key, readySince = now) to Action.Wait(tr(R.string.opening_the_real_link, "Opening the real link"))
        if (s.taps >= MAX_TAPS || (s.tappedKey == key && s.sameKeyTaps >= SAME_BUTTON_TAPS)) {
            return s to Action.AskUser(tr(R.string.tap_on_the_page_to_continue, "Tap “%1\$s” on the page to continue", label))
        }
        if (now - s.readySince < SETTLE_MS || now - s.lastTapAt < TAP_GAP_MS) return s to Action.Wait(tr(R.string.opening_the_real_link, "Opening the real link"))
        val same = if (s.tappedKey == key) s.sameKeyTaps + 1 else 1
        return s.copy(taps = s.taps + 1, lastTapAt = now, readyKey = null, tappedKey = key, sameKeyTaps = same) to
            Action.Tap(scan.x, scan.y, scan.vw, scan.vh, label)
    }

    /** The banner's first line: "Opening the real link · Step 2". */
    fun title(st: State, stepText: String?): String =
        tr(R.string.opening_the_real_link_step, "Opening the real link · Step ") + (stepText ?: st.stepNumber.toString())
}
