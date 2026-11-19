/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * F-05 step 1: SkyStream, Nuvio and Vega formats are told apart from CloudStream. Before the fix
 * a SkyStream repository has "pluginLists" and was sent to the CloudStream installer.
 * Kinds are compared by name so this file compiles against the old enum.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalAddFormatsTest {
    private fun sniff(url: String, body: String) = UniversalAdd.sniff(url, body.toByteArray())?.name
    private fun zip(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun skyStreamRepositoryIsNotCloudStream() {
        val sky = """{"name":"Sky Repo","packageName":"com.example.sky","manifestVersion":1,"pluginLists":["https://x.example/plugins.json"]}"""
        assertNotEquals("SkyStream repo sniffed as CloudStream", "CS_REPO", sniff("https://x.example/repo.json", sky))
        assertEquals("SKYSTREAM_REPO", sniff("https://x.example/repo.json", sky))
        val skyRepos = """{"name":"Sky Mega","id":"mega","repos":["https://y.example/repo.json"]}"""
        assertEquals("SKYSTREAM_REPO", sniff("https://y.example/repo.json", skyRepos))
    }

    @Test fun cloudStreamRepositoryStaysCloudStream() {
        val cs = """{"name":"CS","manifestVersion":1,"pluginLists":["https://x.example/plugins.json"]}"""
        assertEquals("CS_REPO", sniff("https://x.example/repo.json", cs))
    }

    @Test fun nuvioAndVegaManifestsAreRecognised() {
        val nuvio = """{"name":"Nuvio scrapers","version":"1.0.0","scrapers":[{"id":"a","name":"A","filename":"providers/a.js","supportedTypes":["movie"]}]}"""
        assertEquals("NUVIO_REPO", sniff("https://n.example/manifest.json", nuvio))
        val vega = """[{"display_name":"VMovies","value":"vega","version":"2.52","type":"global","hasSettings":true}]"""
        assertEquals("VEGA_REPO", sniff("https://v.example/manifest.json", vega))
    }

    @Test fun skyPluginFileIsRecognised() {
        val sky = zip("plugin.json" to """{"packageName":"com.example.p","name":"P","version":1}""", "plugin.js" to "function getHome(){}")
        assertEquals("SKY_PLUGIN", UniversalAdd.sniff("p.sky", sky)?.name)
    }

    @Test fun unsupportedFormatsSayWhatTheyAre() {
        val labels = listOf("SKYSTREAM_REPO", "SKY_PLUGIN", "NUVIO_REPO", "VEGA_REPO").map { n ->
            UniversalAdd.Kind.entries.firstOrNull { it.name == n }?.label.orEmpty()
        }
        assertTrue("labels: $labels", labels[0].contains("SkyStream") && labels[1].contains("SkyStream") && labels[2].contains("Nuvio") && labels[3].contains("Vega"))
    }
}
