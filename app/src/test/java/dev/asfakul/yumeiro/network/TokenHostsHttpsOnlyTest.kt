/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * G12 X7: cleartext is allowed app-wide for user IPTV and LAN streams, so every host that receives
 * an account token, an API key or the update download must be pinned to HTTPS in
 * `network_security_config.xml`. A typo or a forgotten host would let a token travel in plain text.
 */
class TokenHostsHttpsOnlyTest {
    private val xml = listOf("src/main/res/xml/network_security_config.xml", "app/src/main/res/xml/network_security_config.xml")
        .map(::File).first { it.exists() }.readText()

    private val httpsOnly: List<String> = run {
        val block = xml.substringAfter("cleartextTrafficPermitted=\"false\"").substringBefore("</domain-config>")
        Regex("<domain includeSubdomains=\"true\">([^<]+)</domain>").findAll(block).map { it.groupValues[1].trim() }.toList()
    }

    private fun covered(host: String) = httpsOnly.any { host == it || host.endsWith(".$it") }

    @Test fun everyTokenHostIsHttpsOnly() {
        val hosts = listOf(
            "api.trakt.tv", "trakt.tv", "api.simkl.com", "graphql.anilist.co", "anilist.co", "api.myanimelist.net",
            "myanimelist.net", "kitsu.app", "plex.tv", "app.plex.tv", "accounts.google.com", "api.onedrive.com",
            "ws.audioscrobbler.com", "api.opensubtitles.com", "api.subdl.com", "lrclib.net", "api.aniskip.com",
            "api.github.com", "raw.githubusercontent.com", "api.yumeiro.click", "yumeiro.click",
        )
        val missing = hosts.filterNot(::covered)
        assertTrue("token hosts allowed over plain HTTP: $missing", missing.isEmpty())
    }

    /** CQ-87/88 (2026-11-23): third-party API hosts whose CodeQL pinning alerts are accepted stay HTTPS-only. */
    @Test fun acceptedUnpinnedApiHostsAreHttpsOnly() {
        val hosts = listOf("api.tvmaze.com", "api.ani.zip", "data.simkl.in", "simkl.in")
        val missing = hosts.filterNot(::covered)
        assertTrue("API hosts allowed over plain HTTP: $missing", missing.isEmpty())
    }

    @Test fun cleartextStaysOnForUserStreams() {
        // IPTV, cameras and LAN servers use http://; turning it off globally would break them.
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"true\">"))
    }
}
