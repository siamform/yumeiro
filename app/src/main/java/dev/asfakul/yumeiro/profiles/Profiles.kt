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

    data class Profile(
        val id: String,
        val name: String,
        val tone: AccentTone,
        internal val pinHash: String? = null,
        internal val salt: String? = null,
        /** Unlock with the phone's fingerprint / face / screen lock. */
        val biometric: Boolean = false,
    ) {
        val hasPin: Boolean get() = pinHash != null
        val locked: Boolean get() = pinHash != null || biometric
        val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    }

    enum class PinCheck { OK, WRONG, LOCKED_OUT, NO_PIN }

    private val _all = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _all.asStateFlow()
    private val _current = MutableStateFlow(DEFAULT)
    val currentFlow: StateFlow<String> = _current.asStateFlow()
    @Volatile private var loaded = false
    private val fails = HashMap<String, Pair<Int, Long>>() // id -> (wrong tries, locked until)

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
        synchronized(fails) { fails.remove(id) }
        return true
    }

    fun checkPin(context: Context, id: String, pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        val p = find(context, id) ?: return PinCheck.WRONG
        val stored = p.pinHash ?: return PinCheck.NO_PIN
        synchronized(fails) {
            val (n, until) = fails[id] ?: (0 to 0L)
            if (until > now) return PinCheck.LOCKED_OUT
            val salt = runCatching { Base64.decode(p.salt, Base64.NO_WRAP) }.getOrNull() ?: return PinCheck.WRONG
            val ok = MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
            if (ok) { fails.remove(id); return PinCheck.OK }
            val count = if (until in 1..now) 1 else n + 1 // a finished lockout starts a new count
            fails[id] = if (count >= MAX_FAILS) 0 to now + LOCKOUT_MS else count to 0L
            return PinCheck.WRONG
        }
    }

    /** Milliseconds left in a lockout, or 0. */
    fun lockoutLeft(id: String, now: Long = System.currentTimeMillis()): Long =
        synchronized(fails) { ((fails[id]?.second ?: 0L) - now).coerceAtLeast(0L) }

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
        synchronized(fails) { fails.remove(id) }
        return true
    }

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
    val SCOPED_PREFS = listOf("yt_history", "online_settings", "private_vault")

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

    private fun from(o: JSONObject): Profile? {
        val id = o.optString("id").ifBlank { return null }
        return Profile(
            id, o.optString("name").ifBlank { "Profile $id" }, AccentTone.from(o.optString("tone")),
            o.optString("hash").ifBlank { null }, o.optString("salt").ifBlank { null }, o.optBoolean("bio"),
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
        root(context).deleteRecursively()
        unloadForTests()
    }

    @androidx.annotation.VisibleForTesting
    internal fun unloadForTests() { synchronized(this) { loaded = false; _all.value = emptyList(); _current.value = DEFAULT }; synchronized(fails) { fails.clear() } }
}
