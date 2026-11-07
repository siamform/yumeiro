package com.lagradost.cloudstream3.plugins

import android.app.Activity
import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.InstalledExtension
import dev.asfakul.yumeiro.online.RemoteExtension
import java.io.File

/** An installed extension (CloudStream-compatible). */
data class PluginData(
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String?,
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") val filePath: String,
    @JsonProperty("version") val version: Int,
) {
    fun toSitePlugin(): SitePlugin = SitePlugin(
        filePath, 1, maxOf(1, version), 1, internalName, internalName, emptyList(), null, null, null, null, null,
        File(filePath).length(), null,
    )
}

/**
 * CloudStream's PluginManager, backed by this app's [ExtensionManager]. Lets "helper" extensions
 * (auto-installers, aggregators) see and manage the installed extensions.
 */
object PluginManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"
    var currentlyLoading: String? = null

    private val ctx: Context? get() = CloudStreamApp.context

    /** Maps file path to loaded plugin. */
    val plugins: Map<String, BasePlugin> get() = ExtensionManager.loadedPlugins()

    private fun data(ext: InstalledExtension, context: Context) = PluginData(
        ext.internalName, ext.sourceUrl.takeIf { !ext.manual }, !ext.manual,
        ExtensionManager.pluginFile(context, ext).absolutePath, ext.version,
    )

    fun getPluginsOnline(): Array<PluginData> {
        val c = ctx ?: return emptyArray()
        return ExtensionManager.state.value.installed.filter { !it.manual }.map { data(it, c) }.toTypedArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        val c = ctx ?: return emptyArray()
        return ExtensionManager.state.value.installed.filter { it.manual }.map { data(it, c) }.toTypedArray()
    }

    fun getPluginSanitizedFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_.\\-]"), "_") + "." + name.hashCode()

    fun getPluginPath(context: Context, internalName: String, repositoryUrl: String): File {
        ExtensionManager.state.value.installed.firstOrNull { it.internalName == internalName && it.repoUrl == repositoryUrl }?.let {
            return ExtensionManager.pluginFile(context, it)
        }
        return File(context.filesDir, "$ONLINE_PLUGINS_FOLDER/${getPluginSanitizedFileName(repositoryUrl)}/${getPluginSanitizedFileName(internalName)}.cs3")
    }

    suspend fun downloadPlugin(
        activity: Activity, pluginUrl: String, pluginHash: String?, internalName: String, repositoryUrl: String, loadPlugin: Boolean,
    ): Boolean = runCatching {
        val name = internalName
        ExtensionManager.install(
            activity.applicationContext,
            RemoteExtension(
                internalName = internalName, name = name, url = pluginUrl, version = 0, sha256 = pluginHash,
                repoUrl = repositoryUrl, repoName = runCatching { ExtensionSources.fetchRepo(repositoryUrl).name }.getOrDefault(repositoryUrl),
                origin = "community",
            ),
        )
        true
    }.getOrDefault(false)

    suspend fun downloadPlugin(
        activity: Activity, pluginUrl: String, pluginHash: String?, internalName: String, file: File, loadPlugin: Boolean,
    ): Boolean = runCatching { ExtensionManager.installFromUrl(activity.applicationContext, pluginUrl); true }.getOrDefault(false)

    suspend fun deletePlugin(file: File): Boolean {
        val c = ctx ?: return false
        val ext = ExtensionManager.installedForFile(file.absolutePath) ?: return false
        return runCatching { ExtensionManager.uninstall(c, ext.key); true }.getOrDefault(false)
    }

    fun unloadPlugin(absolutePath: String) {
        val c = ctx ?: return
        val ext = ExtensionManager.installedForFile(absolutePath) ?: return
        kotlinx.coroutines.runBlocking { runCatching { ExtensionManager.setEnabled(c, ext.key, false) } }
    }

    suspend fun loadSinglePlugin(context: Context, apiName: String): Boolean {
        val ext = ExtensionManager.state.value.installed.firstOrNull {
            it.internalName.replace("provider", "", ignoreCase = true) == apiName || it.name == apiName
        } ?: return false
        return runCatching { ExtensionManager.setEnabled(context, ext.key, true); true }.getOrDefault(false)
    }
}
