package dev.asfakul.yumeiro.ui.screens.profiles

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.DialogTone
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.AccentTone
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * PR-1: Settings → Privacy → Profiles. Lists the profiles; tap one to rename it, pick its colour, set a
 * PIN or the phone's lock, switch to it, or delete it. Another profile's settings open only after its
 * own lock, so a child's profile can not remove a parent's PIN.
 */
@Composable
fun ProfilesSettings(activity: Activity?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Profiles.init(context) }
    val list by Profiles.profiles.collectAsState()
    val current by Profiles.currentFlow.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    var unlocking by remember { mutableStateOf<Profiles.Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var mainCheck by remember { mutableStateOf(false) }
    Column(Modifier.testTag("profiles_settings")) {
        list.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(yuRound(12))
                    .clickable { if (p.id != current && p.locked) unlocking = p else editing = p.id }
                    .padding(vertical = 8.dp).testTag("profile_row_${p.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(p, 40.dp, selected = p.id == current)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(if (p.id == current) "Watching now" else null, if (p.hasPin) "PIN" else null, if (p.biometric) "Screen lock" else null, if (!p.locked) "No lock" else null).joinToString(" · "),
                        color = TextTertiary, fontSize = 12.sp,
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
            }
        }
        if (list.size < Profiles.MAX_PROFILES) Row(
            Modifier.fillMaxWidth().clip(yuRound(12)).clickable { if (Profiles.needsMainUnlock(context)) mainCheck = true else adding = true }.padding(vertical = 10.dp).testTag("profile_add"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, TextTertiary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = TextPrimary) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.add_profile), color = TextPrimary, fontSize = 15.sp)
        }
        Text(stringResource(R.string.each_profile_has_its_own_history),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    }
    if (adding) NameAndColourDialog(tr(R.string.new_profile, "New profile"), "", AccentTone.entries[list.size % AccentTone.entries.size], onDismiss = { adding = false }) { name, tone ->
        adding = false
        val p = Profiles.add(context, name, tone)
        AppMessages.success(context, "${p.name} added. Open it from Who's watching? or here.")
    }
    // Netflix's "require PIN to add profiles": with the main profile locked, a child's profile can't add one.
    if (mainCheck && activity != null) Profiles.find(context, Profiles.DEFAULT)?.let { main ->
        UnlockProfileDialog(activity, main, title = "${main.name}'s lock is needed to add a profile", onDismiss = { mainCheck = false }, onUnlocked = { mainCheck = false; adding = true })
    }
    unlocking?.let { p -> if (activity != null) UnlockProfileDialog(activity, p, title = "Edit ${p.name}", onDismiss = { unlocking = null }, onUnlocked = { unlocking = null; editing = p.id }) }
    editing?.let { id -> list.firstOrNull { it.id == id }?.let { p -> EditProfileDialog(activity, p, isCurrent = p.id == current, onDismiss = { editing = null }) } }
}

