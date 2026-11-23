/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B-32 (R19): the other copy offered when an extension's home parses nothing. Copies as in the market index of 2026-11-23. */
class OtherCopyTest {
    private val phisher = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json"
    private val xr3ed = "https://raw.githubusercontent.com/xr3ed/cloudstream-extensions-phisher/builds/repo.json"
    private val adam = "https://raw.githubusercontent.com/adamknight/mega-repo/main/repo.json"
    private val joe = "https://raw.githubusercontent.com/JoeTinnySpace/repo/main/repo.json"

    private fun remote(repo: String, name: String, version: Int, sha: String, verified: Boolean = true, score: Double = 1.0, status: Int = 1, compat: String? = null, api: Int = 1) =
        RemoteExtension("AnimeDekhoProvider", "AnimeDekho", "https://m/$sha.cs3", version, apiVersion = api, status = status, sha256 = sha,
            repoUrl = repo, repoName = name, verified = verified, score = score, compatNote = compat)

    private fun installed(repo: String, name: String, version: Int, sha: String) =
        InstalledExtension("AnimeDekhoProvider", "AnimeDekho", version, repo, name, "a.cs3", "https://m/a.cs3", sha)

    private val market = listOf(
        remote(phisher, "phisher98", 73, "s73"),
        remote(xr3ed, "xr3ed", 73, "s73"),
        remote(adam, "Adam Knight Mega Repo", 71, "s71", score = 0.5),
        remote(joe, "JoeTinnySpace", 61, "s61", score = 0.4),
    )

    @Test fun offersAnOlderCopyFromAnotherRepoNotTheSameCodeAgain() {
        val mine = installed(phisher, "phisher98", 73, "s73")
        val pick = OtherCopy.pick(mine, listOf(mine), market)
        assertEquals("Adam Knight Mega Repo", pick?.repoName)
        assertEquals("Try v71 from Adam Knight Mega Repo", OtherCopy.label(pick!!))
    }

    @Test fun nothingWhenTwoCopiesAreAlreadyInstalled() {
        val a = installed(phisher, "phisher98", 73, "s73")
        val b = installed(adam, "Adam Knight Mega Repo", 71, "s71")
        assertNull(OtherCopy.pick(a, listOf(a, b), market))
    }

    @Test fun skipsDownBrokenAndTooNewCopies() {
        val mine = installed(phisher, "phisher98", 73, "s73")
        val m = listOf(
            remote(adam, "Adam", 71, "s71", status = 0),
            remote(joe, "Joe", 61, "s61", compat = "crashes on load"),
            remote("https://r/x.json", "X", 60, "s60", api = 9),
        )
        assertNull(OtherCopy.pick(mine, listOf(mine), m))
    }

    @Test fun sameFileFromAMirrorRepoIsNotOffered() {
        val mine = installed(phisher, "phisher98", 73, "s73")
        assertNull(OtherCopy.pick(mine, listOf(mine), listOf(remote(xr3ed, "xr3ed", 73, "s73"))))
    }
}
