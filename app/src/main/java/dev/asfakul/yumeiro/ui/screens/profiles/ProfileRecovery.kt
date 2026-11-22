package dev.asfakul.yumeiro.ui.screens.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Suggestions the questions start with; each can be edited. */
internal val SUGGESTED_QUESTIONS = listOf(
    "What was the name of your first school?",
    "In which city was your mother born?",
    "What was your first pet's name?",
)

/**
 * F-19: sets a profile's three security questions. Answers are hashed at once (like the PIN), so the
 * dialog never shows them again; a profile with questions answers them on "Forgot?" instead of using
 * the phone's screen lock, which a child who knows the phone's lock could otherwise use.
 */
@Composable
internal fun SecurityQuestionsDialog(p: Profiles.Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val questions = remember { mutableStateListOf(*(p.recoveryQuestions.takeIf { it.size == Profiles.RECOVERY_QUESTIONS } ?: SUGGESTED_QUESTIONS).toTypedArray()) }
    val answers = remember { mutableStateListOf("", "", "") }
    val ok = Profiles.validRecovery(questions.toList(), answers.toList())
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text("Security questions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("If ${p.name}'s PIN is forgotten, these three answers remove it and keep everything else. Answers ignore capitals and extra spaces.", color = TextSecondary)
                for (i in 0 until Profiles.RECOVERY_QUESTIONS) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(questions[i], { questions[i] = it.take(80) }, label = { Text("Question ${i + 1}") }, modifier = Modifier.fillMaxWidth().testTag("recovery_q_$i"))
                    OutlinedTextField(answers[i], { answers[i] = it.take(60) }, singleLine = true, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth().testTag("recovery_a_$i"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Profiles.setRecovery(context, p.id, questions.toList(), answers.toList())) { AppMessages.success(context, "Security questions saved"); onDismiss() }
            }, enabled = ok, modifier = Modifier.testTag("recovery_save")) { Text("Save", color = if (ok) SpotifyGreen else TextTertiary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** F-19: "Forgot?" for a profile with security questions. All three must match; wrong answers count as wrong PINs. */
@Composable
internal fun RecoveryAnswerDialog(p: Profiles.Profile, onDismiss: () -> Unit, onOtherWay: () -> Unit, onCleared: () -> Unit) {
    val context = LocalContext.current
    val answers = remember { mutableStateListOf("", "", "") }
    var error by remember { mutableStateOf<String?>(null) }
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot the PIN?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Answer ${p.name}'s security questions to remove the PIN. Nothing else changes.", color = TextSecondary)
                p.recoveryQuestions.forEachIndexed { i, q ->
                    Spacer(Modifier.height(12.dp))
                    Text(q)
                    OutlinedTextField(answers[i], { answers[i] = it.take(60); error = null }, singleLine = true, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth().testTag("recovery_answer_$i"))
                }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), modifier = Modifier.testTag("recovery_error")) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOtherWay, modifier = Modifier.testTag("recovery_other")) { Text("I don't know the answers") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (Profiles.recoverWithAnswers(context, p.id, answers.toList())) {
                    Profiles.PinCheck.OK, Profiles.PinCheck.NO_PIN -> { AppMessages.success(context, "PIN removed for ${p.name}"); onCleared() }
                    Profiles.PinCheck.WRONG -> error = "The answers don't match"
                    Profiles.PinCheck.LOCKED_OUT -> error = "Too many tries. Wait ${Profiles.waitText(Profiles.lockoutLeft(context, p.id))}"
                }
            }, modifier = Modifier.testTag("recovery_ok")) { Text("Remove PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
