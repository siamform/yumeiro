/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Group O device tests: a local stand-in for Yumeiro Cloud's hub (`/v1/hub/...`) answering the shapes the Worker
 * gives (server/src/hub.ts). Test fixtures only: invented titles, no TMDB data. Every request is recorded.
 */
class GroupOFakeHub {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<String>()
    val base: String get() = "http://${server.hostName}:${server.port}/v1/hub"
    private val today = LocalDate.now()

    private fun item(kind: String, id: Int, title: String, days: Long, genres: List<String>, rating: Double? = 7.1) =
        """{"id":"$kind:$id","type":"$kind","title":"$title","year":${today.plusDays(days).year},"date":"${today.plusDays(days)}",""" +
            (if (rating != null) """"rating":$rating,"votes":120,""" else "") +
            """"genres":[${genres.joinToString(",") { "\"$it\"" }}],"poster":"https://image.tmdb.org/t/p/w342/yu-fixture-$id.jpg"}"""

    private fun page(items: List<String>, next: Boolean = false) = """{"items":[${items.joinToString(",")}],"hasNext":$next}"""

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val q = request.url
                requests += path + "?" + (q.encodedQuery ?: "")
                val body = when (path) {
                    "/v1/hub/discover" -> {
                        val kind = q.queryParameter("type") ?: "movie"
                        val origin = q.queryParameter("origin")
                        val genre = q.queryParameter("genre")
                        val provider = q.queryParameter("provider")
                        val tag = listOfNotNull(origin, genre?.let { "g$it" }, provider?.let { "p$it" }, q.queryParameter("sort")).joinToString(" ")
                        if (q.queryParameter("page") != "1") page(emptyList()) else page(
                            (1..6).map { i ->
                                item(kind, 9000 + i + (if (kind == "tv") 100 else 0), "YuO ${if (kind == "tv") "Series" else "Film"} $i $tag", (i * 3).toLong(),
                                    if (i % 2 == 0) listOf("Drama") else listOf("Comedy"))
                            },
                        )
                    }
                    "/v1/hub/catalog" -> page((1..8).map { i -> item(if (i % 2 == 0) "tv" else "movie", 8000 + i, "YuO Hot $i ${q.queryParameter("shelf")}", -400, listOf("Action")) })
                    "/v1/hub/recommend" -> page((1..5).map { i -> item("movie", 7000 + i, "YuO Like ${q.queryParameter("id")} $i", -300, listOf("Drama")) })
                    "/v1/hub/providers" -> """{"providers":[{"id":8,"name":"YuO Stream"},{"id":119,"name":"YuO Prime"}]}"""
                    else -> return MockResponse.Builder().code(404).body("{}").build()
                }
                return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()
            }
        }
        server.start()
    }

    fun close() = runCatching { server.close() }
}
