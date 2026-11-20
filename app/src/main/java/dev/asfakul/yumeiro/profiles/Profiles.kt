package dev.asfakul.yumeiro.profiles

import android.content.Context
import android.util.Base64
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PR-1 (ADR-041): local profiles, each with its own watch history and progress, My List and Favorites,
 * YouTube history, adult-content switch and private downloads. Settings, extensions and the local library
 * are shared. The default profile ("0") reads the files from before profiles, so nothing moves on update;
 * every other profile keeps its files in `filesDir/profiles/<id>/` and its preferences in `<name>@p<id>`.
 *
 * A profile can be locked with a PIN (4–8 digits; stored only as a salted PBKDF2 hash) and/or the
 * phone's biometric / screen lock. Five wrong PINs lock the profile for [LOCKOUT_MS]. Switching profile
 * restarts the app (every store loads the new profile's data; nothing of the old one stays in memory).
 */
object Profiles {
    const val DEFAULT = "0"
    const val PREFS = "profiles"
    const val LOCKOUT_MS = 30_000L
    private const val MAX_FAILS = 5
    private const val ITERATIONS = 12_000
    const val MAX_PROFILES = 8
    /** One-time "restart into this profile" pass. "_" files are never in backups ([Backup.prefIncluded]). */
    const val PASS_PREFS = "_profile_pass"
    /** Wrong-PIN counts and lockouts; kept on disk so killing the app does not reset them. Never backed up. */
    const val GUARD_PREFS = "_profile_guard"
    private const val MAX_LOCKOUT_MS = 60 * 60_000L
    /** Files that are a profile's own (top level for the default profile). */
    val PROFILE_FILES = listOf("online_history.json", "online_bookmarks.json")

    data class Profile(
        val id: String,
        val name: String,
        val tone: AccentTone,
        internal val pinHash: String? = null,
        internal val salt: String? = null,
        /** Unlock with the phone's fingerprint / face / screen lock. */
        val biometric: Boolean = false,
        /** F-19: three security questions (shown), answers kept only as one salted PBKDF2 hash. */
        val recoveryQuestions: List<String> = emptyList(),
        internal val recoveryHash: String? = null,
        internal val recoverySalt: String? = null,
    ) {
        val hasPin: Boolean get() = pinHash != null
        val hasRecovery: Boolean get() = recoveryHash != null && recoveryQuestions.size == RECOVERY_QUESTIONS
        val locked: Boolean get() = pinHash != null || biometric
        val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    }

    enum class PinCheck { OK, WRONG, LOCKED_OUT, NO_PIN }

    private val _all = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _all.asStateFlow()
    private val _current = MutableStateFlow(DEFAULT)
    val currentFlow: StateFlow<String> = _current.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val p = prefs(context)
        val list = runCatching { JSONArray(p.getString("list", "[]")) }.getOrDefault(JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::from) }
        }.distinctBy { it.id }
        val withDefault = if (list.none { it.id == DEFAULT }) listOf(Profile(DEFAULT, "Me", AccentTone.GOLD)) + list else list.sortedBy { it.id != DEFAULT }
        _all.value = withDefault
        _current.value = p.getString("current", DEFAULT)?.takeIf { id -> withDefault.any { it.id == id } } ?: DEFAULT
        loaded = true
    }

    fun all(context: Context): List<Profile> { init(context); return _all.value }
    fun find(context: Context, id: String): Profile? = all(context).firstOrNull { it.id == id }
    fun currentId(context: Context): String { init(context); return _current.value }
    fun current(context: Context): Profile = find(context, currentId(context)) ?: all(context).first()

    /** True when the app should ask "Who's watching?" at start. */
    fun askAtStart(context: Context): Boolean = all(context).size > 1 || current(context).locked

    @Synchronized
    fun add(context: Context, name: String, tone: AccentTone): Profile {
        init(context)
        val p = prefs(context)
        val next = maxOf(p.getInt("next", 1), (_all.value.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1)
        val clean = name.trim().take(24).ifBlank { "Profile $next" }
        val profile = Profile(next.toString(), clean, tone)
        _all.value = _all.value + profile
        save(context) { putInt("next", next + 1) }
        return profile
    }

    @Synchronized
    fun rename(context: Context, id: String, name: String): Boolean {
        val clean = name.trim().take(24)
        if (clean.isEmpty() || find(context, id) == null) return false
        replace(context, id) { it.copy(name = clean) }
        return true
    }

    @Synchronized
    fun setTone(context: Context, id: String, tone: AccentTone): Boolean {
        if (find(context, id) == null) return false
        replace(context, id) { it.copy(tone = tone) }
        return true
    }

    @Synchronized
    fun setBiometric(context: Context, id: String, on: Boolean): Boolean {
        if (find(context, id) == null) return false
        replace(context, id) { it.copy(biometric = on) }
        return true
    }

    /** F-19: how many security questions a profile has. */
    const val RECOVERY_QUESTIONS = 3

    /** Answers compare case-blind with spaces collapsed, so "  Dhaka " matches "dhaka". */
    fun normalizeAnswer(a: String): String = a.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Three different, non-blank questions and three answers of at least 2 characters. */
    fun validRecovery(questions: List<String>, answers: List<String>): Boolean =
        questions.size == RECOVERY_QUESTIONS && answers.size == RECOVERY_QUESTIONS &&
            questions.all { it.isNotBlank() } && questions.map { it.trim().lowercase(java.util.Locale.ROOT) }.toSet().size == RECOVERY_QUESTIONS &&
            answers.all { normalizeAnswer(it).length >= 2 }

    private fun answersKey(answers: List<String>) = answers.joinToString("\u0000") { normalizeAnswer(it) }

    /** F-19: sets (or with empty lists removes) the security questions. False when they are not valid. */
    @Synchronized
    fun setRecovery(context: Context, id: String, questions: List<String>, answers: List<String>): Boolean {
        if (find(context, id) == null) return false
        if (questions.isEmpty() && answers.isEmpty()) {
            replace(context, id) { it.copy(recoveryQuestions = emptyList(), recoveryHash = null, recoverySalt = null) }; return true
        }
        if (!validRecovery(questions, answers)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val h = hash(answersKey(answers), salt)
        replace(context, id) { it.copy(recoveryQuestions = questions.map { q -> q.trim() }, recoveryHash = h, recoverySalt = Base64.encodeToString(salt, Base64.NO_WRAP)) }
        return true
    }

    /**
     * F-19: "Forgot PIN" with the security questions. All three answers must match. Wrong answers count
     * against the same tries and lockouts as wrong PINs. OK removes the PIN; the profile keeps its data.
     */
    @Synchronized
    fun recoverWithAnswers(context: Context, id: String, answers: List<String>, now: Long = System.currentTimeMillis()): PinCheck {
        val p = find(context, id) ?: return PinCheck.WRONG
        if (!p.hasRecovery) return PinCheck.NO_PIN
        val g = guard(context)
        if (g.getLong("u_$id", 0L) > now) return PinCheck.LOCKED_OUT
        val salt = runCatching { Base64.decode(p.recoverySalt, Base64.NO_WRAP) }.getOrNull() ?: return PinCheck.WRONG
        if (answers.size == RECOVERY_QUESTIONS && MessageDigest.isEqual(hash(answersKey(answers), salt).toByteArray(), p.recoveryHash!!.toByteArray())) {
            replace(context, id) { it.copy(pinHash = null, salt = null) }
            clearGuard(context, id)
            return PinCheck.OK
        }
        failed(g, id, now)
        return PinCheck.WRONG
    }

    fun validPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    /** Sets (or with null / "" removes) the PIN. False for an invalid PIN or an unknown profile. */
    @Synchronized
    fun setPin(context: Context, id: String, pin: String?): Boolean {
        if (find(context, id) == null) return false
        if (pin.isNullOrEmpty()) { replace(context, id) { it.copy(pinHash = null, salt = null) }; return true }
        if (!validPin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        replace(context, id) { it.copy(pinHash = hash, salt = Base64.encodeToString(salt, Base64.NO_WRAP)) }
        clearGuard(context, id)
        return true
    }

    private fun guard(context: Context) = context.applicationContext.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
    private fun clearGuard(context: Context, id: String) { guard(context).edit().remove("n_$id").remove("u_$id").remove("k_$id").commit() }

    /**
     * Checks a PIN. Five wrong tries lock the profile for [LOCKOUT_MS], each further lockout twice as
     * long (up to an hour). The counts are on disk, so killing the app does not give new tries.
     */
    @Synchronized
    fun checkPin(context: Context, id: String, pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        val p = find(context, id) ?: return PinCheck.WRONG
        val stored = p.pinHash ?: return PinCheck.NO_PIN
        val g = guard(context)
        if (g.getLong("u_$id", 0L) > now) return PinCheck.LOCKED_OUT
        val salt = runCatching { Base64.decode(p.salt, Base64.NO_WRAP) }.getOrNull() ?: return PinCheck.WRONG
        if (MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())) { clearGuard(context, id); return PinCheck.OK }
        failed(g, id, now)
        return PinCheck.WRONG
    }

    private fun failed(g: android.content.SharedPreferences, id: String, now: Long) {
        val n = g.getInt("n_$id", 0) + 1
        if (n >= MAX_FAILS) {
            val k = g.getInt("k_$id", 0)
            val ms = (LOCKOUT_MS shl k.coerceAtMost(7)).coerceAtMost(MAX_LOCKOUT_MS)
            g.edit().putInt("n_$id", 0).putLong("u_$id", now + ms).putInt("k_$id", k + 1).commit()
        } else g.edit().putInt("n_$id", n).commit()
    }

    /** Milliseconds left in a lockout, or 0. */
    fun lockoutLeft(context: Context, id: String, now: Long = System.currentTimeMillis()): Long =
        (guard(context).getLong("u_$id", 0L) - now).coerceAtLeast(0L)

    /** Makes [id] current. Saved with commit(): the caller restarts the app right after. */
    @Synchronized
    fun select(context: Context, id: String): Boolean {
        if (find(context, id) == null) return false
        _current.value = id
        prefs(context).edit().putString("current", id).commit()
        return true
    }

    /** Deletes a profile and everything that is only its own. The default profile stays. */
    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        if (id == DEFAULT || find(context, id) == null) return false
        runCatching { dev.asfakul.yumeiro.online.download.PrivateDownloads.removeProfile(context, id) }
        File(root(context), id).deleteRecursively()
        SCOPED_PREFS.forEach { name -> runCatching { context.applicationContext.deleteSharedPreferences("$name@p$id") } }
        _all.value = _all.value.filterNot { it.id == id }
        if (_current.value == id) _current.value = DEFAULT
        save(context) { putString("current", _current.value) }
        clearGuard(context, id)
        return true
    }

    // region guards (ADR-041 security review)

    /** The phone's main (default) profile is locked and someone else's profile is open. */
    fun needsMainUnlock(context: Context): Boolean = currentId(context) != DEFAULT && find(context, DEFAULT)?.locked == true

    /** The home-screen widget shows nothing of a locked profile. */
    fun hideFromWidget(context: Context): Boolean = current(context).locked

    fun passRestart(context: Context, id: String) {
        context.applicationContext.getSharedPreferences(PASS_PREFS, Context.MODE_PRIVATE).edit().putString("id", id).putLong("at", System.currentTimeMillis()).commit()
    }

    /** True once, right after [passRestart] into the current profile (and within a minute). */
    fun consumePass(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PASS_PREFS, Context.MODE_PRIVATE)
        val id = p.getString("id", null) ?: return false
        val at = p.getLong("at", 0L)
        p.edit().clear().commit()
        return id == currentId(context) && System.currentTimeMillis() - at in 0..60_000L
    }

    /** Every profile as it is now, for [keepLocksAfterRestore]. */
    fun lockSnapshot(context: Context): String = JSONArray().apply { all(context).forEach { put(json(it)) } }.toString()

    /**
     * After a restore: a backup may hold an older profile list (without a PIN set later, or without a
     * profile added later). Local locks always win, and local profiles missing from the backup stay,
     * so a restore can never open a locked profile.
     */
    @Synchronized
    fun keepLocksAfterRestore(context: Context, before: String) {
        val local = runCatching { JSONArray(before) }.getOrNull()?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::from) } }.orEmpty()
        unload()
        init(context)
        val restored = _all.value
        val merged = restored.map { r ->
            val l = local.firstOrNull { it.id == r.id } ?: return@map r
            // F-19: the local security questions win too, so a backup can't bring answers someone else knows.
            if (l.locked) r.copy(pinHash = l.pinHash, salt = l.salt, biometric = l.biometric,
                recoveryQuestions = l.recoveryQuestions, recoveryHash = l.recoveryHash, recoverySalt = l.recoverySalt) else r
        } + local.filter { l -> restored.none { it.id == l.id } }
        _all.value = merged
        save(context)
    }

    /**
     * "Forgot PIN": like a phone's safe folder, the lock can only go together with what it protects.
     * Another profile is deleted; the default profile (which can not be deleted) loses its lock, its
     * lists, history, YouTube history and private downloads, and keeps everything shared.
     */
    @Synchronized
    fun resetForgotten(context: Context, id: String): Boolean {
        if (find(context, id) == null) return false
        if (id != DEFAULT) return remove(context, id)
        val app = context.applicationContext
        PROFILE_FILES.forEach { File(app.filesDir, it).delete() }
        app.getSharedPreferences("yt_history", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("online_settings", Context.MODE_PRIVATE).edit().putBoolean("adult", false).commit()
        runCatching { dev.asfakul.yumeiro.online.download.PrivateDownloads.removeProfile(context, DEFAULT) }
        replace(context, DEFAULT) { it.copy(pinHash = null, salt = null, biometric = false) }
        clearGuard(context, DEFAULT)
        return true
    }

    /** Profiles whose data stays out of a backup made now: locked ones other than the open one. */
    fun lockedOthers(context: Context): Set<String> = all(context).filter { it.locked && it.id != currentId(context) }.map { it.id }.toSet()

    /** A backup zip entry (files/...) that belongs to a locked profile other than the open one. */
    fun hiddenFromBackup(context: Context, zipName: String, others: Set<String> = lockedOthers(context)): Boolean {
        if (others.isEmpty()) return false
        val rel = zipName.removePrefix("files/")
        if (rel.startsWith("profiles/")) return rel.removePrefix("profiles/").substringBefore('/') in others
        return DEFAULT in others && rel in PROFILE_FILES
    }

    /** A preference file that belongs to a locked profile other than the open one. */
    fun prefsHiddenFromBackup(context: Context, name: String, others: Set<String> = lockedOthers(context)): Boolean {
        if (others.isEmpty()) return false
        val at = name.lastIndexOf("@p")
        if (at > 0) return name.substring(at + 2) in others
        return DEFAULT in others && (name == "yt_history" || name == "private_vault")
    }

    // endregion

    /** A per-profile file: the old path for the default profile, `filesDir/profiles/<id>/<name>` otherwise. */
    fun file(context: Context, name: String): File {
        val id = currentId(context)
        if (id == DEFAULT) return File(context.applicationContext.filesDir, name)
        return File(File(root(context), id).apply { mkdirs() }, name)
    }

    /** A per-profile SharedPreferences name (unchanged for the default profile). */
    fun prefsName(context: Context, name: String): String {
        val id = currentId(context)
        return if (id == DEFAULT) name else "$name@p$id"
    }

    /** Preference files that are kept per profile (removed with the profile). */
    val SCOPED_PREFS = listOf("yt_history", "online_settings", "private_vault", "pinned_extensions", "pinned_playlists")

    private fun root(context: Context) = File(context.applicationContext.filesDir, "profiles")

    private fun replace(context: Context, id: String, change: (Profile) -> Profile) {
        _all.value = _all.value.map { if (it.id == id) change(it) else it }
        save(context)
    }

    private fun save(context: Context, extra: android.content.SharedPreferences.Editor.() -> Unit = {}) {
        val a = JSONArray().apply { _all.value.forEach { put(json(it)) } }
        prefs(context).edit().putString("list", a.toString()).apply(extra).commit()
    }

    private fun json(p: Profile) = JSONObject().put("id", p.id).put("name", p.name).put("tone", p.tone.name)
        .put("bio", p.biometric).apply { if (p.pinHash != null) { put("hash", p.pinHash); put("salt", p.salt) } }
        .apply { if (p.recoveryHash != null) { put("rq", JSONArray(p.recoveryQuestions)); put("rh", p.recoveryHash); put("rs", p.recoverySalt) } }

    private fun from(o: JSONObject): Profile? {
        val id = o.optString("id").ifBlank { return null }
        return Profile(
            id, o.optString("name").ifBlank { "Profile $id" }, AccentTone.from(o.optString("tone")),
            o.optString("hash").ifBlank { null }, o.optString("salt").ifBlank { null }, o.optBoolean("bio"),
            recoveryQuestions = o.optJSONArray("rq")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            recoveryHash = o.optString("rh").ifBlank { null }, recoverySalt = o.optString("rs").ifBlank { null },
        )
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().commit()
        context.applicationContext.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.applicationContext.getSharedPreferences(PASS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        root(context).deleteRecursively()
        unloadForTests()
    }

    @androidx.annotation.VisibleForTesting
    internal fun unloadForTests() = unload()

    @Synchronized
    private fun unload() { loaded = false; _all.value = emptyList(); _current.value = DEFAULT }

    /** "45 s" or "3 min" for a lockout message. */
    fun waitText(ms: Long): String = if (ms < 60_000L) "${ms / 1000 + 1} s" else "${(ms + 59_999L) / 60_000L} min"
}
