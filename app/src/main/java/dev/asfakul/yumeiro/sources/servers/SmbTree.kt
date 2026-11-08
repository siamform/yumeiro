/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URLEncoder
import java.util.EnumSet
import java.util.UUID

/**
 * SMB / Windows share (`smb://host/share/folder`). Files are streamed to the player through a
 * loopback-only HTTP server with a per-process random token, so other apps can't read the share.
 */
class SmbTree(override val server: HomeServers.Server) : MediaTree {
    private val host: String
    private val shareName: String
    private val basePath: String

    init {
        val clean = server.url.removePrefix("smb://").trim('/').replace('\\', '/')
        val parts = clean.split('/')
        require(parts.size >= 2 && parts[1].isNotBlank()) { "Use smb://host/share" }
        host = parts[0]; shareName = parts[1]; basePath = parts.drop(2).joinToString("\\")
    }

    @Volatile private var share: DiskShare? = null

    @Synchronized
    fun share(): DiskShare {
        share?.takeIf { it.isConnected }?.let { return it }
        val pass = server.secretPlain()
        val auth = when {
            server.user.isBlank() -> AuthenticationContext.anonymous()
            server.user.equals("guest", true) -> AuthenticationContext.guest()
            else -> {
                val domain = server.user.substringBefore('\\', "")
                AuthenticationContext(server.user.substringAfter('\\'), pass.toCharArray(), domain)
            }
        }
        val s = client.connect(host).authenticate(auth).connectShare(shareName) as? DiskShare ?: error("$shareName isn't a file share")
        share = s
        return s
    }

    private fun smbPath(path: String) = listOf(basePath, path.replace('/', '\\')).filter { it.isNotBlank() }.joinToString("\\")

    override suspend fun list(path: String): List<MediaTree.Entry> = withContext(Dispatchers.IO) {
        share().list(smbPath(path)).filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }.map { f ->
            val dir = (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            MediaTree.Entry(listOf(path, f.fileName).filter { it.isNotBlank() }.joinToString("/"), f.fileName, dir, size = f.endOfFile)
        }
    }

    override suspend fun stream(path: String): Pair<String, Map<String, String>> {
        val port = SmbProxy.ensure()
        return "http://127.0.0.1:$port/smb/${SmbProxy.token}/${server.id}?p=${URLEncoder.encode(path, "UTF-8")}" to emptyMap()
    }

    /** Opens [path] for ranged reads. */
    fun open(path: String) = share().openFile(
        smbPath(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null,
    )

    companion object { private val client by lazy { SMBClient() } }
}

/** Loopback HTTP server that serves SMB files with Range support for the player. */
object SmbProxy {
    val token: String = UUID.randomUUID().toString().replace("-", "")
    private var server: NanoHTTPD? = null

    @Synchronized
    fun ensure(): Int {
        server?.takeIf { it.isAlive }?.let { return it.listeningPort }
        val s = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response = runCatching { handle(session) }
                .getOrElse { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, it.message ?: "error") }
        }
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        server = s
        return s.listeningPort
    }

    private fun NanoHTTPD.handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.size < 3 || parts[0] != "smb" || parts[1] != token) return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "forbidden")
        val srv = HomeServers.servers.value.firstOrNull { it.id == parts[2] && it.kind == HomeServers.Kind.SMB }
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "no server")
        val path = session.parameters["p"]?.firstOrNull() ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "no path")
        val tree = trees.getOrPut(srv.id) { SmbTree(srv) }
        val file = tree.open(path)
        val size = file.fileInformation.standardInformation.endOfFile
        val range = session.headers["range"]?.removePrefix("bytes=")?.split("-")
        val start = range?.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
        val length = (end - start + 1).coerceAtLeast(0)
        val mime = NanoHTTPD.getMimeTypeForFile(path).takeIf { it != "application/octet-stream" } ?: "video/*"
        val stream = object : InputStream() {
            var pos = start
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xff }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos > end) return -1
                val n = file.read(b, pos, off, minOf(len.toLong(), end - pos + 1).toInt())
                if (n <= 0) return -1
                pos += n
                return n
            }
            override fun close() { runCatching { file.close() } }
        }
        val status = if (range != null) NanoHTTPD.Response.Status.PARTIAL_CONTENT else NanoHTTPD.Response.Status.OK
        return NanoHTTPD.newFixedLengthResponse(status, mime, stream, length).apply {
            addHeader("Accept-Ranges", "bytes")
            if (range != null) addHeader("Content-Range", "bytes $start-$end/$size")
        }
    }

    private val trees = java.util.concurrent.ConcurrentHashMap<String, SmbTree>()
}
