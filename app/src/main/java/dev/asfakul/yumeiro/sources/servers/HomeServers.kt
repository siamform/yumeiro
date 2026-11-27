/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.security.SecretBox
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Personal media servers on the user's own network or account: Jellyfin, Emby, Plex, DLNA / UPnP,
 * WebDAV and SMB shares. No Yumeiro server is involved; the phone talks to the user's server.
 * Passwords and tokens are sealed with the Android Keystore ([SecretBox]).
 */
object HomeServers {
    private const val TAG = "HomeServers"

    enum class Kind(val label: String, val ecosystem: SourceHub.Ecosystem) {
        JELLYFIN("Jellyfin", SourceHub.Ecosystem.JELLYFIN),
        EMBY("Emby", SourceHub.Ecosystem.EMBY),
        PLEX("Plex", SourceHub.Ecosystem.PLEX),
        DLNA(tr(R.string.dlna_upnp, "DLNA / UPnP"), SourceHub.Ecosystem.DLNA),
        WEBDAV("WebDAV", SourceHub.Ecosystem.WEBDAV),
        SMB(tr(R.string.smb_share, "SMB share"), SourceHub.Ecosystem.SMB),
    }

    /** Saved connection. [secret] is sealed (password, access token or Plex token). */
    @Serializable
    data class Server(
        val id: String = UUID.randomUUID().toString(),
        val kind: Kind,
        val name: String,
        val url: String,
        val user: String = "",
        val secret: String = "",
        val userId: String = "",
        val extra: String = "",
    ) {
        fun secretPlain(): String = if (secret.isBlank()) "" else SecretBox.open(secret).orEmpty()
    }

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var started = false
    lateinit var appContext: Context
        private set

    private fun file(context: Context) = File(context.filesDir, "servers").apply { mkdirs() }.let { File(it, "servers.json") }

    fun deviceId(context: Context): String {
        val p = context.getSharedPreferences("home_servers", Context.MODE_PRIVATE)
        return p.getString("device", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    /*
     * N4-1: servers.json was read with runCatching → empty list and written in place, so a damaged
     * or half-written file became "no servers" and the next save wiped every server; add and remove
     * could also overwrite each other. Now reads and writes go through SafeJsonFile (atomic write,
     * .tmp recovery, damaged copy kept as .bad) and every change runs under one lock.
     */
    private val lock = Any()

    fun start(context: Context) = synchronized(lock) {
        if (started) return@synchronized
        appContext = context.applicationContext
        val saved = dev.asfakul.yumeiro.data.SafeJsonFile.read(file(appContext)) {
            json.decodeFromString(ListSerializer(Server.serializer()), it)
        }.value.orEmpty()
        _servers.value = saved
        started = true
        publish()
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() = synchronized(lock) { started = false; _servers.value = emptyList() }

    /** Signs in / probes the server, then saves it. [password] is sealed before it is stored. */
    suspend fun add(context: Context, kind: Kind, name: String, url: String, user: String, password: String): Result<Server> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!started) start(context)
                val base = url.trim().removeSuffix("/").let { if (kind == Kind.SMB || it.startsWith("http")) it else "http://$it" }
                val probed = when (kind) {
                    Kind.JELLYFIN, Kind.EMBY -> JellyfinClient.login(context, kind, base, user, password)
                    Kind.PLEX -> PlexClient.probe(context, base, password)
                    Kind.WEBDAV -> WebDavTree(Server(kind = kind, name = name, url = base, user = user, secret = seal(password))).also { it.list("") }.server
                    Kind.SMB -> SmbTree(Server(kind = kind, name = name, url = base, user = user, secret = seal(password))).also { it.list("") }.server
                    Kind.DLNA -> Server(kind = kind, name = name, url = base, extra = DlnaTree.controlUrl(context, base))
                }
                val saved = probed.copy(name = name.ifBlank { probed.name.ifBlank { kind.label } })
                synchronized(lock) {
                    _servers.value = _servers.value.filterNot { it.id == saved.id } + saved
                    save(context)
                    publish()
                }
                saved
            }
        }

    fun remove(context: Context, id: String) = synchronized(lock) {
        if (!started) start(context)
        _servers.value = _servers.value.filterNot { it.id == id }
        save(context)
        publish()
    }

    fun seal(plain: String): String = if (plain.isBlank()) "" else SecretBox.seal(plain) ?: error("Secure storage is unavailable on this device")

    private fun save(context: Context) {
        dev.asfakul.yumeiro.data.SafeJsonFile.write(file(context), json.encodeToString(ListSerializer(Server.serializer()), _servers.value))
    }

    private fun publish() {
        val taken = HashSet<String>()
        val apis: List<MainAPI> = _servers.value.mapNotNull { s ->
            val name = SourceHub.uniqueName(s.name, null, taken).also(taken::add)
            runCatching {
                when (s.kind) {
                    Kind.JELLYFIN, Kind.EMBY -> JellyfinProvider(s, name)
                    Kind.PLEX -> PlexProvider(s, name)
                    Kind.WEBDAV -> TreeProvider(WebDavTree(s), name)
                    Kind.SMB -> TreeProvider(SmbTree(s), name)
                    Kind.DLNA -> TreeProvider(DlnaTree(s), name)
                }
            }.onFailure { Log.w(TAG, s.name, it) }.getOrNull()
        }
        SourceHub.publishVideo("servers", SourceHub.Ecosystem.JELLYFIN, apis)
    }

    /** Ecosystem badge per server is resolved from the provider itself. */
    fun ecosystemOf(api: MainAPI): SourceHub.Ecosystem? = when (api) {
        is JellyfinProvider -> api.server.kind.ecosystem
        is PlexProvider -> SourceHub.Ecosystem.PLEX
        is TreeProvider -> api.tree.server.kind.ecosystem
        else -> null
    }
}
