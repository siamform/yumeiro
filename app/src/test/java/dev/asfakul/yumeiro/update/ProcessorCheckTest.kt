package dev.asfakul.yumeiro.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessorCheckTest {
    @Test fun readsProcessorsFromApkEntries() {
        val e = sequenceOf("classes.dex", "lib/armeabi-v7a/libc++_shared.so", "lib/arm64-v8a/libc++_shared.so", "lib/arm64-v8a/x.so", "lib/mips/x.so", "res/lib/x86/no")
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), ProcessorCheck.abisIn(e))
    }

    @Test fun matchingBuildNeedsNoNote() {
        val r = ProcessorCheck.check(listOf("arm64-v8a", "armeabi-v7a", "armeabi"), listOf("arm64-v8a"))
        assertEquals("arm64-v8a", r.phone); assertNull(r.note); assertEquals("arm64-v8a", ProcessorCheck.installedText(r))
    }

    @Test fun universalAndThirtyTwoBitBuildsSayWhatTheNextUpdateTakes() {
        val u = ProcessorCheck.check(listOf("arm64-v8a", "armeabi-v7a"), listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        assertEquals("universal", ProcessorCheck.installedText(u)); assertTrue(u.note!!.contains("arm64-v8a (64-bit ARM)"))
        val old = ProcessorCheck.check(listOf("arm64-v8a", "armeabi-v7a"), listOf("armeabi-v7a"))
        assertTrue(old.note!!.contains("switches"))
    }

    @Test fun wrongProcessorPointsToTheUniversalApk() {
        val r = ProcessorCheck.check(listOf("x86_64"), listOf("arm64-v8a"))
        assertTrue(r.note!!.contains("universal"))
    }

    @Test fun updaterPicksThePhonesProcessorFile() {
        val json = """{"tag_name":"v2.1.0","name":"v2.1.0","body":"","assets":[
          {"id":1,"name":"release-Yumeiro-v2.1.0.apk","size":90,"url":"u1","browser_download_url":"b1"},
          {"id":2,"name":"Yumeiro-v2.1.0-arm64-v8a.apk","size":40,"url":"u2","browser_download_url":"b2"},
          {"id":3,"name":"Yumeiro-v2.1.0-armeabi-v7a.apk","size":35,"url":"u3","browser_download_url":"b3"},
          {"id":4,"name":"Yumeiro-TV-v2.1.0-arm64-v8a.tv.apk","size":40,"url":"u4","browser_download_url":"b4"}]}"""
        fun pick(abis: List<String>) = GitHubReleaseClient.parseRelease(json, UpdateSource.PUBLIC, abis).asset.name
        assertEquals("Yumeiro-v2.1.0-arm64-v8a.apk", pick(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("Yumeiro-v2.1.0-armeabi-v7a.apk", pick(listOf("armeabi-v7a")))
        assertEquals("release-Yumeiro-v2.1.0.apk", pick(listOf("x86_64")))
    }
}
