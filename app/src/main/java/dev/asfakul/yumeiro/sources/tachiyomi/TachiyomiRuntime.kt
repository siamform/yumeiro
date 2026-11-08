/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory

/**
 * The dependency graph Aniyomi / Mihon extensions expect: they call `Injekt.get<NetworkHelper>()`,
 * `Injekt.get<Application>()` and `Injekt.get<Json>()` from their own code.
 */
object TachiyomiRuntime {
    @Volatile private var ready = false

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext as Application
            Injekt.addSingleton(app)
            Injekt.addSingletonFactory { NetworkHelper(app) }
            Injekt.addSingletonFactory { json }
            Injekt.addSingletonFactory { JavaScriptEngine(app) }
            ready = true
        }
    }
}
