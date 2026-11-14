/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data.model

enum class MediaType {
    AUDIO,
    VIDEO
}

enum class PlayerRepeatMode {
    OFF,
    ALL,
    ONE
}

enum class TrackSort {
    TITLE_ASC,
    TITLE_DESC,
    RECENTLY_ADDED,
    DURATION,
    ARTIST,
    ALBUM
}

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String? = null,
    val albumId: Long = 0L,
    val sizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val folderName: String = "Music",
    val folderPath: String = "",
    val dateAdded: Long = 0L,
    val playCount: Int = 0
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val trackCount: Int,
    val albumArtUri: String? = null,
    val tracks: List<AudioTrack> = emptyList()
)

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val tracks: List<AudioTrack> = emptyList()
)

data class MusicFolder(
    val name: String,
    val path: String,
    val trackCount: Int,
    val tracks: List<AudioTrack> = emptyList()
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoItem(
    val id: String,
    val title: String,
    val durationMs: Long,
    val uri: String,
    val thumbnailUri: String? = null,
    val sizeBytes: Long = 0L,
    val resolution: String = "1080p",
    val width: Int = 0,
    val height: Int = 0,
    val folderName: String = "Movies",
    val folderPath: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val lastWatchedTimestamp: Long = 0L
)

enum class VideoSort {
    NAME_ASC,
    NAME_DESC,
    DATE_DESC,
    DATE_ASC,
    DURATION_DESC,
    DURATION_ASC,
    SIZE_DESC
}

data class VideoFolder(
    val id: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSizeBytes: Long = 0L,
    val isHidden: Boolean = false,
    val videos: List<VideoItem> = emptyList()
)

data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val trackCount: Int = 0,
    val videoCount: Int = 0
)

data class StorageStats(
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val totalAudioDurationMs: Long = 0L,
    val totalStorageUsedBytes: Long = 0L,
    val isScanning: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false
)

data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: PlayerRepeatMode = PlayerRepeatMode.OFF,
    val queue: List<AudioTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null,
    /** What is being decoded now (codec, bitrate, sample rate, channels, decoder); null until known. */
    val audioCodec: dev.asfakul.yumeiro.player.AudioCodecInfo? = null,
)
