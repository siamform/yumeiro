/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Group I: Beta release picking on beta-main (gradle.properties sets yumeiro.channel=beta). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BetaChannelTest {
    private fun release(tag: String, draft: Boolean = false, pre: Boolean = true, code: Int = 1) = JSONObject()
        .put("tag_name", tag).put("draft", draft).put("prerelease", pre).put("name", "Yumeiro $tag")
        .put("body", "notes\n<!-- versionCode: $code -->")
        .put("assets", JSONArray()
            .put(JSONObject().put("name", "Yumeiro-Beta-v${tag.removePrefix("beta-v")}.apk").put("id", 7).put("size", 100)
                .put("url", "https://api.github.com/repos/siamform/yumeiro/releases/assets/7")
                .put("browser_download_url", "https://github.com/siamform/yumeiro/releases/download/$tag/x.apk")
                .put("digest", "sha256:" + "a".repeat(64)))
            .put(JSONObject().put("name", "Yumeiro-Beta-TV-v${tag.removePrefix("beta-v")}.tv.apk").put("id", 8).put("size", 100)
                .put("url", "https://api.github.com/repos/siamform/yumeiro/releases/assets/8")))

    @Test fun `beta-main builds are the Beta channel and read the release list`() {
        assertEquals("beta", BuildConfig.UPDATE_CHANNEL)
        assertTrue(UpdateConfig.isBeta)
        assertTrue(UpdateConfig.latestReleaseApi, UpdateConfig.latestReleaseApi.endsWith("/repos/siamform/yumeiro/releases?per_page=30"))
    }

    @Test fun `newest published beta wins, stable releases and drafts are skipped`() {
        val list = JSONArray()
            .put(release("beta-v2.4.0-beta.9", draft = true, code = 220009))
            .put(release("v2.3.1", pre = false, code = 23))
            .put(release("beta-v2.4.0-beta.8", code = 220008))
            .put(release("beta-v2.4.0-beta.7", code = 220007))
        val picked = JSONObject(GitHubReleaseClient.pickBetaRelease(list.toString()))
        assertEquals("beta-v2.4.0-beta.8", picked.getString("tag_name"))
        val info = GitHubReleaseClient.parseRelease(picked.toString(), UpdateSource.PUBLIC, abis = emptyList())
        assertEquals("2.4.0-beta.8", info.versionName)
        assertEquals(220008L, info.versionCode)
        assertEquals("Yumeiro-Beta-v2.4.0-beta.8.apk", info.asset.name)
        val tv = GitHubReleaseClient.parseRelease(picked.toString(), UpdateSource.PUBLIC, abis = emptyList(), tv = true)
        assertEquals("Yumeiro-Beta-TV-v2.4.0-beta.8.tv.apk", tv.asset.name)
    }

    @Test fun `a list with only stable releases has no beta update`() {
        val list = JSONArray().put(release("v2.3.0", pre = false)).put(release("v2.2.0", pre = false))
        try { GitHubReleaseClient.pickBetaRelease(list.toString()); fail("stable release picked by Beta") }
        catch (e: GitHubReleaseClient.UpdateCheckException) { assertEquals("No Beta release has been published yet.", e.message) }
    }

    @Test fun `the beta version shown keeps its beta number`() {
        assertTrue(UpdateConfig.currentVersionName, !UpdateConfig.currentVersionName.endsWith("-debug"))
    }
}
