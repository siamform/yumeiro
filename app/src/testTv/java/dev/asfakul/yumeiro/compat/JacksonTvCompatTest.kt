/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * G12 X13-1. The TV build runs on Android 7 (minSdk 24). Jackson 2.15+ loads
 * `java.lang.BootstrapMethodError` (Android 8+) while `ObjectMapper` initialises, so on Android 7
 * every CloudStream source failed with NoClassDefFoundError (TV api24 emulator, run 43765162241;
 * jackson-databind#4653). The TV classpath must carry a Jackson line that supports SDK 24.
 */
class JacksonTvCompatTest {
    private val jacksonJars: List<File> by lazy {
        System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
            .filter { it.isFile && it.name.endsWith(".jar") && "jackson" in it.name }
    }

    @Test fun jacksonOnTheTvClasspathIsTheSdk24Line() {
        val v = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION
        assertEquals("jackson-databind major", 2, v.majorVersion)
        assertTrue("jackson-databind $v needs Android 8; the TV build needs <= 2.13", v.minorVersion <= 13)
    }

    @Test fun noJacksonClassNeedsAndroid8Errors() {
        assertTrue("no Jackson jar found on the test classpath", jacksonJars.isNotEmpty())
        val needle = "java/lang/BootstrapMethodError".toByteArray()
        val bad = jacksonJars.flatMap { jar ->
            ZipFile(jar).use { z ->
                z.entries().asSequence()
                    .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                    .filter { e -> z.getInputStream(e).use { it.readBytes() }.contains(needle) }
                    .map { "${jar.name}!${it.name}" }.toList()
            }
        }
        assertTrue("Jackson classes that fail to load on Android 7: $bad", bad.isEmpty())
    }

    /**
     * Same trap in any other library: classes that need Android 8+ just to be referenced in common
     * code. Static review of the TV classpath (G12 X13) found these only in Jackson >= 2.15;
     * `java.nio.file` and `reflect.Parameter` uses sit behind SDK checks or in methods the app never
     * calls (datastore Api26Impl, retrofit Reflection.Java8, smbj smbfs, jsoup/okio Path overloads).
     */
    @Test fun noLibraryOnTheTvClasspathReferencesAndroid8OnlyErrors() {
        val jars = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
            .filter { it.isFile && it.name.endsWith(".jar") }
            .filterNot { "junit" in it.name || "robolectric" in it.name || "android-all" in it.name || "mockk" in it.name }
        val needles = listOf("java/lang/BootstrapMethodError", "java/lang/invoke/VarHandle").map { it.toByteArray() }
        val bad = jars.flatMap { jar ->
            runCatching {
                ZipFile(jar).use { z ->
                    z.entries().asSequence()
                        .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                        .filter { e -> z.getInputStream(e).use { it.readBytes() }.let { b -> needles.any { b.contains(it) } } }
                        .map { "${jar.name}!${it.name}" }.toList()
                }
            }.getOrDefault(emptyList())
        }
        assertTrue("classes that need Android 8 to load: $bad", bad.isEmpty())
    }

    private fun ByteArray.contains(n: ByteArray): Boolean {
        outer@ for (i in 0..size - n.size) {
            for (j in n.indices) if (this[i + j] != n[j]) continue@outer
            return true
        }
        return false
    }
}
