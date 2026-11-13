/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.pageBackground

/** One guide chapter: an illustration, a short intro and numbered steps. */
private data class GuideChapter(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val steps: List<String>,
    val tip: String? = null,
    val diagram: @Composable () -> Unit,
)

/** Settings entry that opens the guide. */
@Composable
fun UserGuideEntryCard(onOpen: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.glass)
            .clickable(onClick = onOpen).padding(16.dp).testTag("open_user_guide"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = p.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("How Yumeiro works", color = p.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Illustrated guide to every section, gesture and fix", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, null, tint = p.textSecondary)
    }
}

@Composable
fun UserGuideScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = LocalMtPalette.current
    val chapters = guideChapters()
    Box(Modifier.fillMaxSize().pageBackground().testTag("user_guide_screen")) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("user_guide"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(p.glass).clickable(onClick = onBack).testTag("user_guide_back"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("User guide", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("How Yumeiro works", color = p.textPrimary, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            item(key = "map") { AppMapCard() }
            items(chapters, key = { it.key }) { ChapterCard(it) }
            item(key = "foot") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    MadeInBangladesh()
                }
            }
        }
    }
}

// ---- Chapter card ---------------------------------------------------------------------------------

@Composable
private fun ChapterCard(c: GuideChapter) {
    val p = LocalMtPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.glass)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(16.dp).testTag("guide_${c.key}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(c.icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(c.title, color = p.textPrimary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = if (p.isDark) 0.35f else 0.06f)).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) { c.diagram() }
        Spacer(Modifier.height(12.dp))
        Text(c.intro, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        c.steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = p.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(s, color = p.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        if (c.tip != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.10f)).padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Lightbulb, null, tint = p.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.tip, color = p.textPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---- Diagram building blocks ------------------------------------------------------------------------

@Composable
private fun Block(label: String, icon: ImageVector, modifier: Modifier = Modifier, strong: Boolean = false) {
    val p = LocalMtPalette.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (strong) p.accent else Color.White.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (strong) p.onAccent else p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (strong) p.onAccent else p.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 12.sp, maxLines = 2)
    }
}

@Composable
private fun RowScope.Arrow() {
    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = LocalMtPalette.current.textSecondary, modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
}

/** A horizontal flow: block → block → block. */
@Composable
private fun Flow(vararg steps: Pair<String, ImageVector>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (label, icon) ->
            Block(label, icon, Modifier.weight(1f), strong = i == steps.lastIndex)
            if (i != steps.lastIndex) Arrow()
        }
    }
}

@Composable
private fun Bar(fraction: Float, height: Int = 8, alpha: Float = 0.12f, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(fraction).height(height.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = alpha)))
}

