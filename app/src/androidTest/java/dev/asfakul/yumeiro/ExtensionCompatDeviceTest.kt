/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.APIHolder
import dev.asfakul.yumeiro.online.ExtensionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Loads popular extensions that need more of CloudStream than its library (screens, settings,
 * accounts, AppCompat). AniKoto used to fail with "needs CloudStream features (AppCompatActivity)".
 */
@RunWith(AndroidJUnit4::class)
class ExtensionCompatDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun popularExtensionsLoad() = runBlocking {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val failures = mutableListOf<String>()
        var loaded = 0
        for ((name, url) in EXTENSIONS) {
            val r = runCatching { ExtensionManager.installFromUrl(ctx, url) }
            r.onSuccess { loaded++; Log.i("MTDeviceTest", "$name loaded: ${APIHolder.allProviders.map { it.name }.takeLast(5)}") }
            r.onFailure { t ->
                // A host that is down is not a compatibility problem; anything else is.
                if (generateSequence(t) { it.cause }.any { it is IOException }) Log.w("MTDeviceTest", "$name: download failed: $t")
                else { failures += "$name: ${t.message}"; Log.e("MTDeviceTest", "$name failed", t) }
            }
        }
        assertTrue("extensions failed to load: $failures", failures.isEmpty())
        assertTrue("nothing could be downloaded", loaded > 0)
    }

    companion object {
        val EXTENSIONS = listOf(
            "AniKoto" to "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/AniKoto.cs3",
            "MovieBox" to "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/MovieBoxProvider.cs3",
            "StreamPlay" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/StreamPlay.cs3",
            "Ultima" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/Ultima.cs3",
            "Anichi" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/Anichi.cs3",
            "CineStream" to "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CineStream.cs3",
            "AnimeKai" to "https://raw.githubusercontent.com/JoeTinnySpace/Cloudstream_Kerala/builds/AnimeKai.cs3",
            "Youtube" to "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/Youtube.cs3",
        )
    }
}
