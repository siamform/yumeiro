/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.update.ui.UpdateSettingsCard
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkOutlineVariant
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClearWatchHistory: () -> Unit = {},
    onClearListeningHistory: () -> Unit = {},
) {
    BackHandler { onBackClick() }
    var showGuide by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
    var gaplessPlayback by remember { mutableStateOf(true) }
    var bassBoost by remember { mutableStateOf(true) }
    var autoScanOnLaunch by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("Spotify Flat") }
    var showPresetMenu by remember { mutableStateOf(false) }

    val eqPresets = listOf("Spotify Flat", "Bass Boosted", "Vocal Clarity", "Rock & Metal", "Electronic Dance", "Acoustic")

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_root"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                UserGuideEntryCard(onOpen = { showGuide = true })
            }
            item {
                SettingsSectionHeader(title = "Appearance", icon = Icons.Default.Palette)
                Spacer(modifier = Modifier.height(8.dp))
                AppearanceSettingsCard()
            }

            // Audio & Equalizer Section
            item {
                SettingsSectionHeader(title = "Audio & Playback", icon = Icons.Default.MusicNote)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassFill),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Equalizer Profile & Sound FX Navigator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToEqualizer() }
                                .padding(vertical = 8.dp)
                                .testTag("setting_equalizer_selector"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Advanced Equalizer & Sound FX",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Customize 5 frequency bands, presets, bass boost & 3D virtualizer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpotifyGreen
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Open Equalizer",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Bass Boost Engine",
                            subtitle = "Enhance low frequencies with dynamic punch",
                            checked = bassBoost,
                            onCheckedChange = { bassBoost = it },
                            testTag = "toggle_bass_boost"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Gapless Playback",
                            subtitle = "Eliminate silent delays between tracks",
                            checked = gaplessPlayback,
                            onCheckedChange = { gaplessPlayback = it },
                            testTag = "toggle_gapless"
                        )
                    }
                }
            }

            // Video Playback Section
            item {
                SettingsSectionHeader(title = "Video Player", icon = Icons.Default.Videocam)
                Spacer(modifier = Modifier.height(8.dp))
                VideoPlayerSettingsCard()
            }

            // Online videos
            item {
                SettingsSectionHeader(title = "Online videos", icon = Icons.Default.Videocam)
                Spacer(modifier = Modifier.height(8.dp))
                OnlineSettingsCard()
            }

            // Network (DNS) Section
            item {
                SettingsSectionHeader(title = "Network", icon = Icons.Default.Dns)
                Spacer(modifier = Modifier.height(8.dp))
                NetworkSettingsCard()
            }

            // Storage & Library Scanner Section
            item {
                SettingsSectionHeader(title = "Storage & Media Scanner", icon = Icons.Default.Folder)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassFill),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SettingsToggleRow(
                            title = "Scan Media on Launch",
                            subtitle = "Automatically index new tracks and videos",
                            checked = autoScanOnLaunch,
                            onCheckedChange = { autoScanOnLaunch = it },
                            testTag = "toggle_auto_scan"
                        )

                        SettingsDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onRescanClick() }
                                .padding(vertical = 8.dp)
                                .testTag("setting_manual_rescan"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Force Media Rescan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Re-index device storage right now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Notifications (inbox, per-kind switches, sound, reminders)
            item {
                SettingsSectionHeader(title = "Notifications", icon = Icons.Default.Notifications)
                Spacer(modifier = Modifier.height(8.dp))
                NotificationSettingsCard()
            }

            item {
                SettingsSectionHeader(title = "Background activity", icon = Icons.Default.Sync)
                Spacer(modifier = Modifier.height(8.dp))
                BackgroundActivityCard()
            }

            item {
                SettingsSectionHeader(title = "Permissions", icon = Icons.Default.Shield)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionsCard(onMediaGranted = onRescanClick)
            }

            item {
                SettingsSectionHeader(title = "Privacy & data", icon = Icons.Default.Lock)
                Spacer(modifier = Modifier.height(8.dp))
                PrivacyDataCard(onClearWatchHistory = onClearWatchHistory, onClearListeningHistory = onClearListeningHistory)
            }

            // App Updates (GitHub Releases updater)
            item {
                SettingsSectionHeader(title = "App Updates", icon = Icons.Default.SystemUpdate)
                Spacer(modifier = Modifier.height(8.dp))
                UpdateSettingsCard()
            }

            // About & System Info
            item {
                SettingsSectionHeader(title = "About & Developer", icon = Icons.Default.Info)
                Spacer(modifier = Modifier.height(8.dp))
                DeveloperCard()
            }

            item {
                SettingsSectionHeader(title = "Social media", icon = Icons.Default.People)
                Spacer(modifier = Modifier.height(8.dp))
                SocialMediaCard()
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
    if (showGuide) UserGuideScreen(onBack = { showGuide = false })
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SpotifyGreen,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(0.5.dp)
            .background(DarkOutlineVariant)
    )
}
