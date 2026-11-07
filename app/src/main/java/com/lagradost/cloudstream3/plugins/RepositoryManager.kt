package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.RemoteExtension

data class Repository(
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("manifestVersion") val manifestVersion: Int,
    @JsonProperty("pluginLists") val pluginLists: List<String>,
)

/** One entry of a plugins.json (CloudStream-compatible). Status: 0 down, 1 ok, 2 slow, 3 beta. */
data class SitePlugin(
    @JsonProperty("url") val url: String,
    @JsonProperty("status") val status: Int,
    @JsonProperty("version") val version: Int,
    @JsonProperty("apiVersion") val apiVersion: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("authors") val authors: List<String>,
    @JsonProperty("description") val description: String?,
    @JsonProperty("repositoryUrl") val repositoryUrl: String?,
    @JsonProperty("tvTypes") val tvTypes: List<String>?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("fileSize") val fileSize: Long?,
    @JsonProperty("fileHash") val fileHash: String?,
)

data class PluginWrapper(
    @JsonProperty("repository") val repository: Repository,
    @JsonProperty("repositoryData") val repositoryData: RepositoryData,
    @JsonProperty("plugin") val plugin: SitePlugin,
) {
    companion object {
        private val localRepository = Repository("", "", "", 1, emptyList())
        private val localRepositoryData = RepositoryData("", "", "")
        fun getLocalPluginWrapper(plugin: SitePlugin): PluginWrapper = PluginWrapper(localRepository, localRepositoryData, plugin)
    }
}

/** CloudStream's RepositoryManager, backed by this app's saved repositories. */
object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"

    val PREBUILT_REPOSITORIES: Array<RepositoryData> get() = emptyArray()

    fun convertRawGitUrl(url: String): String {
        if (url.contains("raw.githubusercontent.com")) return url
        val m = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(url) ?: return url
        val (user, repo, rest) = m.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$rest"
    }

    suspend fun parseRepository(url: String): Repository? = runCatching {
        val r = ExtensionSources.fetchRepo(convertRawGitUrl(url))
        Repository(r.iconUrl, r.name, r.description, 1, r.pluginLists)
    }.getOrNull()

    private fun SitePluginOf(e: RemoteExtension) = SitePlugin(
        e.url, e.status, e.version, e.apiVersion, e.name, e.internalName, e.authors, e.description, e.repoUrl,
        e.tvTypes, e.language, e.iconUrl, e.fileSize, e.sha256,
    )

    suspend fun getRepoPlugins(repositoryData: RepositoryData): List<PluginWrapper>? = runCatching {
        val (repo, list) = ExtensionSources.repoPlugins(repositoryData.url)
        val r = Repository(repo.iconUrl, repo.name, repo.description, 1, repo.pluginLists)
        list.map { PluginWrapper(r, repositoryData, SitePluginOf(it)) }
    }.getOrNull()

    suspend fun getRepoPlugins(repositoryUrl: String): List<Pair<String, SitePlugin>>? = runCatching {
        ExtensionSources.repoPlugins(repositoryUrl).second.map { repositoryUrl to SitePluginOf(it) }
    }.getOrNull()

    fun getRepositories(): Array<RepositoryData> =
        ExtensionManager.state.value.repos.map { RepositoryData(null, it.name, it.url) }.toTypedArray()

    suspend fun addRepository(repository: RepositoryData) {
        val c = CloudStreamApp.context ?: return
        runCatching { ExtensionManager.addRepository(c, repository.url) }
    }

    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        runCatching { ExtensionManager.removeRepository(context, repository.url) }
    }
}
