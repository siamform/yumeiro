package dev.asfakul.yumeiro.update

import android.content.Context
import java.util.zip.ZipFile

/**
 * ARM check (2.2.0, owner order 2026-11-22): tells the user which processor the phone has, which build is
 * installed (one processor or universal) and which file the updater takes next. Releases ship per-processor
 * APKs ("Yumeiro-v2.2.0-arm64-v8a.apk") plus the universal one; [GitHubReleaseClient.parseRelease] already picks
 * the phone's first supported processor, so this only explains it.
 */
object ProcessorCheck {
    data class Result(val phone: String, val installed: List<String>, val note: String?)

    private val NAMES = mapOf(
        "arm64-v8a" to "64-bit ARM",
        "armeabi-v7a" to "32-bit ARM",
        "x86_64" to "64-bit x86",
        "x86" to "32-bit x86",
    )
    private val KNOWN = NAMES.keys

    fun label(abi: String): String = NAMES[abi]?.let { "$abi ($it)" } ?: abi

    /** Processors with native code inside an APK, from its "lib/<abi>/" entries, in a fixed order. */
    fun abisIn(entries: Sequence<String>): List<String> {
        val found = HashSet<String>()
        for (e in entries) {
            if (!e.startsWith("lib/")) continue
            val abi = e.substring(4).substringBefore('/', "")
            if (abi in KNOWN) found += abi
        }
        return KNOWN.filter { it in found }
    }

    /** Pure decision, unit tested: phone ABIs (preference order) and the ABIs in the installed APK. */
    fun check(phoneAbis: List<String>, installed: List<String>): Result {
        val phone = phoneAbis.firstOrNull { it in KNOWN } ?: phoneAbis.firstOrNull() ?: "unknown"
        val note = when {
            installed.isEmpty() -> null
            installed.size > 1 -> "Universal build. The next update downloads the smaller ${label(phone)} file."
            installed.first() == phone -> null
            installed.first() in phoneAbis -> "This is the ${label(installed.first())} build. The next update switches to the faster ${label(phone)} file."
            else -> "This build is for ${label(installed.first())}, not this phone. Install the universal APK from yumeiro.click/download."
        }
        return Result(phone, installed, note)
    }

    fun run(context: Context): Result {
        val installed = runCatching {
            ZipFile(context.applicationInfo.sourceDir).use { z -> abisIn(z.entries().asSequence().map { it.name }) }
        }.getOrDefault(emptyList())
        return check(GitHubReleaseClient.deviceAbis(), installed)
    }

    fun installedText(r: Result): String = when {
        r.installed.isEmpty() -> "unknown"
        r.installed.size > 1 -> "universal"
        else -> r.installed.first()
    }
}
