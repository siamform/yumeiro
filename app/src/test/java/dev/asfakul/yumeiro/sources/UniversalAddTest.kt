package dev.asfakul.yumeiro.sources

import dev.asfakul.yumeiro.sources.UniversalAdd.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** "Add anything" decides the extension kind from the link or the file, never from the user. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalAddTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun schemesAndShapes() {
        assertEquals(Kind.CS_REPO, UniversalAdd.hint("cloudstreamrepo://raw.githubusercontent.com/a/b/builds/repo.json").kind)
        assertEquals("https://raw.githubusercontent.com/a/b/builds/repo.json", UniversalAdd.hint("cloudstreamrepo://raw.githubusercontent.com/a/b/builds/repo.json").url)
        assertEquals(Kind.STREMIO, UniversalAdd.hint("stremio://v3-cinemeta.strem.io/manifest.json").kind)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", UniversalAdd.hint("stremio://v3-cinemeta.strem.io/manifest.json").url)
        val ani = UniversalAdd.hint("aniyomi://add-repo?url=https%3A%2F%2Fraw.githubusercontent.com%2Fx%2Fy%2Frepo%2Findex.min.json")
        assertEquals(Kind.ANIYOMI_REPO, ani.kind); assertEquals("https://raw.githubusercontent.com/x/y/repo/index.min.json", ani.url)
        assertEquals(Kind.TACHI_REPO, UniversalAdd.hint("tachiyomi://add-repo?url=https://k.example/index.min.json").kind)
        assertEquals(Kind.TACHI_REPO, UniversalAdd.hint("mihon://add-repo?url=https://k.example").kind)
        assertEquals(Kind.JS_REPO, UniversalAdd.hint("mangayomi://add-repo?anime=https://m.example/anime_index.json").kind)
        assertEquals(Kind.CS_PLUGIN, UniversalAdd.hint("https://x.example/builds/Sora.cs3").kind)
        assertEquals(Kind.TACHI_APK, UniversalAdd.hint("https://x.example/apk/tachiyomi-en.foo-v14.3.apk").kind)
        val code = UniversalAdd.hint("megarepo")
        assertEquals("megarepo", code.shortCode); assertEquals(Kind.CS_REPO, code.kind)
        assertNull(UniversalAdd.hint("example.com/addon").kind)
        assertEquals("https://example.com/addon", UniversalAdd.hint("example.com/addon").url)
    }

    @Test fun githubPagesTryEveryFormat() {
        val c = UniversalAdd.candidates("https://github.com/owner/repo")
        assertTrue(c.contains("https://raw.githubusercontent.com/owner/repo/builds/repo.json"))
        assertTrue(c.contains("https://raw.githubusercontent.com/owner/repo/repo/index.min.json"))
        assertTrue(c.contains("https://raw.githubusercontent.com/owner/repo/main/index.json"))
        assertEquals(listOf("https://raw.githubusercontent.com/o/r/main/dist/plugins.json"), UniversalAdd.candidates("https://github.com/o/r/blob/main/dist/plugins.json"))
        val site = UniversalAdd.candidates("https://addon.example")
        assertEquals("https://addon.example", site.first())
        assertTrue(site.contains("https://addon.example/manifest.json"))
        assertTrue(site.contains("https://addon.example/System/Info/Public"))
        assertTrue(site.contains("https://addon.example/identity"))
    }

    @Test fun contentDecidesTheKind() {
        val u = "https://x.example/f"
        assertEquals(Kind.CS_PLUGIN, UniversalAdd.sniff(u, zip("manifest.json" to """{"pluginClassName":"a.B","name":"B","version":1}""", "classes.dex" to "dex")))
        assertEquals(Kind.TACHI_APK, UniversalAdd.sniff(u, zip("AndroidManifest.xml" to "bin", "classes.dex" to "dex")))
        assertNull(UniversalAdd.sniff(u, zip("readme.txt" to "hi")))
        assertEquals(Kind.CS_REPO, UniversalAdd.sniff(u, """{"name":"R","manifestVersion":1,"pluginLists":["plugins.json"]}""".toByteArray()))
        assertEquals(Kind.CS_LIST, UniversalAdd.sniff(u, """[{"internalName":"Sora","url":"Sora.cs3","version":1}]""".toByteArray()))
        assertEquals(Kind.ANIYOMI_REPO, UniversalAdd.sniff(u, """[{"name":"Aniyomi: X","pkg":"eu.kanade.tachiyomi.animeextension.en.x","apk":"x.apk","lang":"en","code":1}]""".toByteArray()))
        assertEquals(Kind.MIHON_REPO, UniversalAdd.sniff(u, """[{"name":"Tachiyomi: Y","pkg":"eu.kanade.tachiyomi.extension.en.y","apk":"y.apk","lang":"en","code":1}]""".toByteArray()))
        assertEquals(Kind.TACHI_REPO, UniversalAdd.sniff(u, """{"meta":{"name":"Keiyoushi","shortName":"k","signingKeyFingerprint":"ab"}}""".toByteArray()))
        assertEquals(Kind.STREMIO, UniversalAdd.sniff(u, """{"id":"org.cinemeta","version":"3.0.0","name":"Cinemeta","resources":["catalog","meta"],"types":["movie"],"catalogs":[]}""".toByteArray()))
        assertEquals(Kind.JS_REPO, UniversalAdd.sniff(u, """[{"name":"S","id":1,"baseUrl":"https://s","lang":"en","itemType":1,"sourceCodeUrl":"https://s.js"}]""".toByteArray()))
        assertEquals(Kind.JELLYFIN, UniversalAdd.sniff(u, """{"ServerName":"home","Version":"10.9","ProductName":"Jellyfin Server","Id":"x"}""".toByteArray()))
        assertEquals(Kind.EMBY, UniversalAdd.sniff(u, """{"ServerName":"home","Version":"4.8","ProductName":"Emby Server","Id":"x"}""".toByteArray()))
        assertEquals(Kind.PLEX, UniversalAdd.sniff(u, """<?xml version="1.0"?><MediaContainer size="0" machineIdentifier="abc" version="1.4"/>""".toByteArray()))
        assertEquals(Kind.TACHI_REPO, UniversalAdd.sniff("https://k.example/index.pb", byteArrayOf(0x0a, 0x12, 0x01)))
        assertNull(UniversalAdd.sniff(u, "<html><body>Not found</body></html>".toByteArray()))
        assertNull(UniversalAdd.sniff(u, """{"hello":"world"}""".toByteArray()))
    }

    @Test fun barePluginListIsItsOwnRepository() {
        val r = dev.asfakul.yumeiro.online.ExtensionFormats.parseRepo("https://raw.githubusercontent.com/a/b/builds/plugins.json", """[{"internalName":"Sora","url":"Sora.cs3"}]""")
        assertEquals(listOf("https://raw.githubusercontent.com/a/b/builds/plugins.json"), r.pluginLists)
        assertEquals("a/b", r.name)
        assertEquals("https://raw.githubusercontent.com/a/b/builds/repo.json", dev.asfakul.yumeiro.online.normalizeRepoUrl("cloudstreamrepo://raw.githubusercontent.com/a/b/builds/repo.json"))
        assertEquals("https://raw.githubusercontent.com/a/b/builds/repo.json", dev.asfakul.yumeiro.online.normalizeRepoUrl("https://cs.repo/?https://raw.githubusercontent.com/a/b/builds/repo.json"))
    }
}
