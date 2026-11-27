/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.notify.Reminders
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.pageBackground
import dev.asfakul.yumeiro.ui.theme.yuRound

/** Public payment details for supporting the developer (shown in-app; nothing is sent anywhere). */
object SupportInfo {
    data class Entry(val label: String, val value: String, val note: String? = null)
    data class Group(val title: String, val icon: ImageVector, val entries: List<Entry>, val warning: String? = null)

    val groups = listOf(
        Group(tr(R.string.mobile_banking_bangladesh, "Mobile banking (Bangladesh)"), Icons.Default.PhoneAndroid, listOf(
            Entry("bKash", "01734737294", tr(R.string.personal_send_money, "Personal · Send Money")),
            Entry(tr(R.string.nagad, "Nagad"), "01734737294", tr(R.string.personal_send_money, "Personal · Send Money")),
            Entry(tr(R.string.rocket, "Rocket"), "017347372946", tr(R.string.personal_send_money, "Personal · Send Money")),
        )),
        Group(tr(R.string.bank_transfer, "Bank transfer"), Icons.Default.AccountBalance, listOf(
            Entry(tr(R.string.bank, "Bank"), tr(R.string.dutch_bangla_bank, "DUTCH-BANGLA BANK")),
            Entry(tr(R.string.account_name, "Account name"), tr(R.string.asfakul_islam, "ASFAKUL ISLAM")),
            Entry(tr(R.string.account_number, "Account number"), "2067348739614"),
            Entry(tr(R.string.routing_number, "Routing number"), "090270608"),
        )),
        Group("PayPal", Icons.Default.Payments, listOf(
            Entry("PayPal", "asfakulsiam0@gmail.com", "Send as Friends & Family if you can"),
        )),
        Group("Crypto", Icons.Default.CurrencyBitcoin, listOf(
            Entry(tr(R.string.btc_bnb_usdt, "BTC · BNB · USDT"), "0x17a2dd19ce38b5bbb22e25c526a1c89522fdda88", tr(R.string.bnb_smart_chain_bep_20_only, "BNB Smart Chain (BEP-20) only")),
            Entry(tr(R.string.trx_usdt, "TRX · USDT"), "TSfzZBBu4LAeZBZYSjjowTyAZ88jiM5gEr", tr(R.string.tron_trc_20_only, "TRON (TRC-20) only")),
        ), warning = tr(R.string.pick_the_matching_network_when_you, "Pick the matching network when you send. Coins sent on another network (for example BTC on the Bitcoin network) can't be recovered.")),
    )
}

private fun copy(c: Context, label: String, value: String) {
    c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
    AppMessages.success(c, "$label copied")
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().pageBackground().testTag("support_screen")) {
        Column(Modifier.fillMaxSize()) {
            SubPageHeader(title = tr(R.string.buy_me_a_coffee, "Buy me a coffee"), subtitle = tr(R.string.support_yumeiro, "Support Yumeiro"), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(dev.asfakul.yumeiro.ui.components.ScrollMemory.scroll("support")).padding(horizontal = 16.dp).padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SupportYumeiroCard(full = true, showCoffee = false)
                Text(stringResource(R.string.tap_a_detail_to_copy_it), color = TextSecondary, fontSize = 12.sp)
                SupportInfo.groups.forEach { g -> GroupCard(g) { e -> copy(context, e.label, e.value) } }
                Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.other_ways_to_help), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.a_star_on_github_or_a), color = TextSecondary, fontSize = 12.sp)
                        Row(Modifier.padding(top = 4.dp)) {
                            TextButton(onClick = { if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context) }, modifier = Modifier.testTag("support_star")) {
                                Icon(Icons.Default.Star, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.star_on_github), color = SpotifyGreen)
                            }
                        }
                        Row {
                            Reminders.Social.entries.forEach { s ->
                                TextButton(onClick = { if (Links.open(context, s.url)) Reminders.recordFollow(context, s) }) { Text(s.label, color = SpotifyGreen, fontSize = 13.sp) }
                            }
                            TextButton(onClick = { Links.open(context, Reminders.WEBSITE_URL) }, modifier = Modifier.testTag("support_website")) { Text(stringResource(R.string.yumeiro_click), color = SpotifyGreen, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(g: SupportInfo.Group, onCopy: (SupportInfo.Entry) -> Unit) {
    Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("support_group")) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(g.icon, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(g.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            g.entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.label, color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(e.value, color = TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        e.note?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                    }
                    IconButton(onClick = { onCopy(e) }, modifier = Modifier.testTag("support_copy")) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.copy, e.label), tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            g.warning?.let {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
