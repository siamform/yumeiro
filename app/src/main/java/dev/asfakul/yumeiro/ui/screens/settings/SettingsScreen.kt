/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.material.icons.outlined.AutoAwesome
import dev.asfakul.yumeiro.ui.theme.legibleOnTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.tracking.Kitsu
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.YumeiroMark
import dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category
import dev.asfakul.yumeiro.ui.theme.BrandAmber
import dev.asfakul.yumeiro.ui.theme.BrandCoral
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.update.ui.UpdateSettingsCard
import dev.asfakul.yumeiro.youtube.YtAccount

/**
 * Settings home: a search box, a quick health strip and five groups of categories. Each category
 * opens its own focused page (inside this screen) or an existing full screen via [onOpenPage].
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClearWatchHistory: () -> Unit = {},
    onClearListeningHistory: () -> Unit = {},
    onOpenPage: (String) -> Unit = {},
) {
    // A category requested from elsewhere (setup guide → Network / Notifications) opens directly.
    var openName by rememberSaveable { mutableStateOf<String?>(SettingsLaunch.take()) }
    val launchRequest by SettingsLaunch.pending.collectAsState()
    LaunchedEffect(launchRequest) { launchRequest?.let { openName = it; SettingsLaunch.consume(it) } }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val open = SettingsCatalog.fromName(openName)

    fun go(c: Category) {
        when (val r = c.route) {
            null -> openName = c.name
            "guide" -> showGuide = true
            "setup" -> dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show()
            else -> onOpenPage(r)
        }
    }

    Box(modifier.fillMaxSize().testTag("settings_screen_root")) {
        AnimatedContent(
            targetState = open,
            transitionSpec = {
                if (targetState != null) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                else (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            },
            label = "settings_page",
        ) { page ->
            if (page == null) SettingsHome(onBack = onBackClick, onOpen = ::go)
            else CategoryPage(
                page, onBack = { openName = null },
                onRescanClick = onRescanClick, onNavigateToEqualizer = onNavigateToEqualizer,
                onClearWatchHistory = onClearWatchHistory, onClearListeningHistory = onClearListeningHistory,
                onOpenPage = onOpenPage, onOpenCategory = ::go,
            )
        }
        if (showGuide) UserGuideScreen(onBack = { showGuide = false })
    }
}

// region look of each category

internal data class CategoryLook(val icon: ImageVector, val tint: Color)

private val Rose = Color(0xFFFF8FA3)
private val Violet = Color(0xFFB59CFF)
private val Sky = Color(0xFF8EC5FF)
private val Teal = Color(0xFF6FD6C2)
private val Mint = Color(0xFF9BE59B)
private val YtRed = Color(0xFFFF4E45)

internal fun Category.look(): CategoryLook = when (this) {
    Category.APPEARANCE -> CategoryLook(Icons.Default.Palette, Rose)
    Category.NAVIGATION -> CategoryLook(Icons.Default.ViewWeek, Sky)
    Category.PLAYER -> CategoryLook(Icons.Default.PlayCircle, BrandGold)
    Category.AUDIO -> CategoryLook(Icons.Default.GraphicEq, BrandAmber)
    Category.ONLINE -> CategoryLook(Icons.Default.Videocam, BrandCoral)
    Category.DOWNLOADS -> CategoryLook(Icons.Default.Download, Teal)
    Category.LIBRARY -> CategoryLook(Icons.Default.Folder, Mint)
    Category.SOURCES -> CategoryLook(Icons.Default.Extension, Violet)
    Category.SOURCE_STATUS -> CategoryLook(Icons.Default.MonitorHeart, Mint)
    Category.TRACKING -> CategoryLook(Icons.Default.SyncAlt, Sky)
    Category.YOUTUBE -> CategoryLook(Icons.Default.AccountCircle, YtRed)
    Category.TOGETHER -> CategoryLook(Icons.Default.Groups, Rose)
    Category.NOTIFICATIONS -> CategoryLook(Icons.Default.Notifications, BrandGold)
    Category.BACKGROUND -> CategoryLook(Icons.Default.BatteryChargingFull, Mint)
    Category.NETWORK -> CategoryLook(Icons.Default.Dns, Sky)
    Category.PRIVACY -> CategoryLook(Icons.Default.Shield, Violet)
    Category.BACKUP -> CategoryLook(Icons.Default.Restore, Teal)
    Category.UPDATES -> CategoryLook(Icons.Default.SystemUpdate, BrandAmber)
    Category.SETUP -> CategoryLook(Icons.Filled.Explore, BrandGold)
    Category.GUIDE -> CategoryLook(Icons.AutoMirrored.Filled.MenuBook, BrandGold)
    Category.ABOUT -> CategoryLook(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, BrandCoral)
}

/** The page's Japanese name, shown as a watermark on its banner. */
internal fun Category.kana(): String = when (this) {
    Category.APPEARANCE -> "彩"; Category.NAVIGATION -> "道"; Category.PLAYER -> "映"; Category.AUDIO -> "音"
    Category.ONLINE -> "網"; Category.DOWNLOADS -> "保存"; Category.LIBRARY -> "蔵"; Category.SOURCES -> "源"
    Category.SOURCE_STATUS -> "診"; Category.TRACKING -> "記録"; Category.YOUTUBE -> "動画"; Category.TOGETHER -> "共"
    Category.NOTIFICATIONS -> "通知"; Category.BACKGROUND -> "電池"; Category.NETWORK -> "接続"; Category.PRIVACY -> "鍵"
    Category.BACKUP -> "控"; Category.UPDATES -> "新"; Category.SETUP -> "始"; Category.GUIDE -> "案内"; Category.ABOUT -> "夢色"
}

