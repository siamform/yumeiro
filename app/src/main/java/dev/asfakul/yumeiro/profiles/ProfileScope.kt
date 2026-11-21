/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.profiles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * F-19-EXT: which profiles have an installed extension or an added repository. The files stay shared
 * (one download, one update), but each profile only sees, loads and updates its own; removing one in a
 * profile keeps it for the others and deletes the file only when no profile has it any more.
 *
 * Rules (pure, see [Table]):
 * - First use of a store: everything installed then belongs to every profile that exists then (nothing
 *   disappears on update); profiles made later start empty.
 * - A key with no record (for example from a restored backup) belongs to everyone.
 * - Removing a profile removes what only it had (like the rest of its data): such entries are dropped at
 *   the next load and their files deleted then.
 *
 * Stored in filesDir/profile_scope.json (app-wide, written atomically). Flag `f19_profile_extensions`.
 */
object ProfileScope {
    const val EXTENSIONS = "cs_ext"
    const val REPOSITORIES = "cs_repo"
    private const val FILE = "profile_scope.json"

    /** Pure ownership table of one store. Owners per key; [init] = the first-use seed has run. */
    data class Table(val init: Boolean = false, val owners: Map<String, Set<String>> = emptyMap()) {
        /** First use: every key in [keys] belongs to every profile in [profiles]. */
        fun seeded(keys: Collection<String>, profiles: Collection<String>): Table =
            if (init) this else Table(true, owners + keys.filter { it !in owners }.associateWith { profiles.toSet() })

        fun visible(key: String, profile: String): Boolean = owners[key]?.contains(profile) ?: true

        fun claim(key: String, profile: String): Table = copy(owners = owners + (key to (owners[key].orEmpty() + profile)))

        /** [profile] no longer has [key]; [allProfiles] materialises a key that had no record (= everyone). */
        fun release(key: String, profile: String, allProfiles: Collection<String>): Table {
            val now = (owners[key] ?: allProfiles.toSet()) - profile
            return copy(owners = owners + (key to now))
        }

        /** True when some profile other than [profile] still has [key] after [release]. */
        fun keptByOthers(key: String): Boolean = owners[key]?.isNotEmpty() ?: true

        fun forget(key: String): Table = copy(owners = owners - key)

        /** [profile] is gone; keys only it had are left with no owner ([orphan]). */
        fun dropProfile(profile: String): Table = copy(owners = owners.mapValues { (_, o) -> o - profile })

        /** Nobody has [key] any more (its profile was removed): drop the entry and its file. */
        fun orphan(key: String): Boolean = owners[key]?.isEmpty() == true

        fun toJson(): JSONObject = JSONObject().put("init", init).put("owners", JSONObject().apply {
            owners.forEach { (k, v) -> put(k, JSONArray(v.sorted())) }
        })

        companion object {
            fun fromJson(o: JSONObject?): Table {
                if (o == null) return Table()
                val owners = o.optJSONObject("owners")
                val map = HashMap<String, Set<String>>()
                owners?.keys()?.forEach { k -> owners.optJSONArray(k)?.let { a -> map[k] = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() } }
                return Table(o.optBoolean("init"), map)
            }
        }
    }

    private val tables = HashMap<String, Table>()
    @Volatile private var loaded = false

    fun enabled(): Boolean = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.PROFILE_EXTENSIONS)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val o = runCatching { file(context).takeIf { it.isFile }?.readText()?.let(::JSONObject) }.getOrNull()
        tables.clear()
        o?.keys()?.forEach { k -> tables[k] = Table.fromJson(o.optJSONObject(k)) }
        loaded = true
    }

    @Synchronized
    private fun save(context: Context) {
        val o = JSONObject(); tables.forEach { (k, t) -> o.put(k, t.toJson()) }
        val f = file(context); val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    @Synchronized
    private fun update(context: Context, store: String, change: (Table) -> Table): Table {
        load(context)
        val next = change(tables[store] ?: Table())
        if (next != tables[store]) { tables[store] = next; save(context) }
        return next
    }

    private fun profileIds(context: Context) = Profiles.all(context).map { it.id }

    /**
     * Splits [items] into the current profile's (first) and the other profiles' (second); items of a
     * removed profile are in neither (the caller drops them, so their files are cleaned up). Seeds the
     * store on first use.
     */
    fun <T> split(context: Context, store: String, items: List<T>, key: (T) -> String): Pair<List<T>, List<T>> {
        if (!enabled()) return items to emptyList()
        val me = Profiles.currentId(context)
        var t = update(context, store) { it.seeded(items.map(key), profileIds(context)) }
        val gone = items.map(key).filter { t.orphan(it) }
        if (gone.isNotEmpty()) t = update(context, store) { tb -> gone.fold(tb) { acc, k -> acc.forget(k) } }
        return items.filter { key(it) !in gone }.partition { t.visible(key(it), me) }
    }

    fun claim(context: Context, store: String, key: String) {
        if (!enabled()) return
        val me = Profiles.currentId(context)
        update(context, store) { it.claim(key, me) }
    }

    /** The current profile drops [key]. Returns true when another profile still has it (keep the file). */
    fun release(context: Context, store: String, key: String): Boolean {
        if (!enabled()) return false
        val me = Profiles.currentId(context)
        val t = update(context, store) { it.release(key, me, profileIds(context)) }
        val kept = t.keptByOthers(key)
        if (!kept) update(context, store) { it.forget(key) }
        return kept
    }

    /** Called when a profile is deleted: what only it had is dropped at the next load. */
    fun dropProfile(context: Context, id: String) {
        load(context)
        tables.keys.toList().forEach { store -> update(context, store) { it.dropProfile(id) } }
    }

    /** Forget the in-memory copy only (a process restart). */
    internal fun resetMemoryForTests() { synchronized(this) { tables.clear(); loaded = false } }

    internal fun resetForTests(context: Context) { synchronized(this) { tables.clear(); loaded = false }; file(context).delete() }
}
