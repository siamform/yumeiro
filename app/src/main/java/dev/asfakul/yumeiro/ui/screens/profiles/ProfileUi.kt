package dev.asfakul.yumeiro.ui.screens.profiles

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.theme.DangerTone
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * PR-1 (ADR-041): a profile is a small moon in its own colour with its initial on it, and a thin
 * crescent shadow (Yumeiro's night sky, not a square avatar grid).
 */
@Composable
fun ProfileAvatar(profile: Profiles.Profile, size: Dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    val c = profile.tone.swatch
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.radialGradient(listOf(c, c.copy(alpha = 0.72f))))
            .border(if (selected) 3.dp else 0.dp, if (selected) TextPrimary else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Crescent shadow: a darker disc offset to the lower right, clipped by the moon.
        Box(Modifier.size(size).offset(x = size * 0.38f, y = size * 0.30f).clip(CircleShape).background(Color(0x22000000)))
        Text(profile.initial, color = Color(0xFF101014), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        if (profile.locked) Icon(
            Icons.Default.Lock, null, tint = Color(0xFF101014),
            modifier = Modifier.align(Alignment.BottomEnd).padding(size * 0.08f).size(size * 0.24f),
        )
    }
}

/** The "Who's watching?" cover was passed in this process (or the app restarted into a chosen profile). */
object ProfileSession {
    val chosen = MutableStateFlow(false)
    private const val KEY = "unlocked_for_restart"

    /** Before a restart into [id]: the next start opens it without asking again (one time only). */
    fun passThroughRestart(context: android.content.Context, id: String) {
        context.getSharedPreferences(Profiles.PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, id).commit()
    }

    internal fun consumePass(context: android.content.Context): Boolean {
        val p = context.getSharedPreferences(Profiles.PREFS, android.content.Context.MODE_PRIVATE)
        val id = p.getString(KEY, null) ?: return false
        p.edit().remove(KEY).commit()
        return id == Profiles.currentId(context)
    }

    /** Opens [id]: in place when it is the current one, otherwise saves it and restarts the app. */
    fun open(activity: Activity, id: String) {
        if (id == Profiles.currentId(activity)) { chosen.value = true; return }
        Profiles.select(activity, id)
        passThroughRestart(activity, id)
        dev.asfakul.yumeiro.backup.Restart.now(activity)
    }
}

/** At start: "Who's watching?" when there are several profiles or the profile is locked. */
@Composable
fun ProfileGate(activity: Activity) {
    val chosen by ProfileSession.chosen.collectAsState()
    if (chosen) return
    val context = LocalContext.current
    val need = remember {
        FeatureFlags.on(context, FeatureFlags.Flag.PROFILES) && Profiles.askAtStart(context) && !ProfileSession.consumePass(context)
    }
    if (!need) { LaunchedEffect(Unit) { ProfileSession.chosen.value = true }; return }
    WhoIsWatching(activity, onManage = null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoIsWatching(activity: Activity, onManage: (() -> Unit)?) {
    val list by Profiles.profiles.collectAsState()
    val current by Profiles.currentFlow.collectAsState()
    var unlocking by remember { mutableStateOf<Profiles.Profile?>(null) }
    val first = remember { FocusRequester() }
    BackHandler { activity.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxSize().background(DarkBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("profile_gate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("誰が見る？", color = SpotifyGreen, fontSize = 13.sp, letterSpacing = 2.sp)
        Text("Who's watching?", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 640.dp)) {
            list.forEachIndexed { i, p ->
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(16.dp))
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .clickable(onClickLabel = "Open ${p.name}") { if (p.locked) unlocking = p else ProfileSession.open(activity, p.id) }
                        .padding(8.dp).testTag("profile_pick_${p.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileAvatar(p, 72.dp, selected = p.id == current)
                    Spacer(Modifier.height(8.dp))
                    Text(p.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
        if (onManage != null) TextButton(onClick = onManage, modifier = Modifier.padding(top = 20.dp)) { Text("Manage profiles") }
        Spacer(Modifier.height(48.dp))
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    unlocking?.let { p -> UnlockProfileDialog(activity, p, onDismiss = { unlocking = null }, onUnlocked = { unlocking = null; ProfileSession.open(activity, p.id) }) }
}

/**
 * Unlocks [profile]: the phone's biometric / screen lock when the profile allows it, and the PIN pad
 * when it has a PIN. With neither available (biometric only, and the phone lost its screen lock) the
 * profile can not be opened here; its PIN can be set again from the default profile.
 */
@Composable
fun UnlockProfileDialog(activity: Activity, profile: Profiles.Profile, title: String = "Open ${profile.name}", onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val bioOk = remember { profile.biometric && Privacy.canAuthenticate(activity) }
    fun bio() = Privacy.verify(activity, title) { ok -> if (ok) onUnlocked() else if (!profile.hasPin) onDismiss() }
    LaunchedEffect(profile.id) { if (bioOk) bio() }
    if (!profile.hasPin && !bioOk) {
        LaunchedEffect(Unit) {
            dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "${profile.name} is locked with the phone's screen lock, which is off. Set a screen lock, or remove the lock from another profile.")
            onDismiss()
        }
        return
    }
    if (!profile.hasPin) return // the system prompt is up
    PinPadDialog(
        title = title, subtitle = if (bioOk) "Enter the PIN or use your fingerprint" else "Enter the PIN",
        error = error,
        onBiometric = if (bioOk) ({ bio() }) else null,
        onDismiss = onDismiss,
        onPin = { pin ->
            when (Profiles.checkPin(context, profile.id, pin)) {
                Profiles.PinCheck.OK, Profiles.PinCheck.NO_PIN -> { error = null; onUnlocked() }
                Profiles.PinCheck.WRONG -> error = "Wrong PIN"
                Profiles.PinCheck.LOCKED_OUT -> error = "Too many tries. Wait ${(Profiles.lockoutLeft(profile.id) / 1000) + 1} s"
            }
        },
    )
}

/**
 * A 4–8 digit PIN pad. Works by touch, with the remote's D-pad (every key is focusable) and with the
 * number keys on a TV remote or keyboard. [onPin] gets the digits when OK is pressed.
 */
@Composable
fun PinPadDialog(
    title: String,
    subtitle: String,
    error: String?,
    onDismiss: () -> Unit,
    onPin: (String) -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(error) { if (error != null) { delay(150); pin = "" } }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(28.dp))
                .background(DarkSurfaceContainer).padding(20.dp).testTag("pin_pad")
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val d = digitOf(e.key)
                    when {
                        d != null -> { if (pin.length < 8) pin += d; true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { pin = pin.dropLast(1); true }
                        e.key == Key.NumPadEnter -> { if (pin.length >= 4) onPin(pin); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.semantics { contentDescription = "${pin.length} digits entered" }.testTag("pin_dots"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) SpotifyGreen else TextTertiary.copy(alpha = 0.35f)))
                }
            }
            Text(error ?: " ", color = DangerTone, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).testTag("pin_error"))
            Spacer(Modifier.height(8.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("back", "0", "ok"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { k ->
                        val enabled = when (k) { "ok" -> pin.length >= 4; "back" -> pin.isNotEmpty(); else -> pin.length < 8 }
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(if (k == "ok" && enabled) SpotifyGreen else TextPrimary.copy(alpha = 0.07f))
                                .then(if (k == "1") Modifier.focusRequester(focus) else Modifier)
                                .clickable(enabled = enabled, onClickLabel = when (k) { "ok" -> "OK"; "back" -> "Delete"; else -> k }) {
                                    when (k) { "ok" -> onPin(pin); "back" -> pin = pin.dropLast(1); else -> pin += k }
                                }
                                .testTag("pin_key_$k"),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "back" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = if (enabled) TextPrimary else TextTertiary)
                                "ok" -> Text("OK", color = if (enabled) dev.asfakul.yumeiro.ui.theme.OnAccent else TextTertiary, fontWeight = FontWeight.Bold)
                                else -> Text(k, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (onBiometric != null) TextButton(onClick = onBiometric) {
                    Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Screen lock")
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
}

private fun digitOf(k: Key): String? = when (k) {
    Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"; else -> null
}
