/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * B-30: DiskWala shares. Measured 2026-11-21 (diskwala.com/app/6afdd044…): the web route `/app/:id`
 * is only a "Get the app" page; the web bundle never asks for a video address, and the public
 * `file/temp_info` answer has the file's name, size and type but no URL. The file address is only
 * given to DiskWala's own Android app (an app-integrity check Hikari also notes), which Yumeiro does
 * not fake. So the link is checked, then handed to the DiskWala app, instead of a web page that
 * waits and never plays.
 */
object DiskWalaApi {
    @Volatile internal var base = "https://udapi.diskwala.com/api/v1"
    const val APP_PACKAGE = "com.diskwalaapp"

    data class FileInfo(val name: String, val bytes: Long, val type: String)

    /** `6afdd044…` from `…/app/6afdd044…`. */
    private val RX_SHARE = Regex("""/app/([A-Za-z0-9]{8,})""")

    fun shareId(url: String): String? =
        RX_SHARE.find(url.substringBefore('?').substringBefore('#'))?.groupValues?.get(1)

    /** File facts, or null when DiskWala doesn't know the share (removed or mistyped). */
    fun info(client: OkHttpClient, id: String): FileInfo? = runCatching {
        val req = Request.Builder().url("$base/file/temp_info")
            .header("Origin", "https://www.diskwala.com").header("Referer", "https://www.diskwala.com/")
            .post("""{"id":"$id"}""".toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val o = (Json.parseToJsonElement(r.body?.string().orEmpty()) as JsonObject)["fileInfo"] as? JsonObject ?: return null
            fun s(k: String) = runCatching { o[k]?.jsonPrimitive?.content }.getOrNull()
            FileInfo(s("name") ?: tr(R.string.diskwala_file, "DiskWala file"), s("size")?.toLongOrNull() ?: 0L, s("type").orEmpty())
        }
    }.getOrNull()

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
        else -> "$bytes bytes"
    }

    /** DW-BOT-1: shown while the downloader bot's mini app gets the file. */
    val VIA_BOT: String get() = tr(R.string.opening_it_through_a_telegram_downloader, "Opening it through a Telegram downloader bot (a third party) with your Telegram account.")
    val SIGN_IN_HINT: String get() = tr(R.string.to_play_it_in_yumeiro_sign, "To play it in Yumeiro, sign in to Telegram (Websites > Telegram); DiskWala links then open through a Telegram downloader bot.")

    val APP_ONLY: String get() = tr(R.string.diskwala_only_plays_its_files_inside_2, "DiskWala only plays its files inside the DiskWala app; its website shows a download page, not the video.")
}
