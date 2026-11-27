/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.content.SharedPreferences

/**
 * In-app reminders: join the Yumeiro community on Telegram, star Yumeiro on GitHub, support the project.
 *
 * B5 (Beta chapter): the old rules (support and star every 2 hours, one card on the first open of
 * every day, 20 s after launch) meant a donation card on almost every app open. Competitors ask in a
 * Settings page and never push (Zangetsu: About → Support; Hikari even blocks extension donation
 * popups). Yumeiro now asks rarely and politely:
 *  - Never on launch: a card can only appear after [SESSION_WARMUP_MS] of use in this session, and
 *    at most one card per session ([State.shownThisSession]).
 *  - None in the first [GRACE_MS] (3 days) after install.
 *  - At least [MIN_GAP_MS] (3 days) between any two cards, whatever kind.
 *  - Support every [SUPPORT_MS] (14 days); after the user opened the support page, [AFTER_ACTION_MS]
 *    (60 days). Star every [STAR_MS]; once GitHub was opened, never again. Follow every [FOLLOW_MS];
 *    after [FOLLOWS_FOR_DONE] real follows, never again.
 *  - Every "Maybe later" / close is saved ([State.dismissed]) and doubles that kind's wait (up to
 *    [MAX_BACKOFF] times); after [DISMISSES_FOR_QUIET] dismissals the kind only comes back after
 *    [QUIET_MS] (90 days).
 *  - The user can switch all of them off (Settings → Notifications → Reminders).
 */
object Reminders {
    enum class Kind { FOLLOW, STAR, SUPPORT }
    /** The Yumeiro community on Telegram: the news channel and the chat group. */
    enum class Social(val label: String, val detail: String, val url: String) {
        TELEGRAM_CHANNEL("Telegram channel", "News, releases and sneak peeks", "https://t.me/yumeiroofficial"),
        TELEGRAM_GROUP("Telegram group", "Chat with the community, ask for help, share ideas", "https://t.me/chatyumeiroofficial"),
    }

    const val GITHUB_URL = "https://github.com/siamform/yumeiro"
    /** The official Yumeiro website: downloads, news and guides. */
    const val WEBSITE_URL = "https://yumeiro.click"
    const val HOUR_MS = 60L * 60 * 1000
    const val DAILY_MS = 24L * HOUR_MS
    const val GRACE_MS = 3L * DAILY_MS
    const val MIN_GAP_MS = 3L * DAILY_MS
    const val SUPPORT_MS = 14L * DAILY_MS
    const val STAR_MS = 10L * DAILY_MS
    const val FOLLOW_MS = 7L * DAILY_MS
    const val AFTER_ACTION_MS = 60L * DAILY_MS
    const val QUIET_MS = 90L * DAILY_MS
    const val MAX_BACKOFF = 3
    const val DISMISSES_FOR_QUIET = 3
    const val FOLLOWS_FOR_DONE = 3
    /** A card never shows in the first minutes of a session (it is not a launch screen). */
    const val SESSION_WARMUP_MS = 8L * 60 * 1000
    /** The follow card counts down this long, then closes by itself. */
    const val FOLLOW_COUNTDOWN_S = 15

    data class State(
        val installedAt: Long,
        val lastAny: Long,
        val last: Map<Kind, Long>,
        val followClicks: Int,
        val starClicks: Int,
        val dismissed: Map<Kind, Int> = emptyMap(),
        val supportOpened: Int = 0,
        val shownThisSession: Boolean = false,
    )

    /** Wait for [kind] given what the user already did, or null when it must never come back. */
    fun interval(kind: Kind, s: State): Long? {
        val d = s.dismissed[kind] ?: 0
        if (d >= DISMISSES_FOR_QUIET) return QUIET_MS
        val base = when (kind) {
            Kind.FOLLOW -> if (s.followClicks >= FOLLOWS_FOR_DONE) return null else FOLLOW_MS
            Kind.STAR -> if (s.starClicks > 0) return null else STAR_MS
            Kind.SUPPORT -> if (s.supportOpened > 0) AFTER_ACTION_MS else SUPPORT_MS
        }
        return base shl minOf(d, MAX_BACKOFF)
    }

    /**
     * The reminder to show at [now], or null. [sessionMs] is how long the app has been in use in
     * this session. Due reminders are taken oldest-first, so the three take turns; one that has never
     * been shown counts from the end of the grace period, staggered by [MIN_GAP_MS] per kind.
     */
    fun next(s: State, now: Long, sessionMs: Long): Kind? {
        if (s.shownThisSession || sessionMs < SESSION_WARMUP_MS) return null
        if (now - s.installedAt < GRACE_MS) return null
        if (s.lastAny > 0L && now - s.lastAny < MIN_GAP_MS) return null
        return Kind.entries
            .mapNotNull { k ->
                val wait = interval(k, s) ?: return@mapNotNull null
                val last = s.last[k]
                val dueAt = if (last != null) last + wait else s.installedAt + GRACE_MS + k.ordinal * MIN_GAP_MS
                if (now >= dueAt) k to (last ?: Long.MIN_VALUE + k.ordinal) else null
            }
            .minByOrNull { it.second }?.first
    }

