package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** X1-4: a cancelled sign-in poll must not run its failure branch (it showed "cancelled" as an error). */
class SuspendCatchingTest {
    @Test fun successAndFailurePassThrough() {
        assertEquals(3, runCancellable { 3 }.getOrNull())
        assertTrue(runCancellable { error("x") }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun timeoutStaysAFailure() = runBlocking {
        val r = runCancellable { withTimeout(10) { delay(1_000) } }
        assertTrue(r.isFailure)
    }

    @Test fun cancellationSkipsTheFailureBranch() = runBlocking {
        var failureShown = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            runCancellable { started.complete(Unit); delay(10_000) }.onFailure { failureShown = true }
        }
        started.await(); yield()
        job.cancel(); job.join()
        assertFalse("a cancelled poll must not report an error", failureShown)
    }

    @Test fun plainRunCatchingWouldShowTheError() = runBlocking {
        // Documents the bug the helper fixes.
        var failure: Throwable? = null
        val started = CompletableDeferred<Unit>()
        val job = launch { runCatching { started.complete(Unit); delay(10_000) }.onFailure { failure = it } }
        started.await(); yield()
        job.cancel(); job.join()
        assertTrue(failure is CancellationException)
    }

    @Test fun signInPollsUseTheHelper() {
        val src = File("src/main/java/dev/asfakul/yumeiro/ui/screens/settings/PrivacyTrackingBackup.kt").readText()
        for (call in listOf("Trakt.awaitToken", "Simkl.awaitToken", "LastFm.awaitSession", "Trakt.deviceCode", "Simkl.deviceCode", "LastFm.requestToken")) {
            val line = src.lines().first { call in it }
            assertTrue("$call must use runCancellable", "runCancellable" in line)
        }
    }
}
