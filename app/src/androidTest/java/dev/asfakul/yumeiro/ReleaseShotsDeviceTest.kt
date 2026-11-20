/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.AppProxy
import dev.asfakul.yumeiro.network.DnsConfig
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.ui.screens.settings.NetworkSettingsCard
import dev.asfakul.yumeiro.ui.screens.settings.ProxySettingsCard
import dev.asfakul.yumeiro.ui.screens.sources.ScriptReposTab
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release images for 1.9.0 (handover step 6): the real setting screens of three shipped features, as a
 * user sees them, saved as screenshots (rc_*). Each case also checks that what the image shows is true:
 *  a Proxy: HTTP chosen, host and port typed (never saved, so no traffic goes to the example host);
 *  b Automatic DNS fallback: switched on with a tap, the setting really changes (restored after);
 *  c Script repos: a Vega repository from a local server is added and its two providers are listed.
 * Not a feature test of its own (ProxyDeviceTest, Nbug6/Nbug9 and VegaProvider/UniversalAddEcosystems are);
 * not mapped to a checklist row. Log tag YuShots.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseShotsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val tv get() = Edition.isTv
    private fun log(s: String) = Log.i("YuShots", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 15_000) = runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private lateinit var server: MockWebServer
    private var dnsBefore = DnsConfig()
    private var proxyBefore = AppProxy.Config()
    private var repoUrl: String? = null

    @Before fun setUp() {
        NetworkSettings.init(ctx); dnsBefore = NetworkSettings.dns.value
        proxyBefore = AppProxy.load(ctx)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/vega/manifest.json" -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(
                    """[{"display_name":"Open Films","value":"openfilms","version":"1.2.0","type":"global","disabled":false},""" +
                        """{"display_name":"Public Domain Classics","value":"pdclassics","version":"0.9.1","type":"global","disabled":false}]""",
                ).build()
                else -> MockResponse.Builder().code(404).body("not here").build()
            }
        }
        server.start()
    }

    @After fun tearDown() {
        NetworkSettings.setDns(ctx, dnsBefore)
        AppProxy.save(ctx, proxyBefore)
        repoUrl?.let { EcosystemRegistry.removeRepo(ctx, it) }
        runCatching { server.close() }
    }

    @Test fun a_proxySettings() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { FirstRun.AppBackground { Column(Modifier.verticalScroll(rememberScrollState())) { ProxySettingsCard() } } } } }
        assertTrue("proxy card", waitTag("proxy_type_http"))
        Thread.sleep(1_500); compose.waitForIdle() // the card loads the saved config off the main thread first
        compose.onNodeWithTag("proxy_type_http", true).performScrollTo().performClick()
        assertTrue("host field", waitTag("proxy_host"))
        compose.onNodeWithTag("proxy_host", true).performTextReplacement("proxy.example.org")
        compose.onNodeWithTag("proxy_port", true).performTextReplacement("8080")
        compose.waitForIdle()
        FirstRun.shot("rc_proxy")
        assertEquals("not saved by the image case", proxyBefore, AppProxy.load(ctx))
        log("SHOTS proxy ok tv=$tv")
    }

    @Test fun b_dnsFallbackSetting() {
        NetworkSettings.setDns(ctx, dnsBefore.copy(provider = dev.asfakul.yumeiro.network.DnsProvider.SYSTEM, fallback = false))
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { FirstRun.AppBackground { Column(Modifier.verticalScroll(rememberScrollState())) { NetworkSettingsCard() } } } } }
        assertTrue("DNS fallback switch", waitTag("toggle_dns_fallback_auto"))
        compose.onNodeWithTag("toggle_dns_fallback_auto", true).performScrollTo().performClick()
        compose.waitUntil(5_000) { NetworkSettings.dns.value.fallback }
        compose.waitForIdle()
        FirstRun.shot("rc_dns_fallback")
        log("SHOTS dns fallback=${NetworkSettings.dns.value.fallback} tv=$tv")
    }

    @Test fun c_scriptRepos() {
        val url = "http://${server.hostName}:${server.port}/vega/manifest.json"
        val added = runBlocking { EcosystemRegistry.addRepo(ctx, url) }
        log("SHOTS repo add: ${added.exceptionOrNull()?.message ?: "ok"}")
        assertTrue("Vega repository added: ${added.exceptionOrNull()?.message}", added.isSuccess)
        repoUrl = url
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { FirstRun.AppBackground { ScriptReposTab() } } } }
        assertTrue("repository row", waitTag("script_repo"))
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("script_available", true).fetchSemanticsNodes().size >= 2 }
        compose.waitForIdle()
        FirstRun.shot("rc_script_repos")
        log("SHOTS script repos ok tv=$tv")
    }
}
