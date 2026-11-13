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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import dev.asfakul.yumeiro.watch.WatchTogether

/** Host or join a room on the same Wi-Fi. No account, no server. */
@Composable
fun WatchTogetherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by WatchTogether.status.collectAsState()
    val found by WatchTogether.found.collectAsState()
    var joinTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manual by remember { mutableStateOf(false) }
    DisposableEffect(status.role) {
        if (status.role == WatchTogether.Role.NONE) WatchTogether.discover(context)
        onDispose { WatchTogether.stopDiscovery(context) }
    }

    Column(Modifier.fillMaxSize().testTag("watch_together")) {
        SubPageHeader(title = "Watch together", onBack = onBack, subtitle = "Same Wi-Fi · no account needed")
        LazyColumn(state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("watch_together"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status.role) {
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
                    item("nearby") { SectionTitle("Rooms nearby", if (found.isEmpty()) "Looking on this Wi-Fi…" else null) }
                    if (found.isEmpty()) item("nearby_empty") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassFill).padding(16.dp).testTag("wt_nearby_empty"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Text("No rooms yet. When someone on the same Wi-Fi taps Host a room, it shows up here.", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    items(found, key = { it.name + it.host }) { f ->
                        HubCard(f.name, "${f.host}:${f.port}") {
                            TextButton(onClick = { joinTarget = "${f.host}:${f.port}" to f.name }) { Text("Join", color = SpotifyGreen) }
                        }
                    }
                }
            }
        }
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
