/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Xml
import dev.asfakul.yumeiro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore, version 2 (Round 12, rebuilt from scratch).
 *
 * One `.zip` holds a manifest, every setting as typed values (`prefs.json`), and the data files
 * (history, My List, playlists, sources, repositories, manga progress, lyrics, your background
 * photo). Extensions travel as a list of where they came from, so a restore downloads them again
 * from their own repositories; their files are inside only when you ask for it.
 *
 * Why restore is staged: the old restore copied preference files while the app was running, and
 * the settings already in memory could write themselves back over the restored ones. Now the
 * backup is unpacked into a waiting folder, the app restarts, and [PendingRestore] applies it in
 * `Application.onCreate` before any screen, extension or setting has loaded.
 *
 * Never included: the updater state, account tokens (sealed with this phone's Keystore, they
 * would not open elsewhere), push registration, the app lock and downloaded videos.
 */
object Backup {
    const val FORMAT = 2
    internal const val MANIFEST = "yumeiro-backup.json"
    internal const val PREFS_JSON = "prefs.json"

    /** What a backup can carry; each can be ticked on or off when making or restoring one. */
    enum class Section(val label: String, val hint: String) {
        SETTINGS("Settings", "Player, appearance, network, notifications and every other switch"),
        LIBRARY("Library and history", "Continue watching, My List, play counts, manga progress, saved lyrics"),
        SOURCES("Sources", "Extension list, repositories, playlists, home servers, Stremio add-ons"),
        EXTENSION_FILES("Extension files", "The extensions themselves, so they work offline right after restoring"),
        APPEARANCE("Background photo", "Your own photo behind the app"),
    }

    /** Shared preferences that must stay on this device. */
    internal val excludedPrefs = setOf(
        "mt_updater", "online_secrets", "privacy", "WebViewChromiumPrefs", "fcm_push", "push_wake",
        "mt_oauth_guard", "yt_account", "site_logins", "yumeiro_tv_mode", BackupMeta.PREFS,
    )
    private val excludedPrefPrefixes = listOf("com.google.", "androidx.", "WebView", "org.chromium", "_")
    internal fun prefIncluded(name: String) = name !in excludedPrefs && excludedPrefPrefixes.none { name.startsWith(it) }

    /** Library prefs (history-like) restored with LIBRARY instead of SETTINGS. */
    private val libraryPrefs = setOf("play_counts", "search_recent", "yt_history", "manga_reader", "reminders")

    /** Top-level files that are device-specific. */
    private val excludedFiles = setOf("online_downloads.json")
    /** Folders under filesDir that hold user data. */
    internal val dataDirs = listOf("streams", "servers", "manga", "stremio", "js", "lyrics", "extensions", "tachiyomi")
    private const val PHOTO = "backdrop_photo.jpg"

    data class Summary(val entries: Int, val bytes: Long, val createdAt: Long, val appVersion: String)

    /** The section a zip entry belongs to, or null when it isn't backup material. Pure. */
    internal fun sectionOf(name: String): Section? {
        if (name == PREFS_JSON) return Section.SETTINGS
        if (name.startsWith("prefs/")) return Section.SETTINGS // format 1
        if (!name.startsWith("files/")) return null
        val rel = name.removePrefix("files/")
        if (rel == PHOTO) return Section.APPEARANCE
        if ('/' !in rel) return if (rel.endsWith(".json") && rel !in excludedFiles) Section.LIBRARY else null
        val top = rel.substringBefore('/')
        return when {
            isCode(name) -> Section.EXTENSION_FILES
            top == "manga" || top == "lyrics" -> Section.LIBRARY
            top == "tachiyomi" && rel.startsWith("tachiyomi/memo/") -> Section.LIBRARY
            top == "extensions" -> if (rel == "extensions/state.json") Section.SOURCES else null // market caches refill themselves
            top in dataDirs -> Section.SOURCES
            else -> null
        }
    }