/** Bottom navigation map: the five tabs and what lives in each. */
@Composable
private fun AppMapCard() {
    val p = LocalMtPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.glass).padding(16.dp).testTag("guide_map"),
    ) {
        Text("The app at a glance", color = p.textPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Four tabs in the floating dock at the bottom. The bar above it is the music player.", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        // Mock dock
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = if (p.isDark) 0.35f else 0.06f)).padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp, 16.dp, 6.dp, 6.dp)).background(Color.White.copy(alpha = 0.08f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.5f)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Bar(0.6f, 8, 0.3f); Spacer(Modifier.height(4.dp)); Bar(0.4f, 6) }
                Box(Modifier.size(28.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp, 6.dp, 16.dp, 16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(Icons.Default.Home to "Home", Icons.Default.MusicNote to "Music", Icons.Default.VideoLibrary to "Videos", Icons.Default.LibraryMusic to "Library").forEach { (i, l) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(i, null, tint = p.accent, modifier = Modifier.size(18.dp))
                        Text(l, color = p.textPrimary, fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        listOf(
            "Home" to "Continue, new episodes, Spaces, moods, most played and an endless Yumix random",
            "Music" to "Songs, albums, artists, folders and YouTube Music",
            "Videos" to "Yumix (extensions), YouTube, device videos and Live TV — links, IPTV and the playlist market live in Live TV",
            "Library" to "Your collection overview, playlists, favourites and history",
        ).forEach { (t, d) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(t, color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Text(d, color = p.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The video player with its gesture zones. */
@Composable
private fun GestureDiagram() {
    val p = LocalMtPalette.current
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            Zone("Brightness\nswipe up/down", Icons.Default.BrightnessMedium, p.accent.copy(alpha = 0.18f))
            Zone("Swipe down\nto shrink", Icons.Default.KeyboardArrowDown, Color.White.copy(alpha = 0.06f))
            Zone("Volume\nswipe up/down", Icons.AutoMirrored.Filled.VolumeUp, p.accent.copy(alpha = 0.18f))
        }
        Text(
            "← drag sideways to seek →   ·   double-tap a side: ±10 s",
            color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun RowScope.Zone(label: String, icon: ImageVector, tint: Color) {
    Column(
        Modifier.weight(1f).fillMaxHeight().background(tint).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}

/** Mock of the home page sections. */
@Composable
private fun HomeDiagram() {
    val p = LocalMtPalette.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Continue", color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockTile(Modifier.weight(1f), Icons.Default.MusicNote, "Last song")
            MockTile(Modifier.weight(1f), Icons.Default.SmartDisplay, "Last video")
        }
        Text("Spaces", color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Icons.Default.MusicNote, Icons.Default.Equalizer, Icons.Default.Extension, Icons.Default.LiveTv).forEach {
                Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(it, null, tint = p.accent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text("Yumix random  ·  keeps loading as you scroll ↓", color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        repeat(2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = 0.35f)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Bar(0.7f, 6, 0.25f); Spacer(Modifier.height(3.dp)); Bar(0.4f, 5) }
            }
        }
    }
}

@Composable
private fun MockTile(modifier: Modifier, icon: ImageVector, label: String) {
    val p = LocalMtPalette.current
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = p.textPrimary, fontSize = 10.sp)
    }
}

/** Floating player: the picture shrinks into a corner while you browse. */
@Composable
private fun FloatingDiagram() {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhoneMock(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            }
            Column(Modifier.padding(6.dp)) { Bar(0.8f, 6, 0.25f); Spacer(Modifier.height(4.dp)); Bar(0.5f, 5) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = p.accent)
            Text("Back or\nswipe down", color = p.textSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
        PhoneMock(Modifier.weight(1f)) {
            Column(Modifier.padding(6.dp)) { repeat(4) { Bar(0.9f, 6); Spacer(Modifier.height(5.dp)) } }
            Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.BottomEnd) {
                Box(Modifier.width(52.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp)).background(Color.Black).border(1.dp, p.accent, RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun PhoneMock(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.aspectRatio(0.62f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun guideChapters(): List<GuideChapter> = listOf(
    GuideChapter(
        "home", Icons.Default.Home, "Home",
        "Your starting point. It remembers what you played last and keeps suggesting more.",
        listOf(
            "Tap the song or video card under Continue to pick up where you stopped.",
            "Spaces jump to every part of Yumeiro. Eight show at first; tap Show all for the rest.",
            "Mood of the hour changes with the time of day; tap Shuffle for another. Yumix random at the bottom never ends.",
        ),
        tip = "Customize home (at the bottom) turns sections on or off. Search, alerts and Settings sit in the top-right corner.",
    ) { HomeDiagram() },
    GuideChapter(
        "music", Icons.Default.MusicNote, "Music & Now Playing",
        "Everything on your phone plus YouTube Music, with lyrics, equalizer and a sleep timer.",
        listOf(
            "Open Music and tap any song. The music bar appears above the dock — tap it for Now Playing.",
            "In Now Playing use Lyrics, Up next and Info at the bottom. Tap a lyric line to jump to it.",
            "Tap the moon icon at the top to set a sleep timer (5 min to 1.5 h, or end of song).",
        ),
        tip = "The music fades out gently over the last 20 seconds of a sleep timer.",
    ) { Flow("Pick a song" to Icons.Default.MusicNote, "Music bar" to Icons.Default.PlayArrow, "Lyrics · EQ" to Icons.Default.Lyrics, "Sleep timer" to Icons.Default.Bedtime) },
    GuideChapter(
        "player", Icons.Default.TouchApp, "Video player gestures",
        "The player is controlled with simple swipes on the picture.",
        listOf(
            "Swipe up or down on the left side for brightness, on the right side for volume.",
            "Drag sideways to seek; double-tap the left or right side to jump 10 seconds.",
            "Swipe down from the middle to shrink the video (or leave fullscreen).",
        ),
    ) { GestureDiagram() },
    GuideChapter(
        "floating", Icons.Default.SmartDisplay, "Floating player & picture-in-picture",
        "Keep watching while you browse — just like YouTube.",
        listOf(
            "Press Back while a video plays: it pops out into picture-in-picture.",
            "Hold the player and swipe down: it shrinks into a small floating player inside Yumeiro.",
            "Drag the floating player anywhere. Tap it to open it again, or tap × to close.",
            "Leave the app while playing and it continues in Android's picture-in-picture window.",
        ),
        tip = "Settings → Video Player → Back button lets you choose picture-in-picture, floating player or close.",
    ) { FloatingDiagram() },
    GuideChapter(
        "watch", Icons.Default.PlayArrow, "Watch page: the moon dial",
        "Under every Yumix, YouTube and Live TV player sits Yumeiro's moon dial and source card.",
        listOf(
            "Dim veils everything except the player; tap anywhere to brighten again.",
            "Autoplay starts the video as soon as it opens; Auto-advance (Auto-zap for live TV) moves to the next one when it ends or fails.",
            "Skip intros jumps over openings when the extension marks them.",
            "The source card shows what is playing. Stuck on black? Tap another source or open-as mode. Yumeiro remembers what worked for each site.",
            "Long series show an episode board: pick a range, type a number in Jump to #, tap a tile.",
        ),
    ) { Flow("Moon dial" to Icons.Default.Tune, "Source card" to Icons.Default.LiveTv, "Episode board" to Icons.Default.PlayArrow) },
    GuideChapter(
        "hues", Icons.Default.Extension, "Yumix (extensions)",
        "Yumix extensions are CloudStream-compatible extensions that add online catalogues for anime, movies and shows.",
        listOf(
            "Open the Yumix store from Home → Explore, add a repository link, then install the extensions you want.",
            "Browse an extension or open For you to see picks from all of them; tap a title, then an episode.",
            "Yumeiro picks the best source and automatically switches server if one fails.",
        ),
        tip = "Only install extensions for content you have the right to watch.",
    ) { Flow("Add repo" to Icons.Default.Link, "Install extension" to Icons.Default.Extension, "Pick title" to Icons.Default.Search, "Watch" to Icons.Default.PlayArrow) },
    GuideChapter(
        "stream", Icons.Default.LiveTv, "Live TV: links & IPTV",
        "Paste any video link or IPTV playlist. Yumeiro inspects it and plays it in a watch page with the channel list below.",
        listOf(
            "Open Videos → Live TV, paste a link (or open an .m3u file / pick one in the playlist market) and tap Play.",
            "Yumeiro checks the link. If the server refuses it, it retries automatically like a browser, like the site's own player and like VLC.",
            "While watching, switch channels from the list under the player or use Previous / Next channel.",
        ),
        tip = "Still \"Access denied\"? Refresh the playlist (tokens expire) or add the site's Referer / Cookie under Headers & DRM.",
    ) { Flow("Paste link" to Icons.Default.Link, "Check & fix headers" to Icons.Default.AutoAwesome, "Watch page" to Icons.Default.LiveTv) },
    GuideChapter(
        "youtube", Icons.Default.SmartDisplay, "YouTube & YouTube Music",
        "No account needed. Search, trending, live and music with background play.",
        listOf(
            "Open Videos → YouTube, or Music → YouTube Music.",
            "Tap a video to open its watch page with related videos below the player.",
            "Songs from YouTube Music play in the music player, so the sleep timer and lyrics work too.",
        ),
    ) { Flow("Search" to Icons.Default.Search, "Watch page" to Icons.Default.SmartDisplay, "Up next" to Icons.Default.PlayArrow) },
    GuideChapter(
        "errors", Icons.Default.Shield, "When something goes wrong",
        "Every error shows what happened and a short \"Try this\" list with the fixes that usually work.",
        listOf(
            "Read the Try this tips on the error card and tap Retry.",
            "For online videos, tap another source on the source card under the player.",
            "Check your connection and turn off VPN or data saver if streams keep failing.",
        ),
        tip = "Yumeiro never bypasses region locks or logins — some content can only be played in its own app.",
    ) { Flow("Error" to Icons.Default.Shield, "Try this tips" to Icons.Outlined.Lightbulb, "Retry" to Icons.Default.PlayArrow) },
    GuideChapter(
        "updates", Icons.Default.SystemUpdate, "Updates",
        "Yumeiro updates itself from GitHub Releases. Every update is checked against the official signature.",
        listOf(
            "When an update is ready you'll see a card — tap Update and confirm the install.",
            "Settings → App Updates lets you check manually and choose the update channel.",
        ),
    ) { Flow("New release" to Icons.Default.SystemUpdate, "Verify signature" to Icons.Default.Shield, "Install" to Icons.Default.PlayArrow) },
)