    // region storage

    private const val PREFS = "reminders"
    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(c: Context, now: Long = System.currentTimeMillis()): State {
        val p = prefs(c)
        if (!p.contains("installed_at")) p.edit().putLong("installed_at", now).apply()
        return State(
            installedAt = p.getLong("installed_at", now),
            lastAny = p.getLong("last_any", 0L),
            last = Kind.entries.mapNotNull { k -> p.getLong("last_${k.name}", -1L).takeIf { it >= 0 }?.let { k to it } }.toMap(),
            followClicks = p.getInt("follow_clicks", 0),
            starClicks = p.getInt("star_clicks", 0),
            dismissed = Kind.entries.mapNotNull { k -> p.getInt("dismiss_${k.name}", 0).takeIf { it > 0 }?.let { k to it } }.toMap(),
            supportOpened = p.getInt("support_opened", 0),
            shownThisSession = sessionShown,
        )
    }

    /** One card per process session at most (reset when the process starts). */
    @Volatile private var sessionShown = false
    /** [sessionMs]: how long the app has been on screen in this visit (ReminderHost feeds [Visit]). */
    fun due(
        c: Context,
        now: Long = System.currentTimeMillis(),
        sessionMs: Long = Visit.onScreen(android.os.SystemClock.elapsedRealtime()),
    ): Kind? {
        if (!NotifySettings.get(c, NotifySettings.Key.REMINDERS)) return null
        return next(state(c, now), now, sessionMs)
    }

    fun markShown(c: Context, kind: Kind, now: Long = System.currentTimeMillis()) {
        sessionShown = true
        prefs(c).edit().putLong("last_${kind.name}", now).putLong("last_any", now).apply()
    }

    /** "Maybe later" or closing the card: saved, and that kind waits longer next time. */
    fun recordDismiss(c: Context, kind: Kind) {
        val p = prefs(c)
        p.edit().putInt("dismiss_${kind.name}", p.getInt("dismiss_${kind.name}", 0) + 1).apply()
    }

    /** The support page was opened from the card. */
    fun recordSupportOpened(c: Context) {
        val p = prefs(c)
        p.edit().putInt("support_opened", p.getInt("support_opened", 0) + 1).apply()
    }

    /** Tests only: a new session. */
    internal fun resetSessionForTest() { sessionShown = false; Visit.reset() }

    /**
     * B5: one "visit" is the time the app is on screen; leaving it for [NEW_VISIT_AFTER_MS] or more
     * starts a new visit (so a card can never greet the user when they come back to the app).
     * Fed by ReminderHost from the activity lifecycle; pure clock math, easy to test.
     */
    object Visit {
        const val NEW_VISIT_AFTER_MS = 30L * 60 * 1000
        private var onScreenMs = 0L
        private var resumedAt = -1L
        private var leftAt = -1L

        @Synchronized fun resumed(nowElapsed: Long) {
            if (leftAt >= 0 && nowElapsed - leftAt >= NEW_VISIT_AFTER_MS) { onScreenMs = 0L; sessionShown = false }
            if (resumedAt < 0) resumedAt = nowElapsed
        }
        @Synchronized fun paused(nowElapsed: Long) {
            if (resumedAt >= 0) onScreenMs += nowElapsed - resumedAt
            resumedAt = -1L; leftAt = nowElapsed
        }
        @Synchronized fun onScreen(nowElapsed: Long): Long = onScreenMs + (if (resumedAt >= 0) nowElapsed - resumedAt else 0L)
        @Synchronized internal fun reset() { onScreenMs = 0L; resumedAt = -1L; leftAt = -1L }
    }

    /** A tap that really opened the social app/site counts as a follow. */
    fun recordFollow(c: Context, social: Social) {
        val p = prefs(c)
        p.edit().putInt("follow_clicks", p.getInt("follow_clicks", 0) + 1)
            .putInt("follow_${social.name}", p.getInt("follow_${social.name}", 0) + 1).apply()
    }

    fun followCount(c: Context, social: Social): Int = prefs(c).getInt("follow_${social.name}", 0)

    fun recordStar(c: Context) { val p = prefs(c); p.edit().putInt("star_clicks", p.getInt("star_clicks", 0) + 1).apply() }

    // endregion
}
