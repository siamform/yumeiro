/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.TrackSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testPlaylistTrackOperations() {
        var playlist = Playlist(id = "test_1", name = "My Playlist", trackIds = listOf("t1", "t2"))

        // Add track
        val addedList = (playlist.trackIds + "t3").distinct()
        playlist = playlist.copy(trackIds = addedList)
        assertEquals(listOf("t1", "t2", "t3"), playlist.trackIds)

        // Reorder tracks
        val mutable = playlist.trackIds.toMutableList()
        val item = mutable.removeAt(2) // "t3"
        mutable.add(0, item)
        playlist = playlist.copy(trackIds = mutable)
        assertEquals(listOf("t3", "t1", "t2"), playlist.trackIds)

        // Remove track
        playlist = playlist.copy(trackIds = playlist.trackIds.filter { it != "t1" })
        assertEquals(listOf("t3", "t2"), playlist.trackIds)

        // Rename
        playlist = playlist.copy(name = "Renamed List")
        assertEquals("Renamed List", playlist.name)
    }

    @Test
    fun testTrackSorting() {
        val tracks = listOf(
            AudioTrack(id = "1", title = "Zebra", artist = "Ben", album = "A", durationMs = 300000L, uri = "", dateAdded = 100L),
            AudioTrack(id = "2", title = "Alpha", artist = "Carl", album = "B", durationMs = 120000L, uri = "", dateAdded = 200L),
            AudioTrack(id = "3", title = "Beta", artist = "Adam", album = "C", durationMs = 240000L, uri = "", dateAdded = 50L)
        )

        // Title ASC
        val titleAsc = tracks.sortedBy { it.title.lowercase() }
        assertEquals("Alpha", titleAsc[0].title)
        assertEquals("Beta", titleAsc[1].title)
        assertEquals("Zebra", titleAsc[2].title)

        // Duration DESC
        val durationDesc = tracks.sortedByDescending { it.durationMs }
        assertEquals("Zebra", durationDesc[0].title)
        assertEquals("Beta", durationDesc[1].title)
        assertEquals("Alpha", durationDesc[2].title)

        // Recently Added DESC
        val recentDesc = tracks.sortedByDescending { it.dateAdded }
        assertEquals("Alpha", recentDesc[0].title)
        assertEquals("Zebra", recentDesc[1].title)
        assertEquals("Beta", recentDesc[2].title)
    }

    @Test
    fun testEqualizerBandFormatting() {
        val band60Hz = dev.asfakul.yumeiro.player.equalizer.EqualizerBand(
            bandIndex = 0,
            centerFreqHz = 60,
            levelMb = 300
        )
        assertEquals("60Hz", band60Hz.displayFrequency)
        assertEquals("+3dB", band60Hz.displayLevel)

        val band1kHz = dev.asfakul.yumeiro.player.equalizer.EqualizerBand(
            bandIndex = 1,
            centerFreqHz = 1000,
            levelMb = -500
        )
        assertEquals("1kHz", band1kHz.displayFrequency)
        assertEquals("-5dB", band1kHz.displayLevel)

        val band14kHz = dev.asfakul.yumeiro.player.equalizer.EqualizerBand(
            bandIndex = 2,
            centerFreqHz = 14000,
            levelMb = 0
        )
        assertEquals("14kHz", band14kHz.displayFrequency)
        assertEquals("0dB", band14kHz.displayLevel)

        val band2Point5kHz = dev.asfakul.yumeiro.player.equalizer.EqualizerBand(
            bandIndex = 3,
            centerFreqHz = 2500,
            levelMb = 450
        )
        assertEquals("2.5kHz", band2Point5kHz.displayFrequency)
        assertEquals("+4dB", band2Point5kHz.displayLevel)
    }

    @Test
    fun testEqualizerStateDefaults() {
        val defaultState = dev.asfakul.yumeiro.player.equalizer.EqualizerState()
        assertEquals(false, defaultState.isEnabled)
        assertEquals("Normal", defaultState.currentPreset)
        assertTrue(defaultState.presets.contains("Rock"))
        assertTrue(defaultState.presets.contains("Bass Boost"))
        assertTrue(defaultState.presets.contains("Custom"))
        assertEquals(0, defaultState.bassBoostStrength)
        assertEquals(0, defaultState.virtualizerStrength)
    }

    @Test
    fun testVideoSorting() {
        val sampleVideos = listOf(
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "1",
                title = "Zootopia.mp4",
                durationMs = 6000000L,
                uri = "content://1",
                sizeBytes = 2000000000L,
                dateAdded = 1000L
            ),
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "2",
                title = "Avatar.mkv",
                durationMs = 9000000L,
                uri = "content://2",
                sizeBytes = 4500000000L,
                dateAdded = 3000L
            ),
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "3",
                title = "Inception.mp4",
                durationMs = 8000000L,
                uri = "content://3",
                sizeBytes = 1500000000L,
                dateAdded = 2000L
            )
        )

        // Name ASC
        val nameAsc = sampleVideos.sortedBy { it.title.lowercase() }
        assertEquals("Avatar.mkv", nameAsc[0].title)
        assertEquals("Inception.mp4", nameAsc[1].title)
        assertEquals("Zootopia.mp4", nameAsc[2].title)

        // Name DESC
        val nameDesc = sampleVideos.sortedByDescending { it.title.lowercase() }
        assertEquals("Zootopia.mp4", nameDesc[0].title)
        assertEquals("Inception.mp4", nameDesc[1].title)
        assertEquals("Avatar.mkv", nameDesc[2].title)

        // Date DESC
        val dateDesc = sampleVideos.sortedByDescending { it.dateAdded }
        assertEquals("Avatar.mkv", dateDesc[0].title)
        assertEquals("Inception.mp4", dateDesc[1].title)
        assertEquals("Zootopia.mp4", dateDesc[2].title)

        // Duration DESC
        val durationDesc = sampleVideos.sortedByDescending { it.durationMs }
        assertEquals("Avatar.mkv", durationDesc[0].title)
        assertEquals("Inception.mp4", durationDesc[1].title)
        assertEquals("Zootopia.mp4", durationDesc[2].title)

        // Size DESC
        val sizeDesc = sampleVideos.sortedByDescending { it.sizeBytes }
        assertEquals("Avatar.mkv", sizeDesc[0].title)
        assertEquals("Zootopia.mp4", sizeDesc[1].title)
        assertEquals("Inception.mp4", sizeDesc[2].title)
    }

    @Test
    fun testContinueWatchingCalculation() {
        val videos = listOf(
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "1",
                title = "Video 1",
                durationMs = 100000L,
                playbackPositionMs = 50000L, // 50% - should continue watching
                lastWatchedTimestamp = 1000L,
                uri = ""
            ),
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "2",
                title = "Video 2",
                durationMs = 100000L,
                playbackPositionMs = 0L, // 0% - not started
                lastWatchedTimestamp = 0L,
                uri = ""
            ),
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "3",
                title = "Video 3",
                durationMs = 100000L,
                playbackPositionMs = 96000L, // 96% - completed (>95%)
                lastWatchedTimestamp = 2000L,
                uri = ""
            ),
            dev.asfakul.yumeiro.data.model.VideoItem(
                id = "4",
                title = "Video 4",
                durationMs = 100000L,
                playbackPositionMs = 80000L, // 80% - should continue watching
                lastWatchedTimestamp = 3000L,
                uri = ""
            )
        )

        val inProgress = videos.filter {
            it.playbackPositionMs > 0 && it.playbackPositionMs < (it.durationMs * 0.95).toLong()
        }.sortedByDescending { it.lastWatchedTimestamp }

        assertEquals(2, inProgress.size)
        assertEquals("Video 4", inProgress[0].title) // more recently watched
        assertEquals("Video 1", inProgress[1].title)
    }

    @Test
    fun testVideoFolderGroupingAndHiding() {
        val sampleVideos = listOf(
            dev.asfakul.yumeiro.data.model.VideoItem(id = "1", title = "A", durationMs = 1000L, sizeBytes = 100L, folderName = "Movies", folderPath = "/storage/Movies", uri = ""),
            dev.asfakul.yumeiro.data.model.VideoItem(id = "2", title = "B", durationMs = 2000L, sizeBytes = 200L, folderName = "Movies", folderPath = "/storage/Movies", uri = ""),
            dev.asfakul.yumeiro.data.model.VideoItem(id = "3", title = "C", durationMs = 3000L, sizeBytes = 300L, folderName = "WhatsApp", folderPath = "/storage/WhatsApp", uri = "")
        )

        val hiddenFolders = setOf("/storage/WhatsApp")

        val visibleVideos = sampleVideos.filter { !hiddenFolders.contains(it.folderPath) }
        assertEquals(2, visibleVideos.size)
        assertTrue(visibleVideos.none { it.folderName == "WhatsApp" })

        val grouped = sampleVideos.groupBy { it.folderPath }.map { (path, vids) ->
            dev.asfakul.yumeiro.data.model.VideoFolder(
                id = path,
                name = vids.first().folderName,
                path = path,
                videoCount = vids.size,
                totalSizeBytes = vids.sumOf { it.sizeBytes },
                isHidden = hiddenFolders.contains(path),
                videos = vids
            )
        }

        val moviesFolder = grouped.first { it.name == "Movies" }
        assertEquals(2, moviesFolder.videoCount)
        assertEquals(300L, moviesFolder.totalSizeBytes)
        assertEquals(false, moviesFolder.isHidden)

        val waFolder = grouped.first { it.name == "WhatsApp" }
        assertEquals(1, waFolder.videoCount)
        assertEquals(true, waFolder.isHidden)
    }
}