    /** Runnable code: CloudStream plugins, Aniyomi / Mihon APKs, JavaScript providers. */
    internal fun isCode(name: String): Boolean {
        val rel = name.removePrefix("files/")
        return rel.startsWith("extensions/plugins/") || (rel.startsWith("extensions/") && rel.endsWith(".cs3")) ||
            (rel.startsWith("tachiyomi/") && rel.endsWith(".ext")) || rel.startsWith("js/")
    }

    private fun isTransient(f: File): Boolean {
        val n = f.name.lowercase()
        return n.startsWith(".") || n.endsWith(".tmp") || n.endsWith(".part") || n.endsWith(".bak") || n.endsWith(".log")
    }

    // region export

    /** Every file that goes into a backup with these [sections], as zip name to file. */
    internal fun collect(context: Context, sections: Set<Section>): List<Pair<String, File>> {
        val files = context.filesDir
        val out = ArrayList<Pair<String, File>>()
        files.listFiles()?.filter { it.isFile && !isTransient(it) }?.forEach { f ->
            val n = "files/${f.name}"
            if (sectionOf(n) in sections) out += n to f
        }
        dataDirs.forEach { d ->
            File(files, d).takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && !isTransient(it) && it.length() in 1..MAX_FILE }?.forEach { f ->
                val n = "files/" + f.relativeTo(files).invariantSeparatorsPath
                if (sectionOf(n) in sections) out += n to f
            }
        }
        return out.sortedBy { it.first }
    }

    /** Typed snapshot of every included preference file. */
    internal fun prefsSnapshot(context: Context, sections: Set<Section>): JSONObject {
        val root = JSONObject()
        prefNames(context).forEach { name ->
            val lib = name in libraryPrefs
            if ((lib && Section.LIBRARY !in sections) || (!lib && Section.SETTINGS !in sections)) return@forEach
            val all = runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).all }.getOrNull() ?: return@forEach
            if (all.isNotEmpty()) root.put(name, PrefCodec.encode(all))
        }
        return root
    }

    private fun prefNames(context: Context): List<String> =
        prefsDir(context).listFiles()?.filter { it.extension == "xml" }?.map { it.nameWithoutExtension }?.filter(::prefIncluded).orEmpty().sorted()

    /**
     * Writes a backup to [out]. [kind] is "manual", "auto" or "safety" (made before a restore or
     * reset). Returns what went in.
     */
    suspend fun export(context: Context, out: OutputStream, sections: Set<Section> = Section.entries.toSet(), kind: String = "manual"): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val files = collect(app, sections)
        val prefs = prefsSnapshot(app, sections)
        var count = 0
        var bytes = 0L
        ZipOutputStream(out.buffered()).use { zip ->
            val manifest = manifest(app, now, kind, sections, files, prefs)
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest.toString(2).toByteArray()); zip.closeEntry()
            if (prefs.length() > 0) { zip.putNextEntry(ZipEntry(PREFS_JSON)); zip.write(prefs.toString().toByteArray()); zip.closeEntry(); count++ }
            files.forEach { (name, f) ->
                runCatching {
                    zip.putNextEntry(ZipEntry(name).apply { time = f.lastModified() })
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++; bytes += f.length()
                }
            }
        }
        Summary(count, bytes, now, BuildConfig.VERSION_NAME)
    }

    /** Old entry point (a picked document). */
    suspend fun export(context: Context, target: Uri, includeExtensions: Boolean): Summary {
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("Couldn't write to that location")
        val sections = Section.entries.toSet() - if (includeExtensions) emptySet() else setOf(Section.EXTENSION_FILES)
        return export(context, out, sections)
    }

    private fun manifest(context: Context, now: Long, kind: String, sections: Set<Section>, files: List<Pair<String, File>>, prefs: JSONObject): JSONObject {
        val counts = JSONObject()
        counts.put("settings", prefs.keys().asSequence().count())
        fun jsonCount(name: String, key: String? = null): Int = runCatching {
            val t = File(context.filesDir, name).takeIf { it.isFile }?.readText() ?: return 0
            val v = if (t.trimStart().startsWith("[")) JSONArray(t) else JSONObject(t).let { o -> key?.let { o.optJSONArray(it) } ?: o.optJSONArray("items") ?: o.optJSONArray("entries") }
            v?.length() ?: 0
        }.getOrDefault(0)
        if (Section.LIBRARY in sections) {
            counts.put("history", jsonCount("online_history.json"))
            counts.put("myList", jsonCount("online_bookmarks.json"))
        }
        val extensions = JSONArray()
        val tachiyomi = JSONArray()
        if (Section.SOURCES in sections) {
            runCatching {
                val st = JSONObject(File(context.filesDir, "extensions/state.json").readText())
                st.optJSONArray("installed")?.let { a ->
                    for (i in 0 until a.length()) a.optJSONObject(i)?.let { e ->
                        extensions.put(JSONObject().put("name", e.optString("name")).put("internalName", e.optString("internalName"))
                            .put("sourceUrl", e.optString("sourceUrl")).put("repoUrl", e.optString("repoUrl")).put("version", e.optInt("version")))
                    }
                }
                counts.put("repositories", st.optJSONArray("repos")?.length() ?: 0)
            }
            File(context.filesDir, "tachiyomi").listFiles()?.filter { it.isDirectory && it.name != "memo" }?.forEach { kindDir ->
                kindDir.listFiles()?.filter { it.name.endsWith(".ext") }?.forEach { tachiyomi.put(JSONObject().put("pkg", it.name.removeSuffix(".ext")).put("kind", kindDir.name)) }
            }
            counts.put("playlists", files.count { it.first.startsWith("files/streams/") })
            counts.put("extensions", extensions.length() + tachiyomi.length())
        }
        return JSONObject().put("format", FORMAT).put("app", BuildConfig.APPLICATION_ID)
            .put("version", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
            .put("createdAt", now).put("kind", kind)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60))
            .put("sections", JSONArray(sections.map { it.name }))
            .put("extensions", extensions).put("tachiyomi", tachiyomi)
            .put("counts", counts)
    }

    // endregion

    // region inspect

    /** Restore limits: a hostile "backup" must not fill the phone (zip bomb). */
    const val MAX_RESTORE_BYTES = 768L * 1024 * 1024
    const val MAX_RESTORE_ENTRIES = 50_000
    private const val MAX_FILE = 64L * 1024 * 1024

    internal fun readCapped(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < max) { val n = input.read(buf, 0, minOf(buf.size, max - out.size())); if (n < 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    /** What's inside a backup, read before restoring. Sections come from the real entries. */
    data class Info(
        val manifest: JSONObject,
        val format: Int,
        val createdAt: Long,
        val version: String,
        val kind: String,
        val device: String,
        val sections: Set<Section>,
        val hasCode: Boolean,
        val extensions: Int,
        val counts: JSONObject,
    )

    fun infoOf(manifest: JSONObject, found: Set<Section>, hasCode: Boolean): Info {
        val format = manifest.optInt("format", 1)
        val counts = manifest.optJSONObject("counts") ?: JSONObject()
        val ext = manifest.optJSONArray("extensions")?.length()?.plus(manifest.optJSONArray("tachiyomi")?.length() ?: 0)
            ?: counts.optInt("extensions")
        return Info(manifest, format, manifest.optLong("createdAt"), manifest.optString("version").take(32),
            manifest.optString("kind", "manual"), manifest.optString("device").take(60), found, hasCode, ext, counts)
    }

    suspend fun inspect(open: () -> InputStream?): Info = withContext(Dispatchers.IO) {
        val input = open() ?: error("Couldn't open that file")
        var manifest: JSONObject? = null
        var hasCode = false
        var entries = 0
        val found = HashSet<Section>()
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { runCatching { zip.nextEntry }.getOrElse { throw IllegalStateException("This file is damaged or isn't a Yumeiro backup") } }.forEach { e ->
                if (++entries > MAX_RESTORE_ENTRIES) error("This backup has too many files")
                if (e.name == MANIFEST) manifest = runCatching { JSONObject(readCapped(zip, 512 * 1024).decodeToString()) }.getOrNull()
                else {
                    sectionOf(e.name)?.let { found += it }
                    if (isCode(e.name)) hasCode = true
                }
            }
        }
        val m = manifest ?: error("This isn't a Yumeiro backup")
        if (m.optString("app").isNotBlank() && !m.optString("app").startsWith("dev.asfakul.yumeiro")) error("This backup is from another app")
        // A backup without extension files still restores the list; extensions download again.
        if (m.optJSONArray("extensions")?.length() ?: 0 > 0) found += Section.SOURCES
        infoOf(m, found, hasCode)
    }

    /** Old entry point kept for callers that only need the manifest. */
    suspend fun inspect(context: Context, source: Uri): JSONObject =
        inspect { context.contentResolver.openInputStream(source) }.let { it.manifest.put("hasCode", it.hasCode) }

    // endregion

    // region restore (staged)

    /**
     * Unpacks a backup into the waiting folder; the app must restart afterwards ([Restart]) and
     * [PendingRestore] applies it. Only [sections] are taken. Extension code only when
     * [EXTENSION_FILES][Section.EXTENSION_FILES] is ticked; otherwise extensions download again.
     */
    suspend fun stage(context: Context, open: () -> InputStream?, sections: Set<Section>): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val info = inspect(open)
        require(info.format in 1..FORMAT) { "This backup was made by a newer Yumeiro. Update the app first." }
        val staging = PendingRestore.dir(app).apply { deleteRecursively(); mkdirs() }
        var count = 0
        var total = 0L
        try {
            val input = open() ?: error("Couldn't open that file")
            val legacyPrefs = JSONObject()
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    if (e.isDirectory || e.name == MANIFEST) return@forEach
                    val section = sectionOf(e.name) ?: return@forEach
                    // Format 1 kept library prefs under prefs/ too: SETTINGS decides for prefs.
                    if (section !in sections && !(e.name.startsWith("prefs/") || e.name == PREFS_JSON)) return@forEach
                    if (e.name == PREFS_JSON) {
                        val bytes = readCapped(zip, 16 * 1024 * 1024); total += bytes.size
                        File(staging, PREFS_JSON).writeBytes(filterPrefs(JSONObject(bytes.decodeToString()), sections).toString().toByteArray())
                        count++; return@forEach
                    }
                    if (e.name.startsWith("prefs/")) {
                        val n = e.name.removePrefix("prefs/")
                        if ('/' in n || !n.endsWith(".xml")) return@forEach
                        val bytes = readCapped(zip, 4 * 1024 * 1024); total += bytes.size
                        runCatching { legacyPrefs.put(n.removeSuffix(".xml"), PrefCodec.encode(PrefCodec.parseXml(bytes.inputStream()))) }
                        count++; return@forEach
                    }
                    val target = safeTarget(staging, e.name) ?: return@forEach
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf); if (n < 0) break
                            total += n
                            if (total > MAX_RESTORE_BYTES) error("This backup is too large to restore")
                            out.write(buf, 0, n)
                        }
                    }
                    count++
                }
            }
            if (legacyPrefs.length() > 0) File(staging, PREFS_JSON).writeText(filterPrefs(legacyPrefs, sections).toString())
            PendingRestore.writePlan(app, PendingRestore.Plan(
                sections = sections, from = info.version, createdAt = info.createdAt,
                redownload = Section.SOURCES in sections && Section.EXTENSION_FILES !in sections,
                tachiyomi = info.manifest.optJSONArray("tachiyomi")?.toString() ?: "[]",
            ))
        } catch (t: Throwable) { staging.deleteRecursively(); throw t }
        count
    }

    /** Keeps only the pref files of the chosen sections, and never the device-bound ones. */
    internal fun filterPrefs(all: JSONObject, sections: Set<Section>): JSONObject {
        val out = JSONObject()
        all.keys().forEach { name ->
            if (!prefIncluded(name) || name.contains('/') || name.contains("..")) return@forEach
            val lib = name in libraryPrefs
            if ((lib && Section.LIBRARY in sections) || (!lib && Section.SETTINGS in sections)) all.optJSONObject(name)?.let { out.put(name, it) }
        }
        return out
    }

    /** Rejects zip-slip paths and anything outside prefs/ and files/. */
    internal fun safeTarget(root: File, name: String): File? {
        if (!(name.startsWith("prefs/") || name.startsWith("files/"))) return null
        if (name.contains("..") || name.contains('\\') || name.startsWith("/") || name.contains(':')) return null
        if (name.startsWith("files/")) {
            val rel = name.removePrefix("files/")
            val top = rel.substringBefore('/')
            val ok = if ('/' !in rel) (rel.endsWith(".json") && rel !in excludedFiles) || rel == PHOTO else top in dataDirs
            if (!ok) return null
        } else if (name.removePrefix("prefs/").let { '/' in it || !it.endsWith(".xml") }) return null
        val f = File(root, name)
        return f.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    // endregion

    internal fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    /** Relaunches the app (see [Restart]). */
    fun restartApp(context: Context) = Restart.now(context)

    fun suggestedName(kind: String = "manual"): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date())
        return when (kind) { "auto" -> "yumeiro-auto-$stamp.zip"; "safety" -> "yumeiro-before-restore-$stamp.zip"; else -> "yumeiro-backup-$stamp.zip" }
    }
}

