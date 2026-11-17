/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-5 probe (eighth cycle): "7 Sub episodes still show 7 on the Dub filter; Episode 7 on Dub
 * plays Episode 1". Yumeiro maps the extension's Sub / Dub lists one to one (OnlineRepository.map),
 * so this asks real anime extensions from the market what they send for titles whose dub lags:
 *  - the search card's Sub / Dub counts against the Sub / Dub lists of the load answer;
 *  - whether Dub episodes share one `data` (every tap loads the same video) or reuse Sub data;
 *  - for the last Dub episode, whether its links are the same as Dub episode 1's links.
 * Logs NBUG5_PROBE lines only; it fails nothing. No anime extension reachable → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class DubCountProbeDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("YuDubProbe", "NBUG5_PROBE $s")

    private suspend fun linkUrls(api: com.lagradost.cloudstream3.MainAPI, data: String): Set<String> {
        val urls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        withTimeoutOrNull(45_000) { runCatching { api.loadLinks(data, false, {}, { urls.add(it.url) }) } }
        return urls
    }

    @Test fun probeAnimeDubLists() = runBlocking {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val market = runCatching { ExtensionSources.all(ctx, force = true).items }.getOrDefault(emptyList())
        val anime = market.filter {
            it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION &&
                it.tvTypes.any { t -> t.equals("Anime", true) } && it.language?.startsWith("en") != false
        }.sortedByDescending { it.verified }.take(8)
        log("market anime candidates=${anime.map { it.internalName }}")
        for (c in anime) runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
            .onFailure { log("install ${c.internalName} failed: ${it.message}") }
        val names = anime.map { it.name }.toSet()
        val apis = ExtensionManager.providers().filter { it.name in names || it.supportedTypes.any { t -> t.name == "Anime" } }
        log("providers=${apis.map { it.name }}")
        assumeTrue("no anime extension could be installed (network / market)", apis.isNotEmpty())
        val queries = listOf("One Piece", "Frieren", "Solo Leveling", "Dandadan", "Kaiju No. 8", "Blue Lock")
        var probed = 0
        for (api in apis) for (q in queries) {
            val results = withTimeoutOrNull(30_000) { runCatching { api.search(q, 1)?.items }.getOrNull() ?: runCatching { api.search(q) }.getOrNull() }.orEmpty()
            val cards = results.filterIsInstance<AnimeSearchResponse>().filter { it.dubStatus?.contains(DubStatus.Dubbed) == true }.take(2)
            for (card in cards) {
                val raw = withTimeoutOrNull(45_000) { runCatching { api.load(card.url) }.getOrNull() } as? AnimeLoadResponse ?: continue
                val sub = raw.episodes[DubStatus.Subbed].orEmpty()
                val dub = raw.episodes[DubStatus.Dubbed].orEmpty()
                if (dub.isEmpty()) continue
                probed++
                val mapped = runCatching { OnlineRepository.load(api, card.url).episodes.mapValues { it.value.size } }.getOrNull()
                val dubData = dub.map { it.data }
                val subData = sub.map { it.data }.toSet()
                log("api=${api.name} q=$q title=${raw.name} card=${card.episodes} rawSub=${sub.size} rawDub=${dub.size} mapped=$mapped " +
                    "dubDistinctData=${dubData.toSet().size} dubDataInSub=${dubData.count { it in subData }} " +
                    "dubNumbers=${dub.mapNotNull { it.episode }.let { n -> if (n.isEmpty()) "none" else "${n.min()}..${n.max()}" }} " +
                    "dubData0=${dubData.first().take(120)} dubDataLast=${dubData.last().take(120)}")
                // NBUG-5 second probe (44192612391 showed KickassAnime Dub lists longer than Sub with the
                // same number range): what each repeated entry looks like, in the order the app shows it.
                val numbers = dub.mapNotNull { it.episode }
                if (numbers.size != numbers.toSet().size) {
                    val shown = runCatching { OnlineRepository.load(api, card.url).let { d -> dev.asfakul.yumeiro.online.OnlineSession.episodesOf(d, "Dub") } }.getOrDefault(emptyList())
                    log("api=${api.name} title=${raw.name} repeats=${numbers.size - numbers.toSet().size} raw12=" +
                        dub.take(12).joinToString(" | ") { "s${it.season} e${it.episode} n=${it.name} d=${it.data.substringAfter('?', "").take(30)}" })
                    log("api=${api.name} title=${raw.name} shown12=" +
                        shown.take(12).joinToString(" | ") { "s${it.season} e${it.episode} n=${it.name} d=${it.data.substringAfter('?', "").take(30)}" })
                    log("api=${api.name} title=${raw.name} langs=" + dub.groupingBy { it.data.substringAfter("lang=", "?").substringBefore('&') }.eachCount())
                }
                if (dub.size >= 2) {
                    val first = linkUrls(api, dub.first().data)
                    val last = linkUrls(api, dub.last().data)
                    log("api=${api.name} title=${raw.name} links dub#1=${first.size} dub#last(ep ${dub.last().episode})=${last.size} same=${first.isNotEmpty() && first == last} overlap=${first.intersect(last).size}")
                }
            }
        }
        log("SUMMARY probed=$probed")
        assumeTrue("no title with a Dub list answered", probed > 0)
    }
}
