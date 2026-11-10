/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * Reads every extension-store index format in use:
 *
 * - **v1** `index.min.json`: a JSON array (Aniyomi repos, older Mihon repos).
 * - **v2** TachiyomiX index (Mihon 0.20+ / extensions-lib 1.6, e.g. Keiyoushi): `index.pb`
 *   (protobuf) or its JSON form `index.json`, optionally gzipped, announced by `repo.json`'s
 *   `index_v2` field. Schema: github.com/mihonapp/extensions-lib/blob/main/index/index.proto
 *
 * Pure functions (no Android), so the parsing is unit tested on the JVM.
 */
object TachiyomiIndex {

    /** What a store announces about itself in `repo.json` / the v2 index header. */
    data class Meta(val name: String = "", val indexV2: String? = null, val fingerprint: String? = null)

    data class Parsed(val entries: List<TachiyomiExtensions.IndexEntry>, val meta: Meta = Meta())

    // region v1

    fun parseV1(body: String): List<TachiyomiExtensions.IndexEntry> =
        TachiyomiRuntime.json.decodeFromString(ListSerializer(TachiyomiExtensions.IndexEntry.serializer()), body)

    /**
     * Stores that moved to the v2 index keep a v1 file with only "Outdated App" / "Update to …"
     * stubs for old apps. Those must not be offered as extensions.
     */
    fun isPlaceholderOnly(entries: List<TachiyomiExtensions.IndexEntry>): Boolean =
        entries.isNotEmpty() && entries.all { isPlaceholder(it) }

    fun isPlaceholder(e: TachiyomiExtensions.IndexEntry): Boolean {
        val n = e.name.substringAfter(": ").trim()
        return n.equals("Outdated App", true) || n.startsWith("Update to ", true) ||
            e.pkg.endsWith(".extension.all.keiyoushi") || e.pkg.endsWith(".extension.all.mihon")
    }

    // endregion

    // region repo.json

    fun parseRepoJson(body: String): Meta = runCatching {
        val o = TachiyomiRuntime.json.parseToJsonElement(body).jsonObject
        val meta = o["meta"] as? JsonObject
        Meta(
            name = meta?.str("name").orEmpty(),
            indexV2 = o.str("index_v2")?.takeIf { it.startsWith("http") },
            fingerprint = meta?.str("signingKeyFingerprint")?.let(::cleanFingerprint),
        )
    }.getOrDefault(Meta())

    // endregion

    // region v2

    /** Decodes a v2 index from raw bytes: gzip or not, protobuf or JSON (sniffed, not trusted from the URL). */
    fun parseV2(bytes: ByteArray, indexUrl: String): Parsed {
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else bytes
        val first = raw.firstOrNull { it.toInt().toChar() !in " \t\r\n\uFEFF" }
        return if (first == '{'.code.toByte()) parseV2Json(raw.decodeToString(), indexUrl) else parseV2Proto(raw, indexUrl)
    }

