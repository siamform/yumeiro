/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser

/**
 * Hands a video link to a download-manager app the user already has (1DM, ADM, …) instead of the
 * built-in downloader. Uses each app's public "open this URL" intent — 1DM documents extras for
 * headers, referer, user agent, cookies and file name (apps2sd.info/idmp); other apps get the
 * standard browser headers bundle. Nothing is bundled or paid: if no such app is installed, the
 * in-app downloader is used.
 */
object ExternalDownloader {
    /** Setting value: use Yumeiro's own downloader. */
    const val IN_APP = ""
    /** Setting value: show Android's "open with" list each time. */
    const val CHOOSER = "chooser"

    data class App(val pkg: String, val label: String, val hls: Boolean)

    /** Known download managers (all have free versions on the Play Store / F-Droid). */
    val KNOWN = listOf(
        App("idm.internet.download.manager.plus", "1DM+", hls = true),
        App("idm.internet.download.manager", "1DM", hls = true),
        App("idm.internet.download.manager.adm.lite", "1DM Lite", hls = true),
        App("com.dv.adm", "ADM", hls = false),
        App("com.dv.adm.pay", "ADM Pro", hls = false),
        App("com.dv.adm.old", "ADM (old)", hls = false),
    )
    private const val ONE_DM_ACTIVITY = "idm.internet.download.manager.Downloader"

    fun label(pkg: String): String = when (pkg) { IN_APP -> "Yumeiro (built-in)"; CHOOSER -> "Ask each time"; else -> KNOWN.firstOrNull { it.pkg == pkg }?.label ?: pkg }

    fun is1dm(pkg: String) = pkg.startsWith("idm.internet.download.manager")

    /** The known apps that are installed (needs the <queries> entries in the manifest on Android 11+). */
    fun installed(context: Context): List<App> = KNOWN.filter { app ->
        runCatching { context.packageManager.getPackageInfo(app.pkg, 0); true }.getOrDefault(false)
    }

    /** Why [choice] can't take this link (null = it can). DASH never; HLS only where the app saves streams. */
    fun unsupportedReason(choice: String, isHls: Boolean, isDash: Boolean): String? = when {
        choice == IN_APP -> "built-in"
        isDash -> "This source (DASH) can't be handed to another app"
        isHls && choice != CHOOSER && KNOWN.firstOrNull { it.pkg == choice }?.hls == false -> "${label(choice)} can't save streaming (HLS) links"
        else -> null
    }

    /** Extras every app gets; 1DM's own keys plus the standard browser headers bundle. Pure, unit-tested. */
    internal fun extras(pkg: String, headers: Map<String, String>, fileName: String?): Map<String, Any> {
        val h = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        fun header(name: String) = h.entries.firstOrNull { it.key.equals(name, true) }?.value
        return buildMap {
            put(Browser.EXTRA_HEADERS, h)
            if (is1dm(pkg) || pkg == CHOOSER) {
                put("extra_headers", h.filterKeys { k -> !k.equals("Referer", true) && !k.equals("User-Agent", true) && !k.equals("Cookie", true) })
                header("Referer")?.let { put("extra_referer", it) }
                header("User-Agent")?.let { put("extra_useragent", it) }
                header("Cookie")?.let { put("extra_cookies", it) }
                fileName?.takeIf { it.isNotBlank() }?.let { put("extra_filename", it) }
                put("secure_uri", true)
            }
            fileName?.takeIf { it.isNotBlank() }?.let { put("title", it) }
        }
    }

    /** Clean file name: "Show S1 E3.mp4" (no path characters). */
    fun fileName(title: String, episode: String?, isHls: Boolean): String {
        val base = listOfNotNull(title.takeIf { it.isNotBlank() }, episode?.takeIf { it.isNotBlank() }).joinToString(" ")
            .replace(Regex("""[\\/:*?"<>|\r\n]+"""), " ").replace(Regex("\\s+"), " ").trim().take(100).ifBlank { "video" }
        return "$base.mp4"
    }

    /**
     * Opens [url] in [choice]. Returns null on success, or an error text. Never throws; the link and
     * its headers go only to the app the user picked.
     */
    fun send(context: Context, choice: String, url: String, headers: Map<String, String>, fileName: String?): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "This link can't be opened in another app"
        val ex = extras(choice, headers, fileName)
        fun build(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            ex.forEach { (k, v) ->
                when (v) {
                    is Map<*, *> -> putExtra(k, Bundle().apply { v.forEach { (a, b) -> putString(a.toString(), b.toString()) } })
                    is Boolean -> putExtra(k, v)
                    else -> putExtra(k, v.toString())
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            when {
                choice == CHOOSER -> context.startActivity(Intent.createChooser(build().apply { setDataAndType(data, "video/*") }, "Download with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                is1dm(choice) -> try { context.startActivity(build().setClassName(choice, ONE_DM_ACTIVITY)) }
                    catch (_: ActivityNotFoundException) { context.startActivity(build().setPackage(choice)) }
                else -> context.startActivity(build().setPackage(choice))
            }
            null
        } catch (_: ActivityNotFoundException) {
            "${label(choice)} isn't installed or can't open this link"
        } catch (e: SecurityException) {
            "${label(choice)} refused the link"
        }
    }
}
