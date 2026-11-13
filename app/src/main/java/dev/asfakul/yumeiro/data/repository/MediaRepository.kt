/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.asfakul.yumeiro.data.model.Album
import dev.asfakul.yumeiro.data.model.Artist
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.MediaFolder
import dev.asfakul.yumeiro.data.model.MusicFolder
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.VideoFolder
import dev.asfakul.yumeiro.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

interface MediaRepository {
    val storageStats: Flow<StorageStats>
    val audioTracks: Flow<List<AudioTrack>>
    val videoItems: Flow<List<VideoItem>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val musicFolders: Flow<List<MusicFolder>>
    val folders: Flow<List<MediaFolder>>
    val playlists: Flow<List<Playlist>>
    val recentlyPlayedTracks: Flow<List<AudioTrack>>
    val mostPlayedTracks: Flow<List<AudioTrack>>

    // Video specific flows
    val favoriteVideoIds: Flow<Set<String>>
    val hiddenVideoFolderPaths: Flow<Set<String>>
    val videoFolders: Flow<List<VideoFolder>>
    val recentlyWatchedVideos: Flow<List<VideoItem>>
    val continueWatchingVideos: Flow<List<VideoItem>>
    val favoriteVideos: Flow<List<VideoItem>>

    suspend fun scanDeviceMedia()
    suspend fun toggleFavorite(trackId: String)
    suspend fun addFavorites(trackIds: List<String>)
    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList())
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int)
    suspend fun recordTrackPlayed(trackId: String)
    fun updatePermissionStatus(granted: Boolean, permanentlyDenied: Boolean = false)

    // Video specific actions
    suspend fun toggleVideoFavorite(videoId: String)
    suspend fun hideVideoFolder(folderPath: String)
    suspend fun unhideVideoFolder(folderPath: String)
    suspend fun unhideAllVideoFolders()
    suspend fun updateVideoProgress(videoId: String, positionMs: Long, durationMs: Long)
    suspend fun clearVideoProgress(videoId: String)
    /** Forgets every resume position and "last watched" time (Continue / Recently watched). */
    suspend fun clearWatchHistory() {}
    /** Forgets recently played tracks and play counts (Continue listening / Most played). */
    suspend fun clearListeningHistory() {}
}