@Composable
private fun EditProfileDialog(activity: Activity?, p: Profiles.Profile, isCurrent: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var pinStep by remember { mutableStateOf(0) } // 1 = new PIN, 2 = repeat
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf(false) }
    // OWN-12: an action that waits for the current PIN; settingFirst: the PIN being set is the profile's first.
    var guard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingFirst by remember { mutableStateOf(false) }
    fun guarded(action: () -> Unit) { if (ProfilePinGuard.needsCurrentPin(p)) guard = action else action() }
    // PROFILE-PIC-1: the system photo picker (no storage permission); the copy is kept with the profile.
    val photoScope = androidx.compose.runtime.rememberCoroutineScope()
    var photoBusy by remember { mutableStateOf(false) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoScope.launch {
            photoBusy = true
            val err = dev.asfakul.yumeiro.profiles.ProfilePhotos.import(context, p.id, uri)
            photoBusy = false
            if (err == null) AppMessages.success(context, "Picture updated for ${p.name}") else AppMessages.error(context, err)
        }
    }
    val pickPhoto = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    TsukiDialog(
        onDismissRequest = onDismiss,
        icon = { ProfileAvatar(p, 56.dp) },
        title = { Text(p.name) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickPhoto() }, enabled = !photoBusy, modifier = Modifier.testTag("profile_photo_pick")) {
                        Text(when { photoBusy -> "Saving…"; p.photo > 0L -> tr(R.string.change_picture, "Change picture"); else -> tr(R.string.choose_a_picture, "Choose a picture") }, color = SpotifyGreen)
                    }
                    if (p.photo > 0L) TextButton(onClick = { photoScope.launch { dev.asfakul.yumeiro.profiles.ProfilePhotos.remove(context, p.id); AppMessages.success(context, tr(R.string.picture_removed, "Picture removed")) } },
                        enabled = !photoBusy, modifier = Modifier.testTag("profile_photo_remove")) { Text(stringResource(R.string.remove_picture), color = TextSecondary) }
                }
                ToneRow(p.tone) { Profiles.setTone(context, p.id, it) }
                Spacer(Modifier.height(8.dp))
                ActionRow(tr(R.string.rename, "Rename"), "profile_rename") { rename = true }
                ActionRow(if (p.hasPin) tr(R.string.change_pin, "Change PIN") else tr(R.string.set_a_pin, "Set a PIN"), "profile_pin") { guarded { pinError = null; settingFirst = !p.hasPin; pinStep = 1 } }
                if (p.hasPin) ActionRow(tr(R.string.remove_pin, "Remove PIN"), "profile_pin_remove") { guarded { Profiles.setPin(context, p.id, null); AppMessages.success(context, tr(R.string.pin_removed, "PIN removed")) } }
                if (p.hasPin) ActionRow(if (p.hasRecovery) tr(R.string.change_security_questions, "Change security questions") else tr(R.string.set_security_questions, "Set security questions"), "profile_recovery") { guarded { questions = true } }
                if (p.hasRecovery) ActionRow(tr(R.string.remove_security_questions, "Remove security questions"), "profile_recovery_remove") { guarded { Profiles.setRecovery(context, p.id, emptyList(), emptyList()); AppMessages.success(context, tr(R.string.security_questions_removed, "Security questions removed")) } }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.unlock_with_the_phones_lock), color = TextPrimary, fontSize = 14.sp)
                        Text(if (Privacy.canAuthenticate(context)) tr(R.string.fingerprint_face_or_screen_lock, "Fingerprint, face or screen lock") else tr(R.string.set_a_screen_lock_in_android, "Set a screen lock in Android settings first"), color = TextTertiary, fontSize = 12.sp)
                    }
                    Switch(p.biometric, onCheckedChange = { on ->
                        if (!on) Profiles.setBiometric(context, p.id, false)
                        else if (activity != null) Privacy.verify(activity, "Lock ${p.name}") { ok -> if (ok) Profiles.setBiometric(context, p.id, true) else AppMessages.error(context, "Not turned on") }
                    }, modifier = Modifier.testTag("profile_bio"))
                }
                if (!isCurrent && activity != null) ActionRow("Switch to ${p.name}", "profile_switch") { ProfileSession.open(activity, p.id) }
                if (p.id != Profiles.DEFAULT) ActionRow(tr(R.string.delete_profile, "Delete profile"), "profile_delete", danger = true) { confirmDelete = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
    if (questions) SecurityQuestionsDialog(p) { questions = false }
    if (rename) NameAndColourDialog("Rename", p.name, null, onDismiss = { rename = false }) { name, _ -> rename = false; Profiles.rename(context, p.id, name) }
    if (pinStep > 0) PinPadDialog(
        title = if (pinStep == 1) "New PIN for ${p.name}" else tr(R.string.repeat_the_pin, "Repeat the PIN"),
        subtitle = tr(R.string.t_4_to_8_digits, "4 to 8 digits"), error = pinError,
        onDismiss = { pinStep = 0 },
        onPin = { pin ->
            if (pinStep == 1) { firstPin = pin; pinError = null; pinStep = 2 }
            else if (pin != firstPin) { pinError = tr(R.string.the_pins_dont_match, "The PINs don't match"); pinStep = 1 }
            else {
                val askQuestions = ProfilePinGuard.askQuestionsAfterSet(settingFirst, p)
                Profiles.setPin(context, p.id, pin); pinStep = 0; AppMessages.success(context, "PIN set for ${p.name}")
                if (askQuestions) questions = true
            }
        },
    )
    guard?.let { action ->
        val title = "Current PIN for ${p.name}"
        if (activity != null) UnlockProfileDialog(activity, p, title = title, onDismiss = { guard = null }, onUnlocked = { guard = null; action() })
        else {
            var err by remember { mutableStateOf<String?>(null) }
            PinPadDialog(title = title, subtitle = tr(R.string.enter_the_pin, "Enter the PIN"), error = err, onDismiss = { guard = null }, onPin = { pin ->
                when (Profiles.checkPin(context, p.id, pin)) {
                    Profiles.PinCheck.OK, Profiles.PinCheck.NO_PIN -> { guard = null; action() }
                    Profiles.PinCheck.WRONG -> err = tr(R.string.wrong_pin, "Wrong PIN")
                    Profiles.PinCheck.LOCKED_OUT -> err = "Too many tries. Wait ${Profiles.waitText(Profiles.lockoutLeft(context, p.id))}"
                }
            })
        }
    }
    val deleteScope = androidx.compose.runtime.rememberCoroutineScope()
    if (confirmDelete) TsukiDialog(
        tone = DialogTone.DANGER,
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_2, p.name)) },
        text = { Text(stringResource(R.string.its_history_lists_favorites_private_downloads)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            val wasCurrent = isCurrent
            // F-19-EXT: the removal writes files (profile data, extension owners): off the main thread.
            deleteScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Profiles.remove(context, p.id) }
                onDismiss()
                if (wasCurrent && activity != null) dev.asfakul.yumeiro.backup.Restart.now(activity)
            }
        }, modifier = Modifier.testTag("profile_delete_confirm")) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ActionRow(label: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(label, color = if (danger) dev.asfakul.yumeiro.ui.theme.DangerTone else TextPrimary, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clip(yuRound(10)).clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag))
}

@Composable
private fun ToneRow(selected: AccentTone, onPick: (AccentTone) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AccentTone.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = t.label) { onPick(t) }
                    .semantics { this.selected = on }.testTag("profile_tone_${t.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(t.swatch).border(if (on) 3.dp else 0.dp, if (on) TextPrimary else Color.Transparent, CircleShape))
            }
        }
    }
}

@Composable
private fun NameAndColourDialog(title: String, initial: String, tone: AccentTone?, onDismiss: () -> Unit, onDone: (String, AccentTone) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf(tone ?: AccentTone.GOLD) }
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth().testTag("profile_name"))
                if (tone != null) { Spacer(Modifier.height(12.dp)); ToneRow(pick) { pick = it } }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name, pick) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("profile_name_ok")) { Text(stringResource(R.string.save), color = if (name.isNotBlank()) SpotifyGreen else TextTertiary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