/** Live status shown as a chip on a category row (null = no chip). */
private data class Status(val text: String, val ok: Boolean?)

@Composable
private fun rememberStatuses(): Map<Category, Status> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    remember(context) { NetworkSettings.init(context); YtAccount.init(context); 0 }
    val dns by NetworkSettings.dns.collectAsState()
    val yt by YtAccount.state.collectAsState()
    val lock by Privacy.lockEnabled.collectAsState()
    val incognito by Privacy.incognito.collectAsState()
    return remember(tick, dns, yt, lock, incognito) {
        val notif = PermissionCenter.notificationsAllowed(context)
        val battery = PermissionCenter.batteryUnrestricted(context)
        val linked = listOf(AniList.signedIn, MyAnimeList.signedIn, Kitsu.signedIn, Trakt.signedIn).count { it }
        buildMap {
            put(Category.NOTIFICATIONS, if (notif) Status("On", true) else Status("Off", false))
            put(Category.BACKGROUND, if (battery) Status("Unrestricted", true) else Status("Optimised", false))
            put(Category.NETWORK, Status(if (dns.usesAlternative) dns.provider.label else if (dns.auto) "Auto" else "System", if (dns.auto || dns.usesAlternative) true else null))
            if (linked > 0) put(Category.TRACKING, Status("$linked linked", true))
            if (yt.signedIn) put(Category.YOUTUBE, Status("Signed in", true))
            listOfNotNull(if (lock) "Locked" else null, if (incognito) "Incognito" else null).takeIf { it.isNotEmpty() }
                ?.let { put(Category.PRIVACY, Status(it.joinToString(" · "), true)) }
        }
    }
}

// endregion

// region home

@Composable
private fun SettingsHome(onBack: () -> Unit, onOpen: (Category) -> Unit) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val statuses = rememberStatuses()
    val results = remember(query) { SettingsCatalog.search(query) }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Settings", onBack = onBack, subtitle = "Yumeiro ${BuildConfig.VERSION_NAME}", handleBack = false, modifier = Modifier.testTag("settings_back_button")) {
            IconButton(onClick = { onOpen(Category.GUIDE) }, modifier = Modifier.testTag("open_user_guide")) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, "User guide", tint = BrandGold)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("settings_home"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("settings_home"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hero") { SettingsHero(query = query, onQuery = { query = it }) }

            if (query.isNotBlank()) {
                item("results") {
                    if (results.isEmpty()) Text("No settings match “$query”. Try a shorter word.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(8.dp).testTag("settings_no_results"))
                    else GlassGroup(Modifier.testTag("settings_results")) { CategoryList(results, statuses, onOpen) }
                }
                return@LazyColumn
            }

            item("health") { HealthStrip(statuses, onOpen) }

            SettingsCatalog.byGroup().forEach { (group, cats) ->
                item("g_${group.name}") {
                    Column {
                        GroupLabel(group.label)
                        GlassGroup(Modifier.testTag("settings_group_${group.name.lowercase()}")) { CategoryList(cats, statuses, onOpen) }
                    }
                }
                // Support sits between the everyday groups and the account/system ones, where people scroll past it.
                if (group == SettingsCatalog.Group.WATCH) item("support") { dev.asfakul.yumeiro.ui.screens.extras.SupportYumeiroCard(Modifier.padding(top = 6.dp)) }
            }
            item("made_in") { MadeInBangladesh(Modifier.fillMaxWidth().padding(top = 8.dp)) }
        }
    }
}

