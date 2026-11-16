/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * G10 network signal (device run 43891331959, api36): when the default network is lost the signal must
 * go offline even if `activeNetwork` still reports the old network with INTERNET (the race the old
 * onLost lost: it re-read activeNetwork and stayed online, and no further callback came).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectivityLostTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After fun tearDown() { Connectivity.overrideForTests(true) }

    @Test fun lostDefaultNetworkIsOfflineEvenIfActiveNetworkLags() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val stale = cm.activeNetwork ?: ShadowNetwork.newInstance(7)
        val caps = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
        shadowOf(cm).setNetworkCapabilities(stale, caps)
        assertTrue("precondition: the lagging activeNetwork still says INTERNET", Connectivity.isOnline(app))
        val cb = Connectivity.callback()
        cb.onAvailable(stale)
        assertTrue(Connectivity.online.value)
        cb.onLost(stale)
        assertFalse("onLost of the default network must report offline", Connectivity.online.value)
        cb.onAvailable(ShadowNetwork.newInstance(8))
        assertTrue("the next default network brings it back", Connectivity.online.value)
    }

    @Test fun capabilitiesWithoutInternetAreOffline() {
        val cb = Connectivity.callback()
        val n = ShadowNetwork.newInstance(9)
        cb.onAvailable(n)
        cb.onCapabilitiesChanged(n, ShadowNetworkCapabilities.newInstance())
        assertFalse(Connectivity.online.value)
        cb.onCapabilitiesChanged(n, ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) })
        assertTrue(Connectivity.online.value)
    }
}
