/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import android.content.Context
import android.net.wifi.WifiManager
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * DLNA / UPnP media servers on the local network (MiniDLNA, Serviio, Universal Media Server,
 * most NAS boxes and routers, Windows Media sharing). Discovery is SSDP; browsing is the
 * ContentDirectory Browse action.
 */
class DlnaTree(override val server: HomeServers.Server) : MediaTree {
    private val control get() = server.extra
    private val resCache = ConcurrentHashMap<String, String>()

    override suspend fun list(path: String): List<MediaTree.Entry> = withContext(Dispatchers.IO) {
        val didl = browse(path.ifBlank { "0" }, "BrowseDirectChildren")
        parseDidl(didl)
    }

    override suspend fun stream(path: String): Pair<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val url = resCache[path] ?: parseDidl(browse(path, "BrowseMetadata")).firstOrNull()?.let { resCache[path] }
        (url ?: error("This item has no playable file")) to emptyMap()
    }

    private fun browse(id: String, flag: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>
<u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1"><ObjectID>${xmlEscape(id)}</ObjectID><BrowseFlag>$flag</BrowseFlag>
<Filter>*</Filter><StartingIndex>0</StartingIndex><RequestedCount>1000</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>"""
        val req = Request.Builder().url(control).post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"").build()
        val xml = NetworkClient.get(HomeServers.appContext).newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Media server answered HTTP ${r.code}")
            r.body.string()
        }
        return Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("Result")?.text().orEmpty()
    }

    private fun parseDidl(didl: String): List<MediaTree.Entry> = parse(didl).onEach { (e, res) -> res?.let { resCache[e.path] = it } }.map { it.first }

    companion object {
        private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /** Parses DIDL-Lite into entries plus each item's playable URL. */
        fun parse(didl: String): List<Pair<MediaTree.Entry, String?>> {
            if (didl.isBlank()) return emptyList()
            val doc = Jsoup.parse(didl, "", Parser.xmlParser())
            val containers = doc.select("container").map { c ->
                MediaTree.Entry(c.attr("id"), c.selectFirst("dc|title, title")?.text().orEmpty(), true) to null
            }
            val items = doc.select("item").mapNotNull { i ->
                val cls = i.selectFirst("upnp|class, class")?.text().orEmpty()
                val res = i.select("res").firstOrNull { it.attr("protocolInfo").contains("video") || cls.contains("videoItem") } ?: i.selectFirst("res")
                val mime = res?.attr("protocolInfo")?.split(":")?.getOrNull(2)
                if (!cls.contains("videoItem") && mime?.startsWith("video") != true) return@mapNotNull null
                MediaTree.Entry(i.attr("id"), i.selectFirst("dc|title, title")?.text().orEmpty(), false, mime ?: "video/*",
                    thumb = i.selectFirst("upnp|albumArtURI, albumArtURI")?.text(), size = res?.attr("size")?.toLongOrNull() ?: -1) to res?.text()?.trim()
            }
            return containers + items
        }

        data class Found(val name: String, val location: String)

        /** SSDP search for media servers on the current Wi-Fi (about 3 seconds). */
        suspend fun discover(context: Context): List<Found> = withContext(Dispatchers.IO) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("yumeiro-dlna").apply { setReferenceCounted(false); acquire() }
            val locations = LinkedHashSet<String>()
            try {
                MulticastSocket().use { sock ->
                    sock.soTimeout = 1000
                    val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n".toByteArray()
                    val group = InetAddress.getByName("239.255.255.250")
                    repeat(2) { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                    val deadline = System.currentTimeMillis() + 3000
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < deadline) {
                        val p = DatagramPacket(buf, buf.size)
                        try { sock.receive(p) } catch (_: SocketTimeoutException) { continue }
                        String(p.data, 0, p.length).lines().firstOrNull { it.startsWith("LOCATION:", true) }
                            ?.substringAfter(':')?.trim()?.let(locations::add)
                    }
                }
            } finally { lock.release() }
            locations.mapNotNull { loc ->
                runCatching {
                    val xml = NetworkClient.get(context).newCall(Request.Builder().url(loc).build()).execute().use { it.body.string() }
                    Found(Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("friendlyName")?.text() ?: loc, loc)
                }.getOrNull()
            }
        }

        /** Reads the device description at [location] and returns the ContentDirectory control URL. */
        suspend fun controlUrl(context: Context, location: String): String = withContext(Dispatchers.IO) {
            val xml = NetworkClient.get(context).newCall(Request.Builder().url(location).build()).execute().use { r ->
                if (!r.isSuccessful) error("Media server answered HTTP ${r.code}")
                r.body.string()
            }
            controlFrom(xml, location) ?: error("This device doesn't share media (no ContentDirectory)")
        }

        fun controlFrom(xml: String, location: String): String? {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val svc = doc.select("service").firstOrNull { it.selectFirst("serviceType")?.text()?.contains("ContentDirectory") == true } ?: return null
            val path = svc.selectFirst("controlURL")?.text()?.trim() ?: return null
            val base = doc.selectFirst("URLBase")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: location
            return base.toHttpUrl().resolve(path)?.toString()
        }
    }
}
