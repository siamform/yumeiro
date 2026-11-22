/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/** OWN-1 / OWN-6: how a tapped title opens. Pure, unit-tested; flag FeatureFlags.Flag.FAST_TITLE_OPEN. */
object OpenFlow {
    /** The loading layer stays this long over the title page while the page slides in (push is 420 ms). */
    const val HANDOFF_MS = 450L

    /** Go to the title page at once (without waiting for the extension) when the source is known to open one. */
    fun pageFirst(flagOn: Boolean, opensPage: Boolean?): Boolean = flagOn && opensPage == true

    /** A title that plays straight away (a film or a live stream), not a page. */
    fun direct(d: OnlineDetail): Boolean = d.episodes.values.all { it.isEmpty() } && d.playData != null && d.unsupportedReason == null
}