class OfflineMediaRepository(
    private val context: Context
) : MediaRepository {

    private val _storageStats = MutableStateFlow(StorageStats())
    override val storageStats = _storageStats.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    override val audioTracks = _audioTracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    override val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    override val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<List<MusicFolder>>(emptyList())
    override val musicFolders = _musicFolders.asStateFlow()

    private val videoPrefs = context.getSharedPreferences("mt_player_video_prefs", Context.MODE_PRIVATE)

    private val _favoriteVideoIds = MutableStateFlow<Set<String>>(
        videoPrefs.getStringSet("favorite_video_ids", emptySet())?.toSet() ?: emptySet()
    )
    override val favoriteVideoIds = _favoriteVideoIds.asStateFlow()

    private val _hiddenFolderPaths = MutableStateFlow<Set<String>>(
        videoPrefs.getStringSet("hidden_video_folders", emptySet())?.toSet() ?: emptySet()
    )
    override val hiddenVideoFolderPaths = _hiddenFolderPaths.asStateFlow()

    private val _videoProgress = MutableStateFlow<Map<String, Long>>(loadVideoProgress())
    private val _videoLastWatched = MutableStateFlow<Map<String, Long>>(loadVideoLastWatched())

    private val _allScannedVideos = MutableStateFlow<List<VideoItem>>(emptyList())

    override val videoItems: Flow<List<VideoItem>> = combine(
        _allScannedVideos,
        _hiddenFolderPaths,
        _favoriteVideoIds,
        _videoProgress,
        _videoLastWatched
    ) { allVideos, hiddenFolders, favIds, progressMap, watchedMap ->
        allVideos
            .filter { video ->
                !hiddenFolders.contains(video.folderPath) && !hiddenFolders.contains(video.folderName)
            }
            .map { video ->
                video.copy(
                    isFavorite = favIds.contains(video.id),
                    playbackPositionMs = progressMap[video.id] ?: 0L,
                    lastWatchedTimestamp = watchedMap[video.id] ?: 0L
                )
            }
    }

    override val videoFolders: Flow<List<VideoFolder>> = combine(
        _allScannedVideos,
        _hiddenFolderPaths,
        _favoriteVideoIds,
        _videoProgress,
        _videoLastWatched
    ) { allVideos, hiddenFolders, favIds, progressMap, watchedMap ->
        val mappedVideos = allVideos.map { video ->
            video.copy(
                isFavorite = favIds.contains(video.id),
                playbackPositionMs = progressMap[video.id] ?: 0L,
                lastWatchedTimestamp = watchedMap[video.id] ?: 0L
            )
        }
        mappedVideos.groupBy { if (it.folderPath.isNotBlank()) it.folderPath else it.folderName }
            .map { (pathKey, videosInFolder) ->
                val folderName = videosInFolder.firstOrNull()?.folderName ?: "Videos"
                val isHidden = hiddenFolders.contains(pathKey) || hiddenFolders.contains(folderName)
                VideoFolder(
                    id = pathKey,
                    name = folderName,
                    path = pathKey,
                    videoCount = videosInFolder.size,
                    totalSizeBytes = videosInFolder.sumOf { it.sizeBytes },
                    isHidden = isHidden,
                    videos = videosInFolder
                )
            }.sortedBy { it.name.lowercase() }
    }

    override val continueWatchingVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.playbackPositionMs > 0 && it.durationMs > 0 && it.playbackPositionMs < (it.durationMs * 0.95).toLong() }
            .sortedByDescending { it.lastWatchedTimestamp }
    }

    override val recentlyWatchedVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.lastWatchedTimestamp > 0 }
            .sortedByDescending { it.lastWatchedTimestamp }
    }

    override val favoriteVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.isFavorite }
    }

    private val _folders = MutableStateFlow<List<MediaFolder>>(emptyList())
    override val folders = _folders.asStateFlow()

    // Music library state (favourites, playlists, history) is persisted so it survives restarts.
    private val musicPrefs = context.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE)

    private val favoriteIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (musicPrefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()).toMutableSet()
    )

    private val _playlists = MutableStateFlow(loadPlaylists())
    override val playlists = _playlists.asStateFlow()

    private val _recentlyPlayedIds = MutableStateFlow(
        musicPrefs.getString(KEY_RECENT, null).orEmpty().split('\n').filter { it.isNotBlank() }
    )
    private val _playCounts = MutableStateFlow(loadPlayCounts())

    private fun defaultPlaylists() = listOf(
        Playlist(id = "favorites_default", name = "Favorite Tracks", trackIds = favoriteIds.toList()),
        Playlist(id = "workout_mix", name = "Workout Energy", trackIds = emptyList()),
        Playlist(id = "chill_vibes", name = "Late Night Chill", trackIds = emptyList())
    )

    private fun loadPlaylists(): List<Playlist> = runCatching {
        val raw = musicPrefs.getString(KEY_PLAYLISTS, null) ?: return defaultPlaylists()
        val array = org.json.JSONArray(raw)
        val loaded = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val ids = obj.optJSONArray("trackIds")
            Playlist(
                id = obj.getString("id"),
                name = obj.optString("name", "Playlist"),
                trackIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) },
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
        if (loaded.none { it.id == "favorites_default" }) {
            listOf(Playlist(id = "favorites_default", name = "Favorite Tracks", trackIds = favoriteIds.toList())) + loaded
        } else loaded
    }.getOrElse { defaultPlaylists() }

    private fun loadPlayCounts(): Map<String, Int> = runCatching {
        val obj = org.json.JSONObject(musicPrefs.getString(KEY_PLAY_COUNTS, null) ?: "{}")
        obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
    }.getOrElse { emptyMap() }

    private fun persistPlaylists() {
        runCatching {
            val array = org.json.JSONArray()
            _playlists.value.forEach { pl ->
                array.put(
                    org.json.JSONObject()
                        .put("id", pl.id)
                        .put("name", pl.name)
                        .put("createdAt", pl.createdAt)
                        .put("trackIds", org.json.JSONArray(pl.trackIds))
                )
            }
            musicPrefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
        }
    }

    private fun persistFavorites() {
        musicPrefs.edit().putStringSet(KEY_FAVORITES, HashSet(favoriteIds)).apply()
    }

    private fun persistHistory() {
        runCatching {
            musicPrefs.edit()
                .putString(KEY_RECENT, _recentlyPlayedIds.value.joinToString("\n"))
                .putString(KEY_PLAY_COUNTS, org.json.JSONObject(_playCounts.value as Map<*, *>).toString())
                .apply()
        }
    }

    override val recentlyPlayedTracks: Flow<List<AudioTrack>> = combine(
        _audioTracks,
        _recentlyPlayedIds
    ) { tracks, recentIds ->
        val trackMap = tracks.associateBy { it.id }
        recentIds.mapNotNull { trackMap[it] }
    }

    override val mostPlayedTracks: Flow<List<AudioTrack>> = combine(
        _audioTracks,
        _playCounts
    ) { tracks, counts ->
        tracks.filter { (counts[it.id] ?: 0) > 0 }
            .sortedByDescending { counts[it.id] ?: 0 }
    }

    override fun updatePermissionStatus(granted: Boolean, permanentlyDenied: Boolean) {
        _storageStats.value = _storageStats.value.copy(
            hasStoragePermission = granted,
            permissionPermanentlyDenied = permanentlyDenied
        )
    }

    override suspend fun scanDeviceMedia() = withContext(Dispatchers.IO) {
        _storageStats.value = _storageStats.value.copy(isScanning = true)

        val scannedAudio = mutableListOf<AudioTrack>()
        val scannedVideos = mutableListOf<VideoItem>()

        try {
            val resolver: ContentResolver = context.contentResolver

            // Query Audio from MediaStore
            val audioUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val audioProjection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                    add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                }
                add(MediaStore.Audio.Media.DATA)
            }.toTypedArray()

            val audioSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 10000"

            val audioCursor: Cursor? = resolver.query(
                audioUri,
                audioProjection,
                audioSelection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            audioCursor?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) * 1000L else 0L

                    var folderName = "Music"
                    var folderPath = ""

                    if (bucketCol >= 0 && !cursor.isNull(bucketCol)) {
                        folderName = cursor.getString(bucketCol) ?: "Music"
                    } else if (relPathCol >= 0 && !cursor.isNull(relPathCol)) {
                        val rel = cursor.getString(relPathCol) ?: ""
                        val parts = rel.trim('/').split('/')
                        folderName = parts.lastOrNull()?.ifBlank { "Music" } ?: "Music"
                        folderPath = rel
                    } else if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                        val path = cursor.getString(dataCol) ?: ""
                        val parentFile = File(path).parentFile
                        if (parentFile != null) {
                            folderName = parentFile.name
                            folderPath = parentFile.absolutePath
                        }
                    }

                    if (folderPath.isBlank() && dataCol >= 0 && !cursor.isNull(dataCol)) {
                        folderPath = File(cursor.getString(dataCol) ?: "").parentFile?.absolutePath ?: ""
                    }

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                    } else null

                    val cleanArtist = if (artist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artist
                    val cleanAlbum = if (album.contains("<unknown>", ignoreCase = true)) "Unknown Album" else album
                    val isFav = favoriteIds.contains(id.toString())
                    val playCount = _playCounts.value[id.toString()] ?: 0

                    scannedAudio.add(
                        AudioTrack(
                            id = id.toString(),
                            title = title,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            durationMs = duration,
                            uri = contentUri.toString(),
                            albumArtUri = albumArtUri,
                            albumId = albumId,
                            sizeBytes = size,
                            isFavorite = isFav,
                            folderName = displayFolderName(folderName, folderPath),
                            folderPath = folderPath,
                            dateAdded = dateAdded,
                            playCount = playCount
                        )
                    )
                }
            }

            // Grouping: Albums, Artists, Folders (ids must be unique for list keys)
            val uniqueAudio = scannedAudio.distinctBy { it.id }
            scannedAudio.clear(); scannedAudio.addAll(uniqueAudio)
            val groupedAlbums = scannedAudio.groupBy { it.albumId to it.album.trim().lowercase() }
                .map { (key, tracks) ->
                    val (albumId, _) = key
                    Album(
                        id = albumId,
                        name = tracks.first().album,
                        artist = tracks.firstOrNull()?.artist ?: "Various Artists",
                        trackCount = tracks.size,
                        albumArtUri = tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                        tracks = tracks
                    )
                }.sortedBy { it.name.lowercase() }

            val groupedArtists = scannedAudio.groupBy { it.artist }
                .map { (artistName, tracks) ->
                    Artist(
                        name = artistName,
                        trackCount = tracks.size,
                        albumCount = tracks.map { it.album }.distinct().size,
                        tracks = tracks
                    )
                }.sortedBy { it.name.lowercase() }

            val groupedFolders = scannedAudio.groupBy { it.folderPath.ifBlank { it.folderName } }
                .map { (_, tracks) ->
                    val folderName = tracks.first().folderName
                    MusicFolder(
                        name = folderName,
                        path = tracks.firstOrNull()?.folderPath ?: "",
                        trackCount = tracks.size,
                        tracks = tracks
                    )
                }.sortedBy { it.name.lowercase() }

            _albums.value = groupedAlbums
            _artists.value = groupedArtists
            _musicFolders.value = groupedFolders

            // Query Videos from MediaStore
            val videoUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val videoProjectionList = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                videoProjectionList.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                videoProjectionList.add(MediaStore.Video.Media.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                videoProjectionList.add(MediaStore.Video.Media.WIDTH)
                videoProjectionList.add(MediaStore.Video.Media.HEIGHT)
            }

            val videoCursor: Cursor? = resolver.query(
                videoUri,
                videoProjectionList.toTypedArray(),
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            videoCursor?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0

                    val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    val folderName = displayFolderName(when {
                        !bucketName.isNullOrBlank() -> bucketName
                        dataPath.isNotBlank() -> File(dataPath).parentFile?.name ?: "Videos"
                        !relativePath.isNullOrBlank() -> relativePath.trim('/').split('/').lastOrNull() ?: "Videos"
                        else -> "Videos"
                    }, dataPath)

                    val folderPath = when {
                        dataPath.isNotBlank() -> File(dataPath).parentFile?.absolutePath ?: ""
                        !relativePath.isNullOrBlank() -> relativePath
                        else -> folderName
                    }

                    val title = when {
                        !rawTitle.isNullOrBlank() -> rawTitle
                        dataPath.isNotBlank() -> File(dataPath).nameWithoutExtension
                        else -> "Video_${id}"
                    }

                    val resolution = when {
                        (width >= 3840 && height >= 2160) || (width >= 2160 && height >= 3840) -> "4K"
                        (width >= 1920 && height >= 1080) || (width >= 1080 && height >= 1920) -> "1080p"
                        (width >= 1280 && height >= 720) || (width >= 720 && height >= 1280) -> "720p"
                        (width >= 854 && height >= 480) || (width >= 480 && height >= 854) -> "480p"
                        width > 0 && height > 0 -> "${width}x${height}"
                        else -> "HD"
                    }

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    scannedVideos.add(
                        VideoItem(
                            id = id.toString(),
                            title = title,
                            durationMs = duration,
                            uri = contentUri.toString(),
                            thumbnailUri = contentUri.toString(),
                            sizeBytes = size,
                            resolution = resolution,
                            width = width,
                            height = height,
                            folderName = folderName,
                            folderPath = folderPath,
                            dateAdded = dateAdded
                        )
                    )
                }
            }

        } catch (e: Exception) {
            // Handled gracefully if no storage permissions or empty storage
        }

        val totalDuration = scannedAudio.sumOf { it.durationMs }
        val totalSize = scannedAudio.sumOf { it.sizeBytes } + scannedVideos.sumOf { it.sizeBytes }

        _audioTracks.value = scannedAudio.distinctBy { it.id }
        _allScannedVideos.value = scannedVideos.distinctBy { it.id }
        val hiddenSet = _hiddenFolderPaths.value
        val visibleVideoCount = scannedVideos.count { !hiddenSet.contains(it.folderPath) && !hiddenSet.contains(it.folderName) }
        _storageStats.value = _storageStats.value.copy(
            audioCount = scannedAudio.size,
            videoCount = visibleVideoCount,
            totalAudioDurationMs = totalDuration,
            totalStorageUsedBytes = totalSize,
            isScanning = false
        )
    }

    override suspend fun toggleFavorite(trackId: String) {
        if (favoriteIds.contains(trackId)) {
            favoriteIds.remove(trackId)
        } else {
            favoriteIds.add(trackId)
        }
        updateTrackFavoriteState()
    }

    override suspend fun addFavorites(trackIds: List<String>) {
        favoriteIds.addAll(trackIds)
        updateTrackFavoriteState()
    }

    private fun updateTrackFavoriteState() {
        _audioTracks.value = _audioTracks.value.map { track ->
            track.copy(isFavorite = favoriteIds.contains(track.id))
        }
        // Update default favorites playlist
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == "favorites_default") {
                pl.copy(trackIds = favoriteIds.toList())
            } else pl
        }
        persistFavorites()
        persistPlaylists()
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>) {
        val newPlaylist = Playlist(
            id = "playlist_${System.currentTimeMillis()}",
            name = name,
            trackIds = trackIds
        )
        _playlists.value = _playlists.value + newPlaylist
        persistPlaylists()
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = newName) else pl
        }
        persistPlaylists()
    }

    override suspend fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        persistPlaylists()
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val updatedIds = (pl.trackIds + trackIds).distinct()
                pl.copy(trackIds = updatedIds)
            } else pl
        }
        persistPlaylists()
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filter { it != trackId })
            } else pl
        }
        persistPlaylists()
    }

    override suspend fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val list = pl.trackIds.toMutableList()
                if (fromIndex in list.indices && toIndex in list.indices) {
                    val item = list.removeAt(fromIndex)
                    list.add(toIndex, item)
                }
                pl.copy(trackIds = list)
            } else pl
        }
        persistPlaylists()
    }

    override suspend fun recordTrackPlayed(trackId: String) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        // Update recently played (push to front, max 50)
        val currentList = _recentlyPlayedIds.value.filter { it != trackId }.toMutableList()
        currentList.add(0, trackId)
        if (currentList.size > 50) {
            _recentlyPlayedIds.value = currentList.take(50)
        } else {
            _recentlyPlayedIds.value = currentList
        }

        // Update play counts
        val count = (_playCounts.value[trackId] ?: 0) + 1
        _playCounts.value = _playCounts.value + (trackId to count)

        _audioTracks.value = _audioTracks.value.map { track ->
            if (track.id == trackId) track.copy(playCount = count) else track
        }
        persistHistory()
    }

    override suspend fun toggleVideoFavorite(videoId: String) {
        val current = _favoriteVideoIds.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        videoPrefs.edit().putStringSet("favorite_video_ids", current).apply()
        _favoriteVideoIds.value = current
    }

    override suspend fun hideVideoFolder(folderPath: String) {
        if (folderPath.isBlank()) return
        val current = _hiddenFolderPaths.value.toMutableSet()
        current.add(folderPath)
        videoPrefs.edit().putStringSet("hidden_video_folders", current).apply()
        _hiddenFolderPaths.value = current

        // Update stats
        val totalSize = _audioTracks.value.sumOf { it.sizeBytes } + _allScannedVideos.value.sumOf { it.sizeBytes }
        _storageStats.value = _storageStats.value.copy(
            videoCount = _allScannedVideos.value.count { !current.contains(it.folderPath) && !current.contains(it.folderName) },
            totalStorageUsedBytes = totalSize
        )
    }

    override suspend fun unhideVideoFolder(folderPath: String) {
        val current = _hiddenFolderPaths.value.toMutableSet()
        current.remove(folderPath)
        videoPrefs.edit().putStringSet("hidden_video_folders", current).apply()
        _hiddenFolderPaths.value = current

        // Update stats
        val totalSize = _audioTracks.value.sumOf { it.sizeBytes } + _allScannedVideos.value.sumOf { it.sizeBytes }
        _storageStats.value = _storageStats.value.copy(
            videoCount = _allScannedVideos.value.count { !current.contains(it.folderPath) && !current.contains(it.folderName) },
            totalStorageUsedBytes = totalSize
        )
    }

    override suspend fun unhideAllVideoFolders() {
        videoPrefs.edit().remove("hidden_video_folders").apply()
        _hiddenFolderPaths.value = emptySet()

        val totalSize = _audioTracks.value.sumOf { it.sizeBytes } + _allScannedVideos.value.sumOf { it.sizeBytes }
        _storageStats.value = _storageStats.value.copy(
            videoCount = _allScannedVideos.value.size,
            totalStorageUsedBytes = totalSize
        )
    }

    override suspend fun updateVideoProgress(videoId: String, positionMs: Long, durationMs: Long) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        val now = System.currentTimeMillis()
        val isCompleted = durationMs > 0 && positionMs >= (durationMs * 0.95).toLong()

        val editor = videoPrefs.edit()
        editor.putLong("watched_$videoId", now)
        if (isCompleted || positionMs <= 0L) {
            editor.remove("pos_$videoId")
        } else {
            editor.putLong("pos_$videoId", positionMs)
        }
        editor.apply()

        val updatedProgress = _videoProgress.value.toMutableMap()
        if (isCompleted || positionMs <= 0L) {
            updatedProgress.remove(videoId)
        } else {
            updatedProgress[videoId] = positionMs
        }
        _videoProgress.value = updatedProgress

        val updatedWatched = _videoLastWatched.value.toMutableMap()
        updatedWatched[videoId] = now
        _videoLastWatched.value = updatedWatched
    }

    override suspend fun clearWatchHistory() {
        val editor = videoPrefs.edit()
        videoPrefs.all.keys.filter { it.startsWith("pos_") || it.startsWith("watched_") }.forEach { editor.remove(it) }
        editor.apply()
        _videoProgress.value = emptyMap()
        _videoLastWatched.value = emptyMap()
    }

    override suspend fun clearListeningHistory() {
        _recentlyPlayedIds.value = emptyList()
        _playCounts.value = emptyMap()
        _audioTracks.value = _audioTracks.value.map { if (it.playCount != 0) it.copy(playCount = 0) else it }
        persistHistory()
    }

    override suspend fun clearVideoProgress(videoId: String) {
        videoPrefs.edit().remove("pos_$videoId").apply()
        val updatedProgress = _videoProgress.value.toMutableMap()
        updatedProgress.remove(videoId)
        _videoProgress.value = updatedProgress
    }

    private fun loadVideoProgress(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        try {
            videoPrefs.all.forEach { (key, value) ->
                if (key.startsWith("pos_")) {
                    when (value) {
                        is Long -> map[key.removePrefix("pos_")] = value
                        is Int -> map[key.removePrefix("pos_")] = value.toLong()
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun loadVideoLastWatched(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        try {
            videoPrefs.all.forEach { (key, value) ->
                if (key.startsWith("watched_")) {
                    when (value) {
                        is Long -> map[key.removePrefix("watched_")] = value
                        is Int -> map[key.removePrefix("watched_")] = value.toLong()
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_track_ids"
        const val KEY_PLAYLISTS = "playlists_json"
        const val KEY_RECENT = "recently_played_ids"
        const val KEY_PLAY_COUNTS = "play_counts_json"
    }

}

/**
 * A folder name people can read: MediaStore reports "<unknown>" (or nothing) for some files and
 * the storage root's own name ("0", "1234-ABCD") for files saved at the top of a drive.
 */
internal fun displayFolderName(name: String?, path: String = ""): String {
    val n = name?.trim().orEmpty()
    val parent = java.io.File(path).parentFile?.parentFile?.name.orEmpty()
    return when {
        n.isEmpty() || n.equals("<unknown>", true) || n.equals("unknown", true) -> "Internal storage"
        n == "0" && (path.contains("/emulated/") || parent == "emulated") -> "Internal storage"
        Regex("^[0-9A-F]{4}-[0-9A-F]{4}$").matches(n) -> "SD card"
        else -> n
    }
}
