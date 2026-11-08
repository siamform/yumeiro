/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide "is there internet?" signal, used by the offline banner and online screens. */
object Connectivity {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    @Volatile private var started = false

    fun isOnline(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    /** True while a VPN carries the traffic (its DNS is then the one that matters). */
    fun vpnActive(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    /** Opens the system VPN settings (to turn a VPN on or off), falling back to wireless settings. */
    fun openVpnSettings(c: Context) {
        val intents = listOf(android.provider.Settings.ACTION_VPN_SETTINGS, android.provider.Settings.ACTION_WIRELESS_SETTINGS)
        for (action in intents) {
            val ok = runCatching { c.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    /** Starts listening once for the lifetime of the process. */
    @Synchronized
    fun start(c: Context) {
        if (started) return
        val app = c.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = isOnline(app)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onLost(network: Network) { _online.value = isOnline(app) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
            started = true
        }
    }
}
