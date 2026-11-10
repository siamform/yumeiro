/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class TachiyomiIndexTest {
    private val kei = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"

    private val v2Json = """
        {"name":"Keiyoushi","badgeLabel":"KEI","signingKey":"$kei",
         "contact":{"website":"https://keiyoushi.github.io"},
         "extensionList":{"extensions":[
          {"name":"AHottie","packageName":"eu.kanade.tachiyomi.extension.all.ahottie",
           "resources":{"apkUrl":"https://github.com/keiyoushi/extensions/releases/download/b0-0/tachiyomi-all.ahottie-v1.6.5.apk",
             "iconUrl":"https://cdn.example/ic.png","jarUrl":"https://x/y.jar"},
           "extensionLib":"1.6","versionCode":"106005","versionName":"1.6.5","contentWarning":"CONTENT_WARNING_NSFW",
           "sources":[{"id":"6289731484943315811","name":"AHottie","language":"all","homeUrl":"https://ahottie.top"}]},
          {"name":"MangaDex","packageName":"eu.kanade.tachiyomi.extension.all.mangadex",
           "resources":{"apkUrl":"apk/mdx.apk"},"extensionLib":"1.6","versionCode":"106200","versionName":"1.6.200",
           "contentWarning":"CONTENT_WARNING_MIXED",
           "sources":[{"id":"1","name":"MangaDex","language":"en"},{"id":"2","name":"MangaDex","language":"ja"}]},
          {"name":"Solo","packageName":"eu.kanade.tachiyomi.extension.en.solo","resources":{"apkUrl":"s.apk"},
           "versionCode":"5","versionName":"1.6.5","contentWarning":"CONTENT_WARNING_SAFE","sources":[{"id":"3","language":"en"}]},
          {"name":"Broken","packageName":"","resources":{"apkUrl":"b.apk"}}
         ]}}
    """.trimIndent()

    @Test fun v1() {
        val list = TachiyomiIndex.parseV1("""[{"name":"Tachiyomi: X","pkg":"a.b","apk":"x.apk","lang":"en","code":3,"version":"1.5.3","nsfw":1}]""")
        assertEquals(1, list.size)
        assertEquals("a.b", list[0].pkg)
        assertNull(list[0].apkUrl)
        assertFalse(TachiyomiIndex.isPlaceholderOnly(list))
        val repo = TachiyomiExtensions.Repo("https://x.dev/repo", TachiyomiExtensions.Kind.MANGA)
        val a = TachiyomiExtensions.Available(repo, list[0])
        assertEquals("https://x.dev/repo/apk/x.apk", a.apkUrl)
        assertEquals("https://x.dev/repo/icon/a.b.png", a.iconUrl)
    }

    @Test fun placeholderOnlyV1() {
        val list = TachiyomiIndex.parseV1("""[
            {"name":"Tachiyomi: Outdated App","pkg":"eu.kanade.tachiyomi.extension.all.keiyoushi","apk":"o.apk","lang":"all","code":1,"version":"1.4.1","nsfw":0},
            {"name":"Tachiyomi: Update to Mihon","pkg":"eu.kanade.tachiyomi.extension.all.mihon","apk":"u.apk","lang":"all","code":1,"version":"1.4.1","nsfw":0}]""")
        assertTrue(TachiyomiIndex.isPlaceholderOnly(list))
        assertFalse(TachiyomiIndex.isPlaceholderOnly(emptyList()))
    }

    @Test fun repoJson() {
        val m = TachiyomiIndex.parseRepoJson("""{"meta":{"name":"Keiyoushi","shortName":"KEI","website":"https://keiyoushi.github.io",
            "signingKeyFingerprint":"9A:DD:65 5a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"},
            "index_v2":"https://github.com/keiyoushi/extensions/raw/repo/index.pb"}""")
        assertEquals("Keiyoushi", m.name)
        assertEquals("https://github.com/keiyoushi/extensions/raw/repo/index.pb", m.indexV2)
        assertEquals(kei, m.fingerprint)
        assertEquals(TachiyomiIndex.Meta(), TachiyomiIndex.parseRepoJson("not json"))
    }

    @Test fun v2Json() {
        val p = TachiyomiIndex.parseV2(v2Json.toByteArray(), "https://raw.example/repo/index.json")
        assertEquals("Keiyoushi", p.meta.name)
        assertEquals(kei, p.meta.fingerprint)
        assertEquals(3, p.entries.size)
        val (hot, mdx, solo) = p.entries
        assertEquals(1, hot.nsfw)
        assertEquals("all", hot.lang)
        assertEquals(106005L, hot.code)
        assertEquals("1.6", hot.lib)
        assertEquals("tachiyomi-all.ahottie-v1.6.5.apk", hot.apk)
        assertEquals("https://cdn.example/ic.png", hot.iconUrl)
        assertEquals(0, mdx.nsfw) // mixed is shown like safe
        assertEquals("all", mdx.lang)
        assertEquals("https://raw.example/repo/apk/mdx.apk", mdx.apkUrl)
        assertEquals("en", solo.lang)
        val a = TachiyomiExtensions.Available(TachiyomiExtensions.Repo("https://raw.example/repo", TachiyomiExtensions.Kind.MANGA), hot)
        assertEquals(hot.apkUrl, a.apkUrl)
        assertEquals("https://cdn.example/ic.png", a.iconUrl)
    }

    @Test fun v2Gzip() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(v2Json.toByteArray()) }
        assertEquals(3, TachiyomiIndex.parseV2(out.toByteArray(), "https://raw.example/repo/index.json.gz").entries.size)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun v2Proto() {
        val idx = TachiyomiIndex.PbIndex(
            name = "Store", signingKey = kei,
            extensionList = TachiyomiIndex.PbExtensionList(listOf(
                TachiyomiIndex.PbExtension(
                    name = "A", packageName = "p.a", resources = TachiyomiIndex.PbResources("https://h/a.apk", "https://h/a.png"),
                    extensionLib = "1.6", versionCode = 7, versionName = "1.6.7", contentWarning = 3,
                    sources = listOf(TachiyomiIndex.PbSource(1, "A", "en")),
                ),
            )),
        )
        val p = TachiyomiIndex.parseV2(ProtoBuf.encodeToByteArray(idx), "https://h/index.pb")
        assertEquals("Store", p.meta.name)
        assertEquals(kei, p.meta.fingerprint)
        assertEquals(1, p.entries.size)
        assertEquals(1, p.entries[0].nsfw)
        assertEquals(7L, p.entries[0].code)
        assertEquals("https://h/a.apk", p.entries[0].apkUrl)
        val listUrl = TachiyomiIndex.PbIndex(name = "S", extensionListUrl = "list.pb")
        assertEquals("list.pb", TachiyomiIndex.extensionListUrl(ProtoBuf.encodeToByteArray(listUrl)))
        assertNull(TachiyomiIndex.extensionListUrl(ProtoBuf.encodeToByteArray(idx)))
    }

    /** The real Keiyoushi index.pb (fixture fetched Nov 2026). */
    @Test fun keiyoushiRealIndex() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("keiyoushi-index.pb")!!.readBytes()
        val p = TachiyomiIndex.parseV2(bytes, "https://github.com/keiyoushi/extensions/raw/repo/index.pb")
        assertEquals("Keiyoushi", p.meta.name)
        assertEquals(kei, p.meta.fingerprint)
        assertTrue(p.entries.size > 1000)
        val mdx = p.entries.first { it.pkg == "eu.kanade.tachiyomi.extension.all.mangadex" }
        assertTrue(mdx.apkUrl!!.startsWith("https://"))
        assertEquals("1.6", mdx.lib)
        assertTrue(p.entries.any { it.nsfw == 1 })
        assertTrue(p.entries.all { it.apkUrl!!.endsWith(".apk") })
    }

    @Test fun normalize() {
        val n = TachiyomiExtensions::normalizeRepo
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", n("https://github.com/keiyoushi/extensions/raw/repo/index.pb"))
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", n("https://github.com/keiyoushi/extensions/blob/repo/repo.json"))
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", n("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"))
        assertEquals("https://x.dev/r", n("https://x.dev/r/index.json"))
        assertEquals("https://x.dev/r", n("mihon://add-repo?url=https%3A%2F%2Fx.dev%2Fr%2Findex.min.json"))
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", n("https://github.com/keiyoushi/extensions"))
    }

    @Test fun libVersion() {
        assertEquals(1.6, TachiyomiExtensions.libVersion(1.6f)!!, 1e-9)
        assertEquals(1.6, TachiyomiExtensions.libVersion("1.6")!!, 1e-9)
        assertEquals(16.0, TachiyomiExtensions.libVersion(16)!!, 1e-9)
        assertNull(TachiyomiExtensions.libVersion(0))
        assertNull(TachiyomiExtensions.libVersion(null))
    }

    @Test fun memoStore() {
        val f = java.io.File.createTempFile("memo", ".json").also { it.delete() }
        val s = MemoStore(f, max = 2)
        val memo = kotlinx.serialization.json.buildJsonObject { put("seriesSlug", kotlinx.serialization.json.JsonPrimitive("x")) }
        s.putAll(listOf("m:/a" to memo, "m:/b" to kotlinx.serialization.json.JsonObject(emptyMap())))
        assertEquals(memo, MemoStore(f).get("m:/a"))
        assertNull(MemoStore(f).get("m:/b"))
        s.putAll(listOf("m:/c" to memo, "m:/d" to memo))
        assertNull(MemoStore(f).get("m:/a")) // LRU bound
        f.delete()
    }
}
