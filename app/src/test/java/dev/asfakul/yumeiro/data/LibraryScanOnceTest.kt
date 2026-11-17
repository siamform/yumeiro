/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * G13 M1-1: a cold start with media access asked for the library scan two or three times at once
 * (MainViewModel.init + the startup permission check), and each request read all of MediaStore.
 * Requests with the same access must share one scan; a request after access changed runs again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScanOnceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun grantAll() = shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)

    @Test fun requestsDuringARunningScanShareIt() = runBlocking {
        grantAll()
        val repo = OfflineMediaRepository(app)
        val scans = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        repo.scanProbe = { scans.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS) }
        val first = async(Dispatchers.Default) { repo.scanDeviceMedia() }
        assertTrue("first scan never started", entered.await(5, TimeUnit.SECONDS))
        // The second and third startup requests arrive while the first scan is still reading.
        val others = listOf(async(Dispatchers.Default) { repo.scanDeviceMedia() }, async(Dispatchers.Default) { repo.scanDeviceMedia() })
        Thread.sleep(300)
        release.countDown()
        (others + first).awaitAll()
        assertEquals("one MediaStore scan for three requests with the same access", 1, scans.get())
        // Once it is done, a later request (pull to refresh) scans again.
        repo.scanDeviceMedia()
        assertEquals(2, scans.get())
    }

    @Test fun aRequestAfterAccessChangedScansAgain() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        val repo = OfflineMediaRepository(app)
        val scans = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        repo.scanProbe = { scans.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS) }
        val before = async(Dispatchers.Default) { repo.scanDeviceMedia() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        grantAll() // the user allowed access while the first (empty) scan ran
        val after = async(Dispatchers.Default) { repo.scanDeviceMedia() }
        Thread.sleep(300)
        release.countDown()
        listOf(before, after).awaitAll()
        assertEquals("the scan after the grant must not reuse the scan without access", 2, scans.get())
    }
}
