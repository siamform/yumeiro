/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * ST-20 (ADR-014 extension threading): extension code cannot be changed, but where the Android API
 * lets us, its blocking work is moved off the main thread.
 *
 * Device runs 43767144680 (api36 + api33-lowram) named `com.phisher98.donation.DonationManager
 * .recordShown` as most of the app-process StrictMode hits: `SharedPreferences.Editor.commit()` on
 * the main looper. CloudStream plugins get the Activity as their Context, and the Activity hands
 * every preference lookup to [dev.asfakul.yumeiro]'s application context (X2-3). There, a file that
 * is first opened by extension code is wrapped: `commit()` on the main thread becomes `apply()`
 * (same in-memory result at once, the disk write on Android's own writer thread, flushed by the
 * platform before the activity stops). Off the main thread `commit()` is untouched.
 *
 * Yumeiro's own files are never wrapped: ownership is decided once per file name by the first
 * caller outside the platform, and only a class the app's own class loader cannot load (a class
 * from an extension's dex) makes it an extension file.
 */
object ExtensionPrefs {
    private const val TAG = "ExtensionPrefs"
    private val owner = ConcurrentHashMap<String, Boolean>()
    @Volatile var converted = 0; private set

    /** Wraps [prefs] when [name] was first asked for by extension code. Main or any thread. */
    fun wrapIfExtension(name: String?, prefs: SharedPreferences, appLoader: ClassLoader): SharedPreferences {
        if (name == null) return prefs
        val ext = owner.getOrPut(name) {
            val caller = firstCaller(Thread.currentThread().stackTrace.map { it.className })
            val isExt = caller != null && !loadable(caller, appLoader)
            if (isExt) Log.i(TAG, "\"$name\" belongs to extension code ($caller): main-thread commit() → apply()")
            isExt
        }
        return if (ext) MainSafePrefs(prefs) { Looper.myLooper() == Looper.getMainLooper() } else prefs
    }

    private val PLATFORM = listOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "com.android.", "dalvik.", "libcore.", "sun.", "jdk.",
    )

    /** The first frame that is not the platform, this guard, or the Activity routing. Pure → unit test. */
    internal fun firstCaller(frames: List<String>): String? = frames.firstOrNull { c ->
        PLATFORM.none { c.startsWith(it) } &&
            c != "dev.asfakul.yumeiro.online.ExtensionPrefs" && !c.startsWith("dev.asfakul.yumeiro.online.ExtensionPrefs$") &&
            !c.startsWith("com.lagradost.cloudstream3.MainActivity") &&
            !c.startsWith("dev.asfakul.yumeiro.MainActivity")
    }

    internal fun loadable(className: String, loader: ClassLoader): Boolean =
        runCatching { Class.forName(className.substringBefore("$$"), false, loader); true }.getOrDefault(false)

    internal fun noteConverted(file: String?) {
        converted++
        if (converted <= 3) Log.i(TAG, "extension commit() on main ran as apply() ($file)")
    }

    internal fun resetForTest() { owner.clear(); converted = 0 }
}

/** A [SharedPreferences] whose editors turn a main-thread `commit()` into `apply()`. */
internal class MainSafePrefs(
    private val delegate: SharedPreferences,
    private val onMain: () -> Boolean,
) : SharedPreferences by delegate {
    override fun edit(): SharedPreferences.Editor = Editor(delegate.edit())

    private inner class Editor(private val e: SharedPreferences.Editor) : SharedPreferences.Editor by e {
        // Builder calls must return this wrapper, not the delegate, or a chained commit() escapes.
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { e.putString(key, value); return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { e.putStringSet(key, values); return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { e.putInt(key, value); return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { e.putLong(key, value); return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { e.putFloat(key, value); return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { e.putBoolean(key, value); return this }
        override fun remove(key: String?): SharedPreferences.Editor { e.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { e.clear(); return this }
        override fun commit(): Boolean {
            if (!onMain()) return e.commit()
            e.apply()
            ExtensionPrefs.noteConverted(null)
            return true
        }
    }
}
