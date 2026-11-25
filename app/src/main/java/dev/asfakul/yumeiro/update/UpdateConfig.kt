/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.BuildConfig

/** Build-time configuration for the GitHub Releases updater. */
internal object UpdateConfig {
    val owner: String = BuildConfig.UPDATE_REPO_OWNER
    val repo: String = BuildConfig.UPDATE_REPO_NAME

    /** Group I: "stable" (store package, `/releases/latest`) or "beta" (`.beta` package, Beta pre-releases). */
    val channel: String = BuildConfig.UPDATE_CHANNEL
    val isBeta: Boolean get() = channel == "beta"

    /** Tag prefix of Beta releases (`beta-v2.3.0-beta.7`). Stable tags are `v2.3.0`, so the two never mix. */
    const val BETA_TAG_PREFIX = "beta-v"

    /**
     * Stable: GitHub's latest release, which never is a pre-release. Beta: the release list, newest first; the client
     * takes the newest published `beta-v…` pre-release from it ([GitHubReleaseClient.pickBetaRelease]).
     */
    val latestReleaseApi: String
        get() = if (isBeta) "https://api.github.com/repos/$owner/$repo/releases?per_page=30"
        else "https://api.github.com/repos/$owner/$repo/releases/latest"

    val releasesPage: String
        get() = "https://github.com/$owner/$repo/releases"

    /**
     * Read-only GitHub token injected at build time (secret `MT_UPDATER_TOKEN`).
     * Empty when not configured, in which case only the public endpoint is used.
     */
    val token: String by lazy {
        val key = BuildConfig.UPDATER_TOKEN_KEY
        val bytes = BuildConfig.UPDATER_TOKEN_BYTES
        if (bytes.isEmpty()) "" else String(ByteArray(bytes.size) { i -> (bytes[i] xor key).toByte() }, Charsets.UTF_8)
    }

    val hasToken: Boolean get() = token.isNotBlank()

    /** Current installed version name without build-type suffixes such as `-debug`. */
    val currentVersionName: String
        // Group I: a Beta keeps its "-beta.N" part (it tells two Betas of one version apart); build-type suffixes go.
        get() = if (isBeta) BuildConfig.VERSION_NAME.removeSuffix("-debug").removeSuffix("-preview") else BuildConfig.VERSION_NAME.substringBefore('-')

    val currentVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()
}
