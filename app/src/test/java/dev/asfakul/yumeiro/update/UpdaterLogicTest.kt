/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.security.TrustedSources
import dev.asfakul.yumeiro.update.ui.NoteLine
import dev.asfakul.yumeiro.update.ui.parseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdaterLogicTest {

    @Test
    fun semver_comparesNumericallyNotLexically() {
        assertTrue(SemVer.isNewer("1.10.0", "1.9.9"))
        assertTrue(SemVer.isNewer("v2.0.0", "1.99.99"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.1"))
        assertEquals(0, SemVer.compare("v1.2", "1.2.0"))
        assertEquals(0, SemVer.compare("1.2.3-debug", "1.2.3"))
    }

    private val sampleRelease = """
        {
          "tag_name": "v1.2.0",
          "name": "Yumeiro v1.2.0",
          "html_url": "https://github.com/o/r/releases/tag/v1.2.0",
          "published_at": "2026-11-03T10:00:00Z",
          "body": "## What's changed\n- feat: new updater (abc1234)\n<!-- versionCode: 42 -->",
          "assets": [
            { "id": 1, "name": "SHA256SUMS.txt", "size": 100, "url": "u1", "browser_download_url": "b1" },
            { "id": 2, "name": "release-Yumeiro-v1.2.0.apk", "size": 12345, "url": "https://api.github.com/x/2",
              "browser_download_url": "https://github.com/x/2.apk", "digest": "sha256:ABCDEF" }
          ]
        }
    """.trimIndent()

    @Test
    fun parseRelease_prefersTheDevicesProcessorThenUniversal() {
        fun asset(id: Int, name: String) = """{"id":$id,"name":"$name","size":1,"url":"https://api.github.com/a/$id","browser_download_url":"https://github.com/d/$name"}"""
        val json = """{"tag_name":"v1.2.0","name":"v1.2.0","body":"","assets":[
            ${asset(1, "release-Yumeiro-v1.2.0.apk")},${asset(2, "Yumeiro-v1.2.0-armeabi-v7a.apk")},
            ${asset(3, "Yumeiro-v1.2.0-arm64-v8a.apk")},${asset(4, "SHA256SUMS.txt")}]}"""
        assertEquals("Yumeiro-v1.2.0-arm64-v8a.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("arm64-v8a", "armeabi-v7a")).asset.name)
        assertEquals("Yumeiro-v1.2.0-armeabi-v7a.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("armeabi-v7a")).asset.name)
        assertEquals("x86 TV box gets the universal APK", "release-Yumeiro-v1.2.0.apk",
            GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("x86")).asset.name)
        assertEquals("x86_64", GitHubReleaseClient.abiOf("Yumeiro-v1-x86_64.apk"))
        assertEquals("no TV files: the TV app finds nothing", true,
            runCatching { GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("arm64-v8a"), tv = true) }.isFailure)
        assertNull(GitHubReleaseClient.abiOf("release-Yumeiro-v1.2.0.apk"))
    }

    @Test
    fun parseRelease_picksApkAndVersionCode() {
        val r = GitHubReleaseClient.parseRelease(sampleRelease, UpdateSource.PUBLIC)
        assertEquals("1.2.0", r.versionName)
        assertEquals(42L, r.versionCode)
        assertEquals("release-Yumeiro-v1.2.0.apk", r.asset.name)
        assertEquals("abcdef", r.asset.sha256)
        assertFalse(r.notes.contains("versionCode"))
    }

    @Test
    fun releaseCodec_roundTrips() {
        val r = GitHubReleaseClient.parseRelease(sampleRelease, UpdateSource.AUTHENTICATED)
        assertEquals(r, ReleaseCodec.decode(ReleaseCodec.encode(r)))
        assertNull(ReleaseCodec.decode("not json"))
    }

    @Test
    fun releaseNotes_parseHeadingsAndBullets() {
        val lines = parseNotes("## Features\n- **Updater** with [link](http://x)\n\nPlain text\n<!-- hidden -->")
        assertEquals(NoteLine.Heading("Features"), lines[0])
        assertEquals(NoteLine.Bullet("Updater with link"), lines[1])
        assertEquals(NoteLine.Paragraph("Plain text"), lines[2])
        assertEquals(3, lines.size)
    }

    // ---- trust boundary (security hardening)

    private val sha = "a".repeat(64)
    private val apk = "release-Yumeiro-v1.2.0.apk"

    @Test
    fun parseRelease_capturesChecksumsAsset() {
        val r = GitHubReleaseClient.parseRelease(sampleRelease, UpdateSource.PUBLIC)
        assertEquals("u1", r.asset.checksumsUrl)
    }

    @Test
    fun reconcileChecksum_usesDigestOrSumsAndRejectsConflicts() {
        assertEquals(sha, GitHubReleaseClient.reconcileChecksum(sha.uppercase(), null, apk))
        assertEquals(sha, GitHubReleaseClient.reconcileChecksum(null, "$sha  $apk\n", apk))
        assertEquals(sha, GitHubReleaseClient.reconcileChecksum(sha, "$sha *$apk", apk))
        assertNull(GitHubReleaseClient.reconcileChecksum(null, null, apk))
        assertNull(GitHubReleaseClient.reconcileChecksum("abcdef", "$sha  other.apk", apk)) // malformed / wrong file
        assertTrue(runCatching { GitHubReleaseClient.reconcileChecksum(sha, "${"b".repeat(64)}  $apk", apk) }.isFailure)
    }

    @Test
    fun officialReleaseAssetUrls() {
        val ok = "https://github.com/siamform/yumeiro/releases/download/v1.2.0/$apk"
        assertTrue(TrustedSources.isOfficialReleaseAssetUrl(ok))
        assertTrue(TrustedSources.isOfficialReleaseAssetUrl("https://api.github.com/repos/siamform/yumeiro/releases/assets/123"))
        assertFalse(TrustedSources.isOfficialReleaseAssetUrl(ok.replace("https://", "http://")))
        assertFalse(TrustedSources.isOfficialReleaseAssetUrl("https://github.com/attacker/yumeiro/releases/download/v1/$apk"))
        assertFalse(TrustedSources.isOfficialReleaseAssetUrl("https://github.com.evil.io/siamform/yumeiro/releases/download/v1/$apk"))
        assertFalse(TrustedSources.isOfficialReleaseAssetUrl("https://api.github.com/repos/siamform/yumeiro/releases/assets/1/../x"))
        assertFalse(TrustedSources.isOfficialReleaseAssetUrl(null))
    }

    @Test
    fun downloadHostsAndCatalogUrls() {
        assertTrue(TrustedSources.isGitHubDownloadHost("https://release-assets.githubusercontent.com/x?sig=1"))
        assertFalse(TrustedSources.isGitHubDownloadHost("http://objects.githubusercontent.com/x"))
        assertFalse(TrustedSources.isGitHubDownloadHost("https://githubusercontent.com.evil.io/x"))
        assertTrue(TrustedSources.isOfficialCatalogUrl("https://raw.githubusercontent.com/siamform/yumeiro/market/index.json"))
        assertTrue(TrustedSources.isOfficialCatalogUrl("https://raw.githubusercontent.com/siamform/yumeiro/streams/playlists/a.m3u"))
        assertFalse(TrustedSources.isOfficialCatalogUrl("https://raw.githubusercontent.com/siamform/yumeiro/main/index.json"))
        assertFalse(TrustedSources.isOfficialCatalogUrl("https://raw.githubusercontent.com/fork/yumeiro/market/index.json"))
        assertFalse(TrustedSources.isOfficialCatalogUrl("http://raw.githubusercontent.com/siamform/yumeiro/market/index.json"))
        assertFalse(TrustedSources.isOfficialCatalogUrl("https://raw.githubusercontent.com:8443/siamform/yumeiro/market/index.json"))
    }

    /** 1.7.0: phone and TV apps live in one release; each takes only its own files. */
    @Test
    fun parseRelease_phoneAndTvAppsTakeOnlyTheirOwnFiles() {
        fun asset(id: Int, name: String) = """{"id":$id,"name":"$name","size":1,"url":"https://api.github.com/a/$id","browser_download_url":"https://github.com/d/$name"}"""
        val json = """{"tag_name":"v1.7.0","name":"v1.7.0","body":"","assets":[
            ${asset(1, "release-Yumeiro-v1.7.0.apk")},${asset(2, "Yumeiro-v1.7.0-armeabi-v7a.apk")},
            ${asset(3, "Yumeiro-v1.7.0-arm64-v8a.apk")},${asset(4, "Yumeiro-TV-v1.7.0-universal.tv.apk")},
            ${asset(5, "Yumeiro-TV-v1.7.0-arm64-v8a.tv.apk")},${asset(6, "Yumeiro-TV-v1.7.0-armeabi-v7a.tv.apk")}]}"""
        val arm64 = listOf("arm64-v8a", "armeabi-v7a")
        assertEquals("Yumeiro-v1.7.0-arm64-v8a.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, arm64).asset.name)
        assertEquals("Yumeiro-TV-v1.7.0-arm64-v8a.tv.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, arm64, tv = true).asset.name)
        assertEquals("Yumeiro-TV-v1.7.0-armeabi-v7a.tv.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("armeabi-v7a"), tv = true).asset.name)
        assertEquals("Yumeiro-TV-v1.7.0-universal.tv.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("x86"), tv = true).asset.name)
        assertEquals("release-Yumeiro-v1.7.0.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, listOf("x86")).asset.name)
        assertEquals("arm64-v8a", GitHubReleaseClient.abiOf("Yumeiro-TV-v1.7.0-arm64-v8a.tv.apk"))
        assertNull(GitHubReleaseClient.abiOf("Yumeiro-TV-v1.7.0-universal.tv.apk"))
        // What a 1.6 updater does: it only knows "-<abi>.apk" endings, so it never sees TV files.
        for (n in listOf("Yumeiro-TV-v1.7.0-arm64-v8a.tv.apk", "Yumeiro-TV-v1.7.0-armeabi-v7a.tv.apk")) {
            val base = n.lowercase().removeSuffix(".apk")
            assertEquals(false, listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").any { base.endsWith("-$it") })
            assertEquals(false, n.startsWith("release-"))
        }
    }

    @org.junit.Test
    fun round13_single_universal_files_are_picked_on_every_processor() {
        fun asset(id: Int, name: String) = """{"id":$id,"name":"$name","size":1,"url":"https://api.github.com/a/$id","browser_download_url":"https://github.com/d/$name"}"""
        val json = """{"tag_name":"v1.8.0","name":"v1.8.0","body":"","assets":[${asset(1, "release-Yumeiro-v1.8.0.apk")},${asset(2, "Yumeiro-TV-v1.8.0.tv.apk")}]}"""
        for (abis in listOf(listOf("arm64-v8a", "armeabi-v7a"), listOf("armeabi-v7a"), listOf("x86_64", "x86"), listOf("x86"))) {
            assertEquals("release-Yumeiro-v1.8.0.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, abis).asset.name)
            assertEquals("Yumeiro-TV-v1.8.0.tv.apk", GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, abis, tv = true).asset.name)
        }
        assertNull(GitHubReleaseClient.abiOf("Yumeiro-TV-v1.8.0.tv.apk"))
    }
}
