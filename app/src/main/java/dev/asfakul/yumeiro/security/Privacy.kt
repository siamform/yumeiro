/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privacy switches: incognito (no watch/read history, no "continue watching") and app lock
 * (fingerprint, face or the device PIN, via the platform BiometricPrompt — no extra library).
 */
object Privacy {
    private const val PREFS = "privacy"

    private val _incognito = MutableStateFlow(false)
    /** While on, players and readers skip writing history and progress. */
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()

    private val _lockEnabled = MutableStateFlow(false)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    /** Seconds in the background before the lock comes back (0 = every time). */
    private val _lockAfter = MutableStateFlow(60)
    val lockAfter: StateFlow<Int> = _lockAfter.asStateFlow()

    private val _hideInRecents = MutableStateFlow(false)
    val hideInRecents: StateFlow<Boolean> = _hideInRecents.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var backgroundAt = 0L
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _incognito.value = p.getBoolean("incognito", false)
        _lockEnabled.value = p.getBoolean("lock", false)
        _lockAfter.value = p.getInt("lock_after", 60)
        _hideInRecents.value = p.getBoolean("hide_recents", false)
        _locked.value = _lockEnabled.value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setIncognito(context: Context, on: Boolean) { _incognito.value = on; prefs(context).edit().putBoolean("incognito", on).apply(); dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context) }
    fun setLockAfter(context: Context, seconds: Int) { _lockAfter.value = seconds; prefs(context).edit().putInt("lock_after", seconds).apply() }
    fun setHideInRecents(activity: Activity, on: Boolean) {
        _hideInRecents.value = on
        prefs(activity).edit().putBoolean("hide_recents", on).apply()
        applyWindow(activity)
    }

    /** Turning the lock on asks for authentication first so nobody locks themselves out by accident. */
    fun setLockEnabled(activity: Activity, on: Boolean, onResult: (Boolean) -> Unit = {}) {
        if (!on) { _lockEnabled.value = false; prefs(activity).edit().putBoolean("lock", false).apply(); dev.asfakul.yumeiro.widget.ContinueWidget.refresh(activity); onResult(true); return }
        if (!canAuthenticate(activity)) { onResult(false); return }
        authenticate(activity, "Turn on app lock") { ok ->
            if (ok) { _lockEnabled.value = true; prefs(activity).edit().putBoolean("lock", true).apply(); dev.asfakul.yumeiro.widget.ContinueWidget.refresh(activity) }
            onResult(ok)
        }
    }

    fun canAuthenticate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS ||
                (context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true)
        } else context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
    }

    fun onStop() { backgroundAt = SystemClock.elapsedRealtime() }

    fun onStart(activity: Activity) {
        load(activity)
        applyWindow(activity)
        if (!_lockEnabled.value) { _locked.value = false; return }
        val away = if (backgroundAt == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - backgroundAt
        if (away >= _lockAfter.value * 1000L) _locked.value = true
    }

    fun unlock(activity: Activity) {
        if (!_locked.value) return
        authenticate(activity, "Unlock Yumeiro") { ok -> if (ok) _locked.value = false }
    }

    private fun applyWindow(activity: Activity) {
        val secure = _hideInRecents.value && _lockEnabled.value
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun authenticate(activity: Activity, title: String, done: (Boolean) -> Unit) {
        val builder = BiometricPrompt.Builder(activity).setTitle(title).setSubtitle("Use your fingerprint, face or screen lock")
        // API 30+: the unlock is bound to a Keystore key that only works right after a strong
        // biometric or the device PIN, so a hooked callback alone can't open the app.
        val cipher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) LockKey.cipher() else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                if (cipher != null) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", activity.mainExecutor) { _, _ -> done(false) }
        }
        val prompt = runCatching { builder.build() }.getOrElse { done(false); return }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                if (cipher == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { done(true); return }
                // Only unlock if the auth-bound key really works now.
                done(result?.cryptoObject?.cipher?.let { LockKey.verify(it) } == true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = done(false)
        }
        runCatching {
            if (cipher != null) prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, callback)
            else prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback)
        }.onFailure { done(false) }
    }
}
