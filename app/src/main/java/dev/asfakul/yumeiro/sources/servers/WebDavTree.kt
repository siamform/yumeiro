/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** WebDAV folder (Nextcloud, ownCloud, rclone serve webdav, NAS apps…) over PROPFIND. */
class WebDavTree(override val server: HomeServers.Server) : MediaTree {
    private val auth by lazy { server.secretPlain().let { if (server.user.isBlank()) null else Credentials.basic(server.user, it) } }
    private val root = server.url.trimEnd('/') + "/"

    override suspend fun list(path: String): List<MediaTree.Entry> = withContext(Dispatchers.IO) {
        val url = resolve(path)
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontenttype/><d:getcontentlength/><d:displayname/></d:prop></d:propfind>"""
        val req = Request.Builder().url(url).method("PROPFIND", body.toRequestBody("application/xml".toMediaType())).header("Depth", "1")
            .apply { auth?.let { header("Authorization", it) } }.build()
        val xml = NetworkClient.get(HomeServers.appContext).newCall(req).execute().use { r ->
            if (r.code == 401) error("WebDAV refused the username or password")
            if (!r.isSuccessful) error("WebDAV answered HTTP ${r.code}")
            r.body.string()
        }
        parse(xml, url)
    }

    override suspend fun stream(path: String) = resolve(path) to (auth?.let { mapOf("Authorization" to it) } ?: emptyMap())

    private fun resolve(path: String): String = if (path.startsWith("http")) path else root + path.trimStart('/')

    companion object {
        /** Parses a multistatus answer; the folder itself is dropped. Paths are absolute URLs. */
        fun parse(xml: String, folderUrl: String): List<MediaTree.Entry> {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val self = folderUrl.toHttpUrl()
            return doc.select("*|response, response").mapNotNull { r ->
                val href = r.selectFirst("*|href, href")?.text()?.trim() ?: return@mapNotNull null
                val abs = self.resolve(href)?.toString() ?: return@mapNotNull null
                if (abs.trimEnd('/') == self.toString().trimEnd('/')) return@mapNotNull null
                val isDir = r.selectFirst("*|collection, collection") != null
                val name = r.selectFirst("*|displayname, displayname")?.text()?.takeIf { it.isNotBlank() }
                    ?: java.net.URLDecoder.decode(abs.trimEnd('/').substringAfterLast('/'), "UTF-8")
                MediaTree.Entry(abs, name, isDir, r.selectFirst("*|getcontenttype, getcontenttype")?.text(),
                    size = r.selectFirst("*|getcontentlength, getcontentlength")?.text()?.toLongOrNull() ?: -1)
            }.distinctBy { it.path }
        }
    }
}
