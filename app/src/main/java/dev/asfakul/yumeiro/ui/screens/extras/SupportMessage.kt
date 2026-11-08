/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.notify.Reminders
import dev.asfakul.yumeiro.ui.screens.settings.DeveloperInfo
import dev.asfakul.yumeiro.ui.theme.BrandCoral
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/**
 * The one place the "why support Yumeiro" words live, so the support page, Settings, About and
 * the reminder card all say the same honest thing: giving is optional, the app stays free.
 */
object SupportMessage {
    const val TITLE = "Yumeiro is free. Keeping it alive isn't."
    const val OPTIONAL = "Donating is never required. Yumeiro stays free for everyone, with no ads and no tracking, whether you give or not."
    val paragraphs = listOf(
        OPTIONAL,
        "We all spend a little on things that simply make the day nicer: a coffee, a snack, one more subscription. " +
            "If Yumeiro has given you a good evening, a song on repeat or just one more episode, maybe it has earned a coffee too.",
        "Yumeiro is still in active development. ${DeveloperInfo.NAME} and the small Yumeiro team build it in their own time, " +
            "with no funding, and keeping the community going is a real struggle.",
        "Your support keeps the community alive, keeps our developer motivated and keeps new features coming. " +
            "Can't donate? A star on GitHub costs nothing and means a lot. Thank you for being here and for loving Yumeiro.",
    )
    const val SHORT = "Donating is never required, and Yumeiro will always be free with no ads. But it's still in development, " +
        "and the developer and the Yumeiro team keep it alive on their own. If it has earned a coffee, it would mean a lot."
    const val STAR = "A star costs nothing, helps more people find Yumeiro and tells our developer the late nights are worth it."
}

private val CoffeeYellow = Color(0xFFFFDD00)

/**
 * Support card. [full] shows every paragraph (support page, About); the compact form is the
 * Settings home banner. Both offer Buy me a coffee and Star on GitHub.
 */
@Composable
fun SupportYumeiroCard(modifier: Modifier = Modifier, full: Boolean = false, showCoffee: Boolean = true) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(BrandGold.copy(alpha = 0.20f), BrandCoral.copy(alpha = 0.10f), GlassFill)))
            .border(1.dp, Brush.linearGradient(listOf(BrandGold.copy(alpha = 0.55f), BrandCoral.copy(alpha = 0.18f))), shape)
            .padding(16.dp)
            .testTag(if (full) "support_message_full" else "support_message"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, null, tint = BrandCoral, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(SupportMessage.TITLE, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (full) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportMessage.paragraphs.forEach { Text(it, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        } else {
            Text(SupportMessage.SHORT, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (showCoffee) Button(
                onClick = { AppPages.open(AppPages.Page.SUPPORT) },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeYellow, contentColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).testTag("support_card_coffee"),
            ) {
                Icon(Icons.Default.Coffee, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Buy me a coffee", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = { if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context) },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).testTag("support_card_star"),
            ) {
                Icon(Icons.Default.Star, null, tint = BrandGold, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Star on GitHub", color = TextPrimary, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
