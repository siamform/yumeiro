package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

class StarterExtensionsTest {
    private fun r(name: String, cats: List<String>, score: Double, desc: String? = null, nsfw: Boolean = false) = RemoteExtension(
        internalName = name, name = name, url = "https://x/$name.cs3", version = 1, description = desc,
        tvTypes = if (nsfw) listOf("NSFW") else emptyList(), repoUrl = "https://x/repo.json", repoName = "x",
        score = score, categories = cats,
    )

    @Test fun picksBestMoviesAnimeAndSports() {
        val all = listOf(
            r("MovieA", listOf("Movies"), 5.0), r("MovieB", listOf("Movies"), 9.0),
            r("AnimeA", listOf("Anime"), 7.0), r("Adult", listOf("Movies"), 99.0, nsfw = true),
            r("NewsTV", listOf("Live"), 8.0), r("FootballLive", listOf("Live"), 3.0, desc = "Live football and cricket"),
        )
        assertEquals(listOf("MovieB", "AnimeA", "FootballLive"), StarterExtensions.pick(all).map { it.name })
    }

    @Test fun fallsBackToTopLiveAndAvoidsDuplicates() {
        val all = listOf(r("Both", listOf("Movies", "Anime"), 9.0), r("Anime2", listOf("Anime"), 4.0), r("TV1", listOf("Live"), 2.0))
        assertEquals(listOf("Both", "Anime2", "TV1"), StarterExtensions.pick(all).map { it.name })
    }

    @Test fun emptyCatalogPicksNothing() = assertEquals(emptyList<RemoteExtension>(), StarterExtensions.pick(emptyList()))
}
