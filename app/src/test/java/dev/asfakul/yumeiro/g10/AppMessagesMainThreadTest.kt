/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.components.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import kotlin.concurrent.thread

/**
 * FINAL-42 (run 44669673771, tv-api34, RetryActionsDeviceTest c_mihonRepoRetry): the Tachiyomi
 * "add repository" job called AppMessages.error from a worker thread (DefaultDispatcher-worker-1).
 * The state write resumed the message host's collector on that worker, which ran a Compose frame
 * off the main thread (ViewRootImpl$CalledFromWrongThreadException "Error was captured in
 * composition"); the slot table was left broken and the process crashed when the screen was
 * destroyed (ArrayIndexOutOfBoundsException in SlotWriter.moveSlotGapTo).
 * Old code: the collector runs on the posting worker thread. New code: on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppMessagesMainThreadTest {
    @Before fun host() { AppMessages.hosts = 1; AppMessages.current.value?.let { AppMessages.dismiss(it.id) } }
    @After fun noHost() { AppMessages.current.value?.let { AppMessages.dismiss(it.id) }; AppMessages.hosts = 0 }

    @Test fun aMessageFromAWorkerThreadReachesItsObserversOnTheMainThread() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mainThread = Looper.getMainLooper().thread
        val seen = Collections.synchronizedList(mutableListOf<String>())
        // Dispatchers.Unconfined resumes the collector on whichever thread wrote the state, as the
        // Compose test frame clock did in the failing run.
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            AppMessages.current.drop(1).collect { m -> if (m != null) seen += "${Thread.currentThread().name}|${m.text}" }
        }
        thread(name = "worker-final42") { AppMessages.error(ctx, "Couldn't add the repository", "Retry") {} }.join()
        shadowOf(Looper.getMainLooper()).idle()
        job.cancel()
        assertTrue("FINAL-42: the message never arrived: $seen", seen.any { "Couldn't add the repository" in it })
        assertTrue("FINAL-42: the message state changed off the main thread: $seen", seen.all { it.startsWith(mainThread.name + "|") })
    }
}
