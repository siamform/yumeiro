/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.content.SharedPreferences

/**
 * In-app reminders: follow the developer, star Yumeiro on GitHub, support the project.
 *
 * Rules:
 *  - "Support" (buy me a coffee) and "Star" come back every 2 hours ([OFTEN_MS]); "Follow"
 *    every 4 hours ([EVERY_MS]).
 *  - "Follow" counts taps that really opened Facebook / Instagram / LinkedIn. After 3 such
 *    taps it only comes back once a day. Same for "Star" after the user opened GitHub once.
 *  - Never two at once, and at least [MIN_GAP_MS] between any two reminders, so they are
 *    spread out over the day. None in the first [GRACE_MS] after install.
 *  - The user can switch all of them off (Settings → Notifications → Reminders).
 */
object Reminders {
    enum class Kind { FOLLOW, STAR, SUPPORT }
    enum class Social(val label: String, val url: String) {
        FACEBOOK("Facebook", "https://www.facebook.com/siam1p"),
        INSTAGRAM("Instagram", "https://www.instagram.com/asfakul_siam"),
        LINKEDIN("LinkedIn", "https://www.linkedin.com/in/asfakulislamsiam"),
    }

    const val GITHUB_URL = "https://github.com/siamform/yumeiro"
    const val EVERY_MS = 4L * 60 * 60 * 1000
    /** Coffee and star (until starred) are the ones that keep the project going, so they come back sooner. */
    const val OFTEN_MS = 2L * 60 * 60 * 1000
    const val DAILY_MS = 24L * 60 * 60 * 1000
    const val MIN_GAP_MS = 60L * 60 * 1000
    const val GRACE_MS = 30L * 60 * 1000
    const val FOLLOWS_FOR_DAILY = 3
    /** The follow card counts down this long, then closes by itself. */
    const val FOLLOW_COUNTDOWN_S = 15

    data class State(
        val installedAt: Long,
        val lastAny: Long,
        val last: Map<Kind, Long>,
        val followClicks: Int,
        val starClicks: Int,
    )

    /** Interval for [kind] given what the user already did. */
    fun interval(kind: Kind, s: State): Long = when (kind) {
        Kind.FOLLOW -> if (s.followClicks >= FOLLOWS_FOR_DAILY) DAILY_MS else EVERY_MS
        Kind.STAR -> if (s.starClicks > 0) DAILY_MS else OFTEN_MS
        Kind.SUPPORT -> OFTEN_MS
    }

    /**
     * The reminder to show at [now], or null. Due reminders are taken oldest-first, so the
     * three take turns. A reminder that has never been shown is staggered by its position, so
     * a fresh install doesn't get all three in its first hours.
     */
    fun next(s: State, now: Long, dayOf: (Long) -> Long = { it / DAILY_MS }): Kind? {
        if (now - s.installedAt < GRACE_MS) return null
        val candidates = Kind.entries
            .map { k -> k to (s.last[k] ?: (s.installedAt - EVERY_MS + GRACE_MS + k.ordinal * MIN_GAP_MS)) }
        // First open of the day: one card is always shown (the one that waited longest), even
        // when its normal interval hasn't passed yet. After that the usual timing applies.
        if (s.lastAny <= 0L || dayOf(s.lastAny) != dayOf(now)) return candidates.minByOrNull { it.second }?.first
        if (now - s.lastAny < MIN_GAP_MS) return null
        return candidates
            .filter { (k, last) -> now - last >= interval(k, s) }
            .minByOrNull { it.second }?.first
    }

    /** Calendar day in the phone's time zone (a new day starts at local midnight). */
    fun localDay(t: Long): Long = (t + java.util.TimeZone.getDefault().getOffset(t)) / DAILY_MS

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
        )
    }

    fun due(c: Context, now: Long = System.currentTimeMillis()): Kind? {
        if (!NotifySettings.get(c, NotifySettings.Key.REMINDERS)) return null
        return next(state(c, now), now, ::localDay)
    }

    fun markShown(c: Context, kind: Kind, now: Long = System.currentTimeMillis()) {
        prefs(c).edit().putLong("last_${kind.name}", now).putLong("last_any", now).apply()
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
