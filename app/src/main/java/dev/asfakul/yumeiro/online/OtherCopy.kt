/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * B-32 (R19): an extension whose home comes back empty while its site works (EmptyHome NOTHING_PARSED)
 * is out of date for that site. Another repository often still publishes a copy that matches it:
 * HindiSubAnime ships inside AnimeDekhoProvider, and v72/v73 (phisher98, xr3ed) read only AnimeDekho's
 * new `a.dgt-card` layout while hindisubanime.co still serves the old `article` cards that v71 (Adam
 * Knight Mega Repo) and v61 read. CloudStream and Hikari run whichever copy the user installed, which is
 * why it "works there" for people on an older copy.
 *
 * So instead of guessing new selectors, Yumeiro offers the best other copy of the same extension from
 * the market, within PIN-1-DUP's cap of two copies; PIN-2 lists it as "Name · Repo" next to the first.
 * Pure, unit-tested (OtherCopyTest).
 */
object OtherCopy {
    /**
     * The copy to offer for [installed], or null. Same internalName, another repository, not installed,
     * loadable by this app (apiVersion), not marked down or known-broken, and the PIN-1-DUP cap not
     * reached. A different version comes first (the same version is the same code), then curated copies,
     * then the market's score.
     */
    fun pick(installed: InstalledExtension, all: List<InstalledExtension>, market: List<RemoteExtension>, apiVersion: Int = OnlineRuntime.API_VERSION): RemoteExtension? {
        val norm = installed.name.trim().lowercase()
        if (all.count { it.name.trim().lowercase() == norm } >= ExtensionManager.MAX_SAME_NAME) return null
        val keys = all.mapTo(HashSet()) { it.key }
        val mine = normalizeRepoUrl(installed.repoUrl).lowercase()
        return market.asSequence()
            .filter { it.internalName == installed.internalName && it.key !in keys }
            .filter { normalizeRepoUrl(it.repoUrl).lowercase() != mine }
            .filter { it.apiVersion <= apiVersion && !it.isDown && it.compatNote == null }
            .filter { installed.sha256.isBlank() || it.sha256 == null || !it.sha256.equals(installed.sha256, ignoreCase = true) }
            .sortedWith(
                compareByDescending<RemoteExtension> { it.version != installed.version }
                    .thenByDescending { it.verified }
                    .thenByDescending { it.score }
                    .thenByDescending { it.version },
            )
            .firstOrNull()
    }

    /** The button label: "Try v71 from Adam Knight Mega Repo". */
    fun label(copy: RemoteExtension): String = "Try v${copy.version} from ${copy.repoName.ifBlank { "another repository" }}"
}