/**
 * Top of Settings: the 設定 overline, a short promise and the search field, always visible (a
 * hidden search toggle was easy to miss).
 */
@Composable
private fun SettingsHero(query: String, onQuery: (String) -> Unit) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeLg
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = if (p.isDark) 0.16f else 0.12f), BrandGold.copy(alpha = 0.06f), Color.Transparent)))
            .border(1.dp, dev.asfakul.yumeiro.ui.components.rememberMoonHairline(), shape)
            .padding(16.dp).testTag("settings_hero_home"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YumeiroMark(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dev.asfakul.yumeiro.ui.components.kanaOverline("設定", "Settings"), style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, maxLines = 1)
                Text("Make Yumeiro yours", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        dev.asfakul.yumeiro.ui.components.YuSearchField(query = query, onQuery = onQuery, placeholder = "Search settings", tag = "settings_search")
    }
}

@Composable
private fun CategoryList(cats: List<Category>, statuses: Map<Category, Status>, onOpen: (Category) -> Unit) {
    cats.forEachIndexed { i, c ->
        if (i > 0) RowDivider()
        val look = c.look()
        val s = statuses[c]
        CategoryRow(look.icon, look.tint.legibleOnTheme(), c.title, c.summary, "settings_cat_${c.name.lowercase()}", s?.text, s?.ok) { onOpen(c) }
    }
}