    /** `extensionListUrl` stores (the list lives in a second file) return it here instead of entries. */
    fun extensionListUrl(bytes: ByteArray): String? = runCatching {
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else bytes
        val first = raw.firstOrNull { it.toInt().toChar() !in " \t\r\n\uFEFF" }
        if (first == '{'.code.toByte()) {
            TachiyomiRuntime.json.parseToJsonElement(raw.decodeToString()).jsonObject.str("extensionListUrl")
        } else {
            @OptIn(ExperimentalSerializationApi::class)
            ProtoBuf.decodeFromByteArray<PbIndex>(raw).extensionListUrl
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun parseV2Json(body: String, indexUrl: String): Parsed {
        val root = TachiyomiRuntime.json.parseToJsonElement(body).jsonObject
        // A bare ExtensionList file ({"extensions": [...]}) is accepted too.
        val list = (root["extensionList"] as? JsonObject)?.get("extensions") as? JsonArray
            ?: root["extensions"] as? JsonArray
            ?: JsonArray(emptyList())
        val entries = list.mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            val res = e["resources"] as? JsonObject
            val sources = (e["sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            entry(
                name = e.str("name").orEmpty(),
                pkg = e.str("packageName").orEmpty(),
                apkUrl = res?.str("apkUrl"),
                iconUrl = res?.str("iconUrl"),
                lib = e.str("extensionLib"),
                code = e.str("versionCode")?.toLongOrNull() ?: 0,
                version = e.str("versionName").orEmpty(),
                warning = warningOf(e["contentWarning"]),
                langs = sources.mapNotNull { it.str("language") },
                indexUrl = indexUrl,
            )
        }
        return Parsed(entries, Meta(root.str("name").orEmpty(), indexUrl, root.str("signingKey")?.let(::cleanFingerprint)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseV2Proto(bytes: ByteArray, indexUrl: String): Parsed {
        val idx = ProtoBuf.decodeFromByteArray<PbIndex>(bytes)
        val entries = idx.extensionList?.extensions.orEmpty().mapNotNull { e ->
            entry(
                name = e.name, pkg = e.packageName, apkUrl = e.resources?.apkUrl, iconUrl = e.resources?.iconUrl,
                lib = e.extensionLib, code = e.versionCode, version = e.versionName,
                warning = e.contentWarning, langs = e.sources.map { it.language }, indexUrl = indexUrl,
            )
        }
        return Parsed(entries, Meta(idx.name, indexUrl, idx.signingKey.let(::cleanFingerprint).ifBlank { null }))
    }

    private fun entry(
        name: String, pkg: String, apkUrl: String?, iconUrl: String?, lib: String?, code: Long, version: String,
        warning: Int, langs: List<String>, indexUrl: String,
    ): TachiyomiExtensions.IndexEntry? {
        if (pkg.isBlank() || apkUrl.isNullOrBlank()) return null
        val distinct = langs.filter { it.isNotBlank() }.distinct()
        return TachiyomiExtensions.IndexEntry(
            name = name.ifBlank { pkg.substringAfterLast('.') },
            pkg = pkg,
            apk = apkUrl.substringAfterLast('/'),
            lang = if (distinct.size == 1) distinct[0] else if (distinct.isEmpty()) "" else "all",
            code = code,
            version = version,
            nsfw = if (warning == WARNING_NSFW) 1 else 0,
            apkUrl = resolve(indexUrl, apkUrl),
            iconUrl = iconUrl?.takeIf { it.isNotBlank() }?.let { resolve(indexUrl, it) },
            lib = lib,
        )
    }

    /** ContentWarning in proto numbering: 1 safe, 2 mixed, 3 NSFW. JSON may carry the name or the number. */
    const val WARNING_SAFE = 1
    const val WARNING_MIXED = 2
    const val WARNING_NSFW = 3

    private fun warningOf(el: JsonElement?): Int {
        val p = el as? JsonPrimitive ?: return 0
        p.contentOrNull?.toIntOrNull()?.let { return it }
        return when (p.contentOrNull?.uppercase()) {
            "CONTENT_WARNING_SAFE" -> WARNING_SAFE
            "CONTENT_WARNING_MIXED" -> WARNING_MIXED
            "CONTENT_WARNING_NSFW" -> WARNING_NSFW
            else -> 0
        }
    }

    // endregion

    fun cleanFingerprint(raw: String): String = raw.replace(":", "").replace(" ", "").lowercase()

    fun resolve(base: String, ref: String): String = runCatching { URI(base).resolve(ref.trim()).toString() }.getOrDefault(ref)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    // region protobuf schema (index.proto); unknown and future fields are skipped by the decoder.

    @Serializable
    internal class PbIndex(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val badgeLabel: String = "",
        @ProtoNumber(3) val signingKey: String = "",
        @ProtoNumber(101) val extensionList: PbExtensionList? = null,
        @ProtoNumber(102) val extensionListUrl: String? = null,
    )

    @Serializable
    internal class PbExtensionList(@ProtoNumber(1) val extensions: List<PbExtension> = emptyList())

    @Serializable
    internal class PbExtension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: PbResources? = null,
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: Int = 0,
        @ProtoNumber(8) val sources: List<PbSource> = emptyList(),
    )

    @Serializable
    internal class PbResources(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    internal class PbSource(
        @ProtoNumber(1) val id: Long = 0,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
    )

    // endregion
}
