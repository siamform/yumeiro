/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/** Full-screen cover shown while [Privacy.locked]; asks for the fingerprint / device PIN. */
@Composable
fun AppLockGate(activity: Activity) {
    val locked by Privacy.locked.collectAsState()
    if (!locked) return
    LaunchedEffect(Unit) { Privacy.unlock(activity) }
    BackHandler { activity.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxSize().background(DarkBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("app_lock"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, tint = SpotifyGreen, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Yumeiro is locked", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Unlock with your fingerprint, face or screen lock", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Privacy.unlock(activity) }, shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Unlock") }
    }
}
