/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.SiteClones
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** A source with a plain constructor, as CloudStream extensions register them. Reads titles from its site. */
class GroupMMirrorSite : MainAPI() {
    override var name = "YuMirror"
    override var mainUrl = "http://localhost"
    override val supportedTypes = setOf(TvType.Movie)
    override suspend fun search(query: String, page: Int): SearchResponseList =
        newSearchResponseList(titles(app.get("$mainUrl/search?q=$query").text), false)
    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
    override suspend fun load(url: String): LoadResponse = newMovieLoadResponse("Mirror film", url, TvType.Movie, url)
    private fun titles(html: String): List<SearchResponse> =
        Regex("<a href=\"([^\"]+)\">([^<]+)</a>").findAll(html).map { m -> newMovieSearchResponse(m.groupValues[2], mainUrl + m.groupValues[1], TvType.Movie) }.toList()
}

/**
 * F7 (Group M) on a device: "Clone site" makes a second copy of an installed source that reads another
 * address. One local server answers on two host names (localhost = the original site, 127.0.0.1 = its
 * clone) and puts the host it was asked on into the titles, so the test sees which site each source read.
 */
@RunWith(AndroidJUnit4::class)
class GroupMCloneSiteDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val hosts = Collections.synchronizedList(ArrayList<String>())
    private var made: String? = null
    private fun log(s: String) = Log.i("YuGroupM", s)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val h = request.headers["Host"].orEmpty().substringBefore(':')
                hosts += h
                return MockResponse.Builder().code(200).addHeader("Content-Type", "text/html; charset=UTF-8")
                    .body("<html><body><a href=\"/film/1\">Film on $h</a></body></html>").build()
            }
        }
        server.start()
        OnlineRuntime.init(ctx)
        SiteClones.init(ctx)
        OnlineRepository.clearCaches()
    }

    @After fun tearDown() {
        made?.let { SiteClones.remove(ctx, it) }
        runCatching { server.close() }
    }

    @Test fun aCloneSearchesTheOtherSiteNextToTheOriginal() = runBlocking<Unit> {
        val orig = GroupMMirrorSite().apply {
            name = "YuMirror " + System.nanoTime()
            mainUrl = "http://localhost:${server.port}"
            sourcePlugin = "yu-groupm-clone"
        }
        val added = SiteClones.add(ctx, orig, "http://127.0.0.1:${server.port}/")
        log("GM_CASE F7 add=${added.getOrNull() ?: added.exceptionOrNull()?.message}")
        assertTrue("clone added: ${added.exceptionOrNull()?.message}", added.isSuccess)
        val name = added.getOrThrow().also { made = it }
        assertEquals("${orig.name} · 127.0.0.1", name)
        assertTrue("the same address twice is refused", SiteClones.add(ctx, orig, "127.0.0.1:${server.port}").isFailure)

        val apis = SiteClones.expand(listOf(orig))
        assertEquals(listOf(orig.name, name), apis.map { it.name })
        val found = OnlineRepository.search("film", apis, retry = true).toList().filter { it.error == null }.associate { it.api to it.items.map { i -> i.name } }
        val line = "GM_CASE F7 found=$found hosts=${hosts.toSet()}"
        log(line)
        assertEquals(line, listOf("Film on localhost"), found[orig.name])
        assertEquals(line, listOf("Film on 127.0.0.1"), found[name])
        assertEquals("the original keeps its address", "http://localhost:${server.port}", orig.mainUrl)

        // Kept across a restart.
        SiteClones.resetForTests()
        SiteClones.init(ctx)
        assertTrue("kept: ${SiteClones.all()}", SiteClones.all().any { it.name == name && it.of == orig.name })
        assertEquals(listOf(orig.name, name), SiteClones.expand(listOf(orig)).map { it.name })

        SiteClones.remove(ctx, name); made = null
        assertEquals(listOf(orig.name), SiteClones.expand(listOf(orig)).map { it.name })
        log("GM_RESULT F7 PASS")
    }
}
