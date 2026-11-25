/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import com.lagradost.cloudstream3.MainAPI

/**
 * EXT-SETUP-1 (A3): "2 extensions didn't load: Jellyfin · Ultima". Both load fine; they show nothing until they are
 * set up in their own settings (Jellyfin: a server address and sign-in; Ultima: which sections of other extensions to
 * show). Their home then fails or comes back empty, and Yumeiro called that a load failure with a Retry that can never
 * work. Now an extension that has its own settings and a home with no rows, a blank site address, or an error that asks
 * for setup is shown as "needs setup" with a Set up button that opens those settings.
 */
object ExtensionSetup {
    private val SETUP_WORDS = listOf(
        "login", "log in", "sign in", "signin", "not configured", "configure", "setup", "set up", "server url", "no server",
        "credentials", "api key", "apikey", "access token", "no sections", "enable sections", "open settings",
    )

    /**
     * Pure → unit-tested. [hasSettings]: the extension registered its own settings screen. [mainUrl]: its site address
     * now. [rowsDeclared]: its declared home rows. [error]: the home's error text (null when it came back empty).
     */
    fun needsSetup(hasSettings: Boolean, mainUrl: String?, rowsDeclared: Int, error: String?): Boolean {
        if (!hasSettings) return false
        val url = mainUrl?.trim().orEmpty()
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://")) || url.removePrefix("https://").removePrefix("http://").trim('/').isEmpty()) return true
        if (rowsDeclared == 0) return true
        val e = error?.lowercase() ?: return true // empty home from an extension with settings: almost always unset
        return SETUP_WORDS.any { it in e } || "unauthorized" in e || "401" in e
    }

    fun hasSettings(api: MainAPI): Boolean = openerFor(api) != null

    private fun openerFor(api: MainAPI): ((Context) -> Unit)? {
        val ext = runCatching { ExtensionManager.extensionOf(api) }.getOrNull() ?: return null
        val p = ExtensionManager.pluginOf(ext.key) as? com.lagradost.cloudstream3.plugins.Plugin ?: return null
        return p.openSettings
    }

    fun needsSetup(api: MainAPI, error: Throwable?): Boolean = runCatching {
        needsSetup(hasSettings(api), api.mainUrl, api.mainPage.size, error?.let { it.message ?: it.javaClass.simpleName })
    }.getOrDefault(false)

    /** Opens the extension's own settings; false when it has none or they crashed. */
    fun open(context: Context, api: MainAPI): Boolean {
        val open = openerFor(api) ?: return false
        val host = com.lagradost.cloudstream3.CommonActivity.activity ?: context
        return runCatching { open(host) }.isSuccess
    }

    /** Pure → unit-tested. */
    fun message(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> "${names[0]} needs setting up before it shows anything"
        else -> "${names.size} extensions need setting up: ${names.joinToString(" · ")}".take(160)
    }
}