/** Three tiles that answer "is anything wrong?" at a glance: notifications, battery, network. */
@Composable
private fun HealthStrip(statuses: Map<Category, Status>, onOpen: (Category) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Category.NOTIFICATIONS, Category.BACKGROUND, Category.NETWORK).forEach { c ->
            val s = statuses[c]
            val look = c.look()
            val warn = s?.ok == false
            val warnColor = dev.asfakul.yumeiro.ui.theme.WarnTone
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(look.tint.copy(alpha = if (warn) 0.10f else 0.16f), GlassFill)))
                    .border(1.dp, if (warn) warnColor.copy(alpha = 0.55f) else BrandGold.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    .clickable { onOpen(c) }.padding(12.dp).testTag("settings_health_${c.name.lowercase()}"),
            ) {
                Icon(look.icon, null, tint = if (warn) warnColor else look.tint.legibleOnTheme(), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(when (c) { Category.BACKGROUND -> "Battery"; else -> c.title }, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                Text(s?.text ?: "—", color = if (warn) warnColor else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// endregion

// region category pages

@Composable
private fun CategoryPage(
    c: Category, onBack: () -> Unit,
    onRescanClick: () -> Unit, onNavigateToEqualizer: () -> Unit,
    onClearWatchHistory: () -> Unit, onClearListeningHistory: () -> Unit,
    onOpenPage: (String) -> Unit, onOpenCategory: (Category) -> Unit,
) {
    val look = c.look()
    Column(Modifier.fillMaxSize().testTag("settings_page_${c.name.lowercase()}")) {
        SubPageHeader(title = c.title, onBack = onBack, subtitle = c.group.label)
        LazyColumn(
            Modifier.fillMaxSize(), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("settings_" + c.name),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hero") { CategoryHero(look.icon, look.tint.legibleOnTheme(), c.title, c.summary, c.kana()) }
            when (c) {
                Category.APPEARANCE -> item("c") { AppearanceSettingsCard() }
                Category.PLAYER -> {
                    item("c") { VideoPlayerSettingsCard() }
                    item("info") { GroupLabel("Player info overlay"); PlayerInfoOverlayCard() }
                }
                Category.NAVIGATION -> item("c") { NavigationBarSettingsCard() }
                Category.AUDIO -> {
                    item("qa") { GroupLabel("Video"); QualityAndAudioEntry() }
                    item("q") { GroupLabel("Quality"); AudioQualityCard() }
                    item("c") {
                        GroupLabel("Sound")
                        SettingsCard {
                        SettingsActionRow("Equalizer & sound effects", "5 bands, presets, bass boost and 3D virtualizer", "setting_equalizer_selector") { onNavigateToEqualizer() }
                        Text("Songs already play back-to-back with no gap, and Yumeiro keeps playing with the screen off.",
                            color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
                Category.ONLINE -> {
                    item("c") { OnlineSettingsCard() }
                    item("more") {
                        SettingsCard {
                            SettingsActionRow("Tracking", "AniList, MyAnimeList, Kitsu and Trakt sign-in and setup guides", "online_open_tracking") { onOpenPage("tracking") }
                            SettingsDivider()
                            SettingsActionRow("Sources", "Add extensions, add-ons and home servers", "online_open_sources") { onOpenPage("sources") }
                        }
                    }
                }
                Category.DOWNLOADS -> item("c") { DownloadSettingsCard() }
                Category.LIBRARY -> item("c") {
                    SettingsCard {
                        SettingsActionRow("Rescan this device", "Look for new music and videos right now", "setting_manual_rescan") { onRescanClick() }
                        Text("Yumeiro reads Android's media index every time it opens, so new files usually appear on their own.",
                            color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
                Category.NOTIFICATIONS -> {
                    item("c") { NotificationSettingsCard() }
                    item("push") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GroupLabel("On-time alerts")
                            PushAlertsCard()
                            GuideCard(SetupGuides.unifiedPush)
                        }
                    }
                }
                Category.BACKGROUND -> item("c") { BackgroundActivityCard() }
                Category.NETWORK -> {
                    item("c") { NetworkSettingsCard() }
                    item("torrent") { GroupLabel("Torrent streaming"); TorrServerCard() }
                }
                Category.PRIVACY -> {
                    item("lock") {
                        GroupLabel("Lock & incognito")
                        val incog by Privacy.incognito.collectAsState()
                        val lock by Privacy.lockEnabled.collectAsState()
                        SettingsCard {
                            SettingsActionRow("App lock & incognito", "Fingerprint or screen lock, pause history, hide from recents", "settings_privacy",
                                status = listOfNotNull(if (lock) "Locked" else null, if (incog) "Incognito" else null).joinToString(" · ").ifBlank { null }) { onOpenPage("privacy") }
                        }
                    }
                    item("perm") { GroupLabel("Permissions"); PermissionsCard(onMediaGranted = onRescanClick) }
                    item("data") { GroupLabel("Clear data"); PrivacyDataCard(onClearWatchHistory = onClearWatchHistory, onClearListeningHistory = onClearListeningHistory) }
                }
                Category.UPDATES -> item("c") { UpdateSettingsCard() }
                Category.ABOUT -> {
                    item("dev") { DeveloperCard() }
                    item("social") { GroupLabel("Community"); SocialMediaCard() }
                    item("support") { GroupLabel("Support Yumeiro"); dev.asfakul.yumeiro.ui.screens.extras.SupportYumeiroCard(full = true) }
                    item("guide") { UserGuideEntryCard(onOpen = { onOpenCategory(Category.GUIDE) }) }
                }
                // Categories with their own screens never open here; offer the jump just in case.
                else -> item("c") {
                    SettingsCard { SettingsActionRow("Open ${c.title}", c.summary, "settings_open_${c.name.lowercase()}") { onOpenCategory(c) } }
                }
            }
        }
    }
}

// endregion

// region shared rows (used by every settings card)

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = if (enabled) TextPrimary else TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(TextTertiary.copy(alpha = 0.16f)))
}

// endregion

/** One-shot request to open Settings on a given [SettingsCatalog.Category] (by enum name). */
object SettingsLaunch {
    // Round 8: a StateFlow instead of a one-shot field. Settings is reused from the back stack
    // (SubNav pops back to it), so a request made while it already existed was never read and
    // shortcuts like Home → Theme / DNS landed on the Settings list instead of the category.
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<String?> = _pending
    fun request(category: SettingsCatalog.Category) { _pending.value = category.name }
    fun take(): String? = _pending.value.also { _pending.value = null }
    fun consume(name: String) { _pending.compareAndSet(name, null) }
}
