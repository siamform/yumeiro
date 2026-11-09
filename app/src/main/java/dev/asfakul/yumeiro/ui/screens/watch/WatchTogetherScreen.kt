/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.watch

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.sources.HubCard
import dev.asfakul.yumeiro.ui.screens.sources.SectionTitle
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.watch.RoomNav
import dev.asfakul.yumeiro.watch.WatchTogether
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** Host or join a room: on the same Wi-Fi (no server), or online through Yumeiro Cloud (end-to-end encrypted). No account. */
@Composable
fun WatchTogetherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by WatchTogether.status.collectAsState()
    val found by WatchTogether.found.collectAsState()
    var joinTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manual by remember { mutableStateOf(false) }
    var joinOnline by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("watch_together", android.content.Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", null).orEmpty()) }
    fun myName() = name.trim().ifBlank { android.os.Build.MODEL.take(24) }
    // Opened from an invite link: ask before joining.
    val pendingInvite by RoomNav.pending.collectAsState()
    LaunchedEffect(pendingInvite) { pendingInvite?.let { joinOnline = it; RoomNav.consumed() } }
    DisposableEffect(status.role) {
        if (status.role == WatchTogether.Role.NONE) WatchTogether.discover(context)
        onDispose { WatchTogether.stopDiscovery(context) }
    }

    Column(Modifier.fillMaxSize().testTag("watch_together")) {
        SubPageHeader(title = "Watch together", onBack = onBack, subtitle = "Same Wi-Fi or online · no account needed")
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status.online && status.role != WatchTogether.Role.NONE) {
                item("online") { OnlineRoomCard(status, onLeave = { WatchTogether.leave(context) }) }
                item("chat") { RoomChat() }
            } else when (status.role) {
                WatchTogether.Role.HOST -> item("host") {
                    Surface(shape = RoundedCornerShape(18.dp), color = SpotifyGreen.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("You're hosting \"${status.roomName}\"", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Room code", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                            Text(status.code, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text("Address ${status.address} · ${status.guests} watching", color = TextSecondary, fontSize = 13.sp)
                            Text(if (status.state.title.isBlank()) "Play something from Videos → Online. Everyone in the room follows your title, episode, pause and seek."
                                else "Now: ${status.state.title}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { WatchTogether.leave(context) }, shape = RoundedCornerShape(20.dp)) { Text("Close room") }
                        }
                    }
                }
                WatchTogether.Role.GUEST -> item("guest") {
                    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (status.connected) "In \"${status.roomName}\"" else "Connecting to \"${status.roomName}\"…", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(status.state.title.ifBlank { "Waiting for the host to play something" }, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            status.message?.let { Text(it, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                            Text("You need the same source installed as the host. Each phone streams on its own.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { WatchTogether.leave(context) }, shape = RoundedCornerShape(20.dp)) { Text("Leave") }
                        }
                    }
                }
                WatchTogether.Role.NONE -> {
                    item("start") {
                        Text("One phone hosts, others join with a 4-digit code. Works on home Wi-Fi or a phone hotspot; nothing goes through the internet except each phone's own stream.",
                            color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { WatchTogether.host(context, android.os.Build.MODEL + " room") }, shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("wt_host")) { Text("Host a room") }
                            OutlinedButton(onClick = { manual = true }, shape = RoundedCornerShape(20.dp)) { Text("Join by address") }
                        }
                    }
                    status.message?.let { m -> item("msg") { Text(m, color = TextTertiary, fontSize = 12.sp) } }
                    item("online_start") {
                        SectionTitle("Online room", "Friends anywhere · end-to-end encrypted")
                        Text("Share a link; whoever opens it joins. Yumeiro Cloud only relays sealed messages: the room key is in the link's #part, which never reaches the server. Rooms end when the host closes them, after 10 idle minutes, or after 24 hours.",
                            color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(name, { name = it.take(24); prefs.edit().putString("name", name).apply() }, singleLine = true,
                            label = { Text("Your name in rooms") }, placeholder = { Text(android.os.Build.MODEL) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { WatchTogether.hostOnline(context, myName()) }, shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("wt_host_online")) { Text("Create online room") }
                            OutlinedButton(onClick = { joinOnline = "" }, shape = RoundedCornerShape(20.dp)) { Text("Join with link") }
                        }
                    }
                    item("nearby") { SectionTitle("Rooms nearby", if (found.isEmpty()) "Looking on this Wi-Fi…" else null) }
                    items(found, key = { it.name + it.host }) { f ->
                        HubCard(f.name, "${f.host}:${f.port}") {
                            TextButton(onClick = { joinTarget = "${f.host}:${f.port}" to f.name }) { Text("Join", color = SpotifyGreen) }
                        }
                    }
                }
            }
        }
    }
    joinOnline?.let { initial ->
        var link by remember(initial) { mutableStateOf(initial) }
        var bad by remember { mutableStateOf(false) }
        TsukiDialog(onDismissRequest = { joinOnline = null }, title = { Text("Join online room") },
            text = { Column {
                Text("Paste the invite link a friend sent you.", color = TextSecondary, fontSize = 13.sp)
                OutlinedTextField(link, { link = it; bad = false }, label = { Text("Invite link") }, isError = bad, maxLines = 3, modifier = Modifier.fillMaxWidth().testTag("wt_invite"))
                if (bad) Text("That doesn't look like a complete Yumeiro invite.", color = TextTertiary, fontSize = 12.sp)
            } },
            confirmButton = { TextButton(enabled = link.isNotBlank(), onClick = {
                if (WatchTogether.joinOnline(context, link, myName())) joinOnline = null else bad = true
            }) { Text("Join") } },
            dismissButton = { TextButton(onClick = { joinOnline = null }) { Text("Cancel") } })
    }
    if (manual) {
        var addr by remember { mutableStateOf("") }
        TsukiDialog(onDismissRequest = { manual = false }, title = { Text("Join by address") },
            text = { OutlinedTextField(addr, { addr = it }, singleLine = true, label = { Text("192.168.1.20:41234") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) },
            confirmButton = { TextButton(enabled = addr.contains(':'), onClick = { manual = false; joinTarget = addr.trim() to addr.trim() }) { Text("Next") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } })
    }
    joinTarget?.let { (address, name) ->
        var code by remember { mutableStateOf("") }
        TsukiDialog(onDismissRequest = { joinTarget = null }, title = { Text("Join $name") },
            text = { OutlinedTextField(code, { if (it.length <= 4) code = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Room code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.testTag("wt_code")) },
            confirmButton = { TextButton(enabled = code.length == 4, onClick = { joinTarget = null; WatchTogether.join(context, address, code, name) }) { Text("Join") } },
            dismissButton = { TextButton(onClick = { joinTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun OnlineRoomCard(status: WatchTogether.Status, onLeave: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val host = status.role == WatchTogether.Role.HOST
    Surface(shape = RoundedCornerShape(18.dp), color = if (host) SpotifyGreen.copy(alpha = 0.12f) else GlassFill, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(when {
                host && status.connected -> "Your online room is open"
                host -> "Opening your online room…"
                status.connected -> "In an online room"
                else -> "Joining online room…"
            }, color = TextPrimary, fontWeight = FontWeight.Bold)
            if (status.code.isNotBlank()) Text("Room ${status.code} · ${status.guests} ${if (host) "watching" else "others here"}" +
                if (!host && !status.hostHere) " · host away" else "", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            status.message?.let { Text(it, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(when {
                host && status.state.title.isBlank() -> "Play something from Videos → Online. Everyone follows your title, episode, pause and seek."
                status.state.title.isNotBlank() -> "Now: ${status.state.title}"
                else -> "Waiting for the host to play something"
            }, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            if (!host) Text("You need the same source installed as the host. Each device streams on its own.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.invite.isNotBlank()) {
                    Button(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, "Watch with me on Yumeiro: ${status.invite}")
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, "Invite to room")) }
                    }, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Invite") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(status.invite))
                        dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Invite link copied")
                    }, shape = RoundedCornerShape(20.dp)) { Text("Copy link") }
                }
                OutlinedButton(onClick = onLeave, shape = RoundedCornerShape(20.dp)) { Text(if (host) "End room" else "Leave") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomChat() {
    val chat by WatchTogether.chat.collectAsState()
    var draft by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Chat", color = TextPrimary, fontWeight = FontWeight.Bold)
            if (chat.isEmpty()) Text("Messages are end-to-end encrypted and disappear when you leave.", color = TextTertiary, fontSize = 12.sp)
            chat.takeLast(40).forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (c.mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.widthIn(max = 280.dp)) {
                        if (!c.mine) Text(c.from, color = TextTertiary, fontSize = 11.sp)
                        Text(c.text, color = TextPrimary, fontSize = if (c.reaction) 24.sp else 14.sp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("\uD83D\uDE02", "\uD83D\uDE2D", "\uD83D\uDE31", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDC4F").forEach { r ->
                    TextButton(onClick = { WatchTogether.say(r, reaction = true) }) { Text(r, fontSize = 20.sp) }
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(500) }, singleLine = true, placeholder = { Text("Say something") },
                    modifier = Modifier.weight(1f).testTag("wt_chat"))
                TextButton(enabled = draft.isNotBlank(), onClick = { if (WatchTogether.say(draft)) draft = "" }) { Text("Send", color = SpotifyGreen) }
            }
        }
    }
}