/** Typed SharedPreferences values to JSON and back (and the old XML files of format 1). */
internal object PrefCodec {
    fun encode(all: Map<String, *>): JSONObject {
        val o = JSONObject()
        all.forEach { (k, v) ->
            val e = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> if (v.isFinite()) JSONObject().put("t", "f").put("v", v.toDouble()) else null
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.filterIsInstance<String>()))
                else -> null
            }
            if (e != null) o.put(k, e)
        }
        return o
    }

    fun decode(o: JSONObject): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        o.keys().forEach { k ->
            val e = o.optJSONObject(k) ?: return@forEach
            val v: Any? = when (e.optString("t")) {
                "s" -> e.optString("v")
                "b" -> e.optBoolean("v")
                "i" -> e.optInt("v")
                "l" -> e.optLong("v")
                "f" -> e.optDouble("v").toFloat()
                "ss" -> e.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                else -> null
            }
            if (v != null) m[k] = v
        }
        return m
    }

    /** Writes [values] as the whole content of [prefs] (replaces), synchronously. */
    fun apply(prefs: SharedPreferences, values: Map<String, Any>): Boolean {
        val ed = prefs.edit().clear()
        values.forEach { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            when (v) {
                is String -> ed.putString(k, v); is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v); is Set<*> -> ed.putStringSet(k, v as Set<String>)
            }
        }
        return ed.commit()
    }

    /** Android's shared_prefs XML (format 1 backups). */
    fun parseXml(input: InputStream): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val p = Xml.newPullParser()
        p.setInput(input, "UTF-8")
        var set: MutableSet<String>? = null
        var setName: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val name = p.getAttributeValue(null, "name")
                val value = p.getAttributeValue(null, "value")
                when (p.name) {
                    "string" -> if (set != null) set.add(p.nextText()) else if (name != null) out[name] = p.nextText()
                    "boolean" -> if (name != null) out[name] = value.toBoolean()
                    "int" -> if (name != null) value?.toIntOrNull()?.let { out[name] = it }
                    "long" -> if (name != null) value?.toLongOrNull()?.let { out[name] = it }
                    "float" -> if (name != null) value?.toFloatOrNull()?.let { out[name] = it }
                    "set" -> { set = LinkedHashSet(); setName = name }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "set") {
                if (setName != null && set != null) out[setName!!] = set!!
                set = null; setName = null
            }
            ev = p.next()
        }
        return out
    }
}
