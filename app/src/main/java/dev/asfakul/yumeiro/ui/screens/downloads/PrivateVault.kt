package dev.asfakul.yumeiro.ui.screens.downloads

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.profiles.PinPadDialog
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/**
 * Opens the vault: the phone's fingerprint / face / screen lock when it has one, the profile PIN
 * when it has one (both when both: the PIN pad offers the screen lock). With neither there is
 * nothing to check, so the user is asked to set one first.
 */
@Composable
internal fun VaultUnlock(onDismiss: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val profile = remember { Profiles.current(context) }
    val device = remember { activity != null && Privacy.canAuthenticate(activity) }
    var error by remember { mutableStateOf<String?>(null) }
    fun bio() { if (activity != null) Privacy.verify(activity, "Open Private") { ok -> if (ok) onOpen() else if (!profile.hasPin) onDismiss() } }
    LaunchedEffect(Unit) {
        when {
            !device && !profile.hasPin -> {
                AppMessages.error(context, "Set a screen lock on the phone, or a PIN for this profile (Settings → Privacy → Profiles), to use Private")
                onDismiss()
            }
            device -> bio()
        }
    }
    if (profile.hasPin) PinPadDialog(
        title = "Open Private", subtitle = "Enter ${profile.name}'s PIN", error = error,
        onBiometric = if (device) ({ bio() }) else null, onDismiss = onDismiss,
        onPin = { pin ->
            when (Profiles.checkPin(context, profile.id, pin)) {
                Profiles.PinCheck.OK, Profiles.PinCheck.NO_PIN -> onOpen()
                Profiles.PinCheck.WRONG -> error = "Wrong PIN"
                Profiles.PinCheck.LOCKED_OUT -> error = "Too many tries. Wait ${Profiles.waitText(Profiles.lockoutLeft(context, profile.id))}"
            }
        },
    )
}

@Composable
internal fun PrivateVault(days: Int, onBack: () -> Unit, onPlayVideo: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val all by OnlineDownloads.items.collectAsState()
    val me = remember { Profiles.currentId(context) }
    val items = remember(all) { all.filter { it.hidden && it.profile == me }.sortedByDescending { it.finishedAt } }
    // Locks again as soon as the app leaves the screen, and no screenshots / recents preview meanwhile.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) onBack() }
        lifecycle.lifecycle.addObserver(obs)
        onDispose {
            lifecycle.lifecycle.removeObserver(obs)
            if (!(Privacy.hideInRecents.value && Privacy.lockEnabled.value)) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    Column(Modifier.fillMaxSize().testTag("downloads_vault")) {
        SubPageHeader("Private", onBack, subtitle = "${items.size} hidden · only on this phone, only for ${Profiles.current(context).name}")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) item("empty") {
                Text("Nothing here yet. In Downloads, tap the eye on a finished download to move it here. It leaves the Downloads list, the gallery and every other app.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
            items(items, key = { "v_" + it.id }) { d -> DownloadRow(d, days, onPlayVideo) }
            item("note") {
                Text("Private downloads are not in backups. Deleting this profile deletes them.", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
