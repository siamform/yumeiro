/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.asfakul.yumeiro.data.model.Album
import dev.asfakul.yumeiro.data.model.Artist
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.MusicFolder
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.TrackSort
import dev.asfakul.yumeiro.data.model.VideoFolder
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.data.model.VideoSort
import dev.asfakul.yumeiro.data.repository.MediaRepository
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import dev.asfakul.yumeiro.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MediaRepository = OfflineMediaRepository(application.applicationContext)
    private val playerManager: MusicPlayerManager = MusicPlayerManager(
        context = application.applicationContext,
        onTrackChanged = { trackId ->
            viewModelScope.launch {
                repository.recordTrackPlayed(trackId)
            }
        }
    )

    private val equalizerManager: dev.asfakul.yumeiro.player.equalizer.EqualizerManager =
        dev.asfakul.yumeiro.player.equalizer.EqualizerManager.getInstance(application.applicationContext)

    /**
     * Playback state enriched with the live library so favourite toggles (and other metadata
     * changes) are reflected immediately in the mini player and Now Playing screen. The player
     * keeps its own copy of the track objects, which otherwise go stale.
     */
    val playbackState: StateFlow<PlaybackState> = combine(
        // Position ticks are split out (see [playbackPositionMs]) so the whole app does not
        // recompose every 300 ms while music plays — only the progress bars read them.
        playerManager.playbackState
            .map { it.copy(currentPositionMs = 0L, bufferedPositionMs = 0L) }
            .distinctUntilChanged(),
        repository.audioTracks
    ) { state, tracks ->
        if (tracks.isEmpty()) return@combine state
        val byId = tracks.associateBy { it.id }
        state.copy(
            currentTrack = state.currentTrack?.let { byId[it.id] ?: it },
            queue = state.queue.map { byId[it.id] ?: it }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, playerManager.playbackState.value)

    /** Live playback position, read only by progress UI (keeps recomposition scoped & cheap). */
    val playbackPositionMs: StateFlow<Long> = playerManager.playbackState
        .map { it.currentPositionMs }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, playerManager.playbackState.value.currentPositionMs)
    val equalizerState: StateFlow<dev.asfakul.yumeiro.player.equalizer.EqualizerState> = equalizerManager.equalizerState

    val storageStats: StateFlow<StorageStats> = repository.storageStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    val rawAudioTracks: StateFlow<List<AudioTrack>> = repository.audioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawAlbums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawArtists: StateFlow<List<Artist>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMusicFolders: StateFlow<List<MusicFolder>> = repository.musicFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawVideoItems: StateFlow<List<VideoItem>> = repository.videoItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolders: StateFlow<List<VideoFolder>> = repository.videoFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatchingVideos: StateFlow<List<VideoItem>> = repository.continueWatchingVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatchedVideos: StateFlow<List<VideoItem>> = repository.recentlyWatchedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVideos: StateFlow<List<VideoItem>> = repository.favoriteVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenVideoFolderPaths: StateFlow<Set<String>> = repository.hiddenVideoFolderPaths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedTracks: StateFlow<List<AudioTrack>> = repository.recentlyPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedTracks: StateFlow<List<AudioTrack>> = repository.mostPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _trackSort = MutableStateFlow(TrackSort.TITLE_ASC)
    val trackSort = _trackSort.asStateFlow()

    private val _videoSort = MutableStateFlow(VideoSort.DATE_DESC)
    val videoSort = _videoSort.asStateFlow()

    private val _activeMusicTab = MutableStateFlow(0) // 0: Tracks, 1: Albums, 2: Artists, 3: Folders, 4: Favorites
    val activeMusicTab = _activeMusicTab.asStateFlow()

    private val _activeVideoTab = MutableStateFlow(0) // 0: All Videos, 1: Folders, 2: Continue, 3: Recent, 4: Favorites
    val activeVideoTab = _activeVideoTab.asStateFlow()

    private val _selectedVideoFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedVideoFolder = _selectedVideoFolder.asStateFlow()

    private val _activePlayingVideo = MutableStateFlow<VideoItem?>(null)
    val activePlayingVideo = _activePlayingVideo.asStateFlow()

    private val _videoForDetails = MutableStateFlow<VideoItem?>(null)
    val videoForDetails = _videoForDetails.asStateFlow()

    private val _showManageHiddenFoldersDialog = MutableStateFlow(false)
    val showManageHiddenFoldersDialog = _showManageHiddenFoldersDialog.asStateFlow()

    private val _isVideoGridView = MutableStateFlow(true)
    val isVideoGridView = _isVideoGridView.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded = _isNowPlayingExpanded.asStateFlow()

    // Multi-select state
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTrackIds = _selectedTrackIds.asStateFlow()

    // Context song options sheet
    private val _actionTrack = MutableStateFlow<AudioTrack?>(null)
    val actionTrack = _actionTrack.asStateFlow()

    // Add to playlist modal state
    private val _pendingPlaylistTrackIds = MutableStateFlow<List<String>>(emptyList())
    val pendingPlaylistTrackIds = _pendingPlaylistTrackIds.asStateFlow()

    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    val showAddToPlaylistDialog = _showAddToPlaylistDialog.asStateFlow()

    // Filtered & Sorted Tracks
    val filteredAudioTracks: StateFlow<List<AudioTrack>> = combine(
        rawAudioTracks,
        _searchQuery,
        _trackSort
    ) { tracks, query, sort ->
        val filtered = if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }

        when (sort) {
            TrackSort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            TrackSort.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            TrackSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            TrackSort.DURATION -> filtered.sortedByDescending { it.durationMs }
            TrackSort.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            TrackSort.ALBUM -> filtered.sortedBy { it.album.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums: StateFlow<List<Album>> = combine(
        rawAlbums,
        _searchQuery
    ) { albums, query ->
        if (query.isBlank()) albums
        else albums.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArtists: StateFlow<List<Artist>> = combine(
        rawArtists,
        _searchQuery
    ) { artists, query ->
        if (query.isBlank()) artists
        else artists.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFolders: StateFlow<List<MusicFolder>> = combine(
        rawMusicFolders,
        _searchQuery
    ) { folders, query ->
        if (query.isBlank()) folders
        else folders.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.path.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideoItems: StateFlow<List<VideoItem>> = combine(
        rawVideoItems,
        _searchQuery,
        _videoSort
    ) { videos, query, sort ->
        val filtered = if (query.isBlank()) videos
        else videos.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.folderName.contains(query, ignoreCase = true)
        }

        when (sort) {
            VideoSort.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            VideoSort.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            VideoSort.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
            VideoSort.DATE_ASC -> filtered.sortedBy { it.dateAdded }
            VideoSort.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            VideoSort.DURATION_ASC -> filtered.sortedBy { it.durationMs }
            VideoSort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanMedia()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortChange(sort: TrackSort) {
        _trackSort.value = sort
    }

    fun onMusicTabChange(tabIndex: Int) {
        _activeMusicTab.value = tabIndex
    }

    fun onVideoTabChange(tabIndex: Int) {
        _activeVideoTab.value = tabIndex
        if (_selectedVideoFolder.value != null && tabIndex != 1) {
            _selectedVideoFolder.value = null
        }
    }

    fun onVideoSortChange(sort: VideoSort) {
        _videoSort.value = sort
    }

    fun toggleVideoLayout() {
        _isVideoGridView.value = !_isVideoGridView.value
    }

    fun toggleVideoFavorite(videoId: String) {
        viewModelScope.launch {
            repository.toggleVideoFavorite(videoId)
        }
    }

    fun hideVideoFolder(folderPath: String) {
        viewModelScope.launch {
            repository.hideVideoFolder(folderPath)
            if (_selectedVideoFolder.value?.path == folderPath) {
                _selectedVideoFolder.value = null
            }
        }
    }

    fun unhideVideoFolder(folderPath: String) {
        viewModelScope.launch {
            repository.unhideVideoFolder(folderPath)
        }
    }

    fun unhideAllVideoFolders() {
        viewModelScope.launch {
            repository.unhideAllVideoFolders()
        }
    }

    fun selectVideoFolder(folder: VideoFolder?) {
        _selectedVideoFolder.value = folder
    }

    /**
     * Video "queue": the list the video was opened from (folder, tab, home grid...). When opened
     * without a list (details dialog), the other videos of the same folder are used, in the
     * current sort order. Streams / shared links have no queue.
     */
    private val _videoQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val videoQueue = _videoQueue.asStateFlow()

    fun playVideo(video: VideoItem) = playVideo(video, emptyList())

    fun playVideo(video: VideoItem, queue: List<VideoItem>) {
        // Pause music playback if active
        if (playbackState.value.isPlaying) {
            playerManager.pause()
        }
        val q = queue.ifEmpty {
            if (video.folderPath.isBlank()) emptyList()
            else filteredVideoItems.value.filter { it.folderPath == video.folderPath }
        }.distinctBy { it.id }
        _videoQueue.value = if (q.any { it.id == video.id }) q else listOf(video)
        _activePlayingVideo.value = video
    }

    private fun queueNeighbor(offset: Int): VideoItem? {
        val current = _activePlayingVideo.value ?: return null
        val q = _videoQueue.value
        val i = q.indexOfFirst { it.id == current.id }
        if (i < 0) return null
        val target = q.getOrNull(i + offset) ?: return null
        // Use the freshest copy (latest resume position / favourite flag) when available.
        return rawVideoItems.value.firstOrNull { it.id == target.id } ?: target
    }

    fun nextVideoInQueue(): VideoItem? = queueNeighbor(1)
    fun previousVideoInQueue(): VideoItem? = queueNeighbor(-1)

    /** Moves to the next video of the queue. Returns false when there is none. */
    fun playNextVideo(): Boolean {
        val next = queueNeighbor(1) ?: return false
        _activePlayingVideo.value = next
        return true
    }

    fun playPreviousVideo(): Boolean {
        val prev = queueNeighbor(-1) ?: return false
        _activePlayingVideo.value = prev
        return true
    }

    fun closeVideoPlayer() {
        _activePlayingVideo.value = null
        _videoQueue.value = emptyList()
    }

    fun updateVideoPlaybackProgress(videoId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repository.updateVideoProgress(videoId, positionMs, durationMs)
        }
    }

    fun clearWatchHistory() { viewModelScope.launch { repository.clearWatchHistory() } }
    fun clearListeningHistory() { viewModelScope.launch { repository.clearListeningHistory() } }

    fun clearVideoPlaybackProgress(videoId: String) {
        viewModelScope.launch {
            repository.clearVideoProgress(videoId)
        }
    }

    fun showVideoDetails(video: VideoItem) {
        _videoForDetails.value = video
    }

    fun dismissVideoDetails() {
        _videoForDetails.value = null
    }

    fun setShowManageHiddenFolders(show: Boolean) {
        _showManageHiddenFoldersDialog.value = show
    }

    // Playback actions
    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = filteredAudioTracks.value) {
        playerManager.playTrack(track, queue)
        // Selecting a song opens the full player page; the mini player is the collapsed form.
        _isNowPlayingExpanded.value = true
    }

    /** Stops playback entirely and dismisses the mini player / Now Playing page. */
    fun stopPlayback() {
        _isNowPlayingExpanded.value = false
        playerManager.stop()
    }

    fun playNext(track: AudioTrack) {
        playerManager.playNext(track)
    }

    fun addToQueue(track: AudioTrack) {
        playerManager.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    /** Music sleep timer (countdown with fade-out, or end of the current song). */
    val sleepTimer = dev.asfakul.yumeiro.player.SleepTimer(
        scope = viewModelScope,
        positionMs = { playerManager.playbackState.value.currentPositionMs },
        durationMs = { playerManager.playbackState.value.durationMs },
        trackId = { playerManager.playbackState.value.currentTrack?.id },
        setVolume = playerManager::setPlayerVolume,
        pause = playerManager::pause,
    )

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerManager.cycleRepeatMode()
    }

    fun playQueueIndex(index: Int) {
        playerManager.playQueueIndex(index)
    }

    fun openNowPlaying() {
        _isNowPlayingExpanded.value = true
    }

    fun closeNowPlaying() {
        _isNowPlayingExpanded.value = false
    }

    fun clearPlaybackError() {
        playerManager.clearError()
    }

    // Track Action Sheet
    fun openTrackOptions(track: AudioTrack) {
        _actionTrack.value = track
    }

    fun closeTrackOptions() {
        _actionTrack.value = null
    }

    // Multi-Selection
    fun toggleSelectionMode(initialTrackId: String? = null) {
        val newMode = !_isSelectionMode.value
        _isSelectionMode.value = newMode
        if (newMode && initialTrackId != null) {
            _selectedTrackIds.value = setOf(initialTrackId)
        } else if (!newMode) {
            _selectedTrackIds.value = emptySet()
        }
    }

    fun toggleTrackSelection(trackId: String) {
        val current = _selectedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _selectedTrackIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAllTracks(tracks: List<AudioTrack>) {
        _selectedTrackIds.value = tracks.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedTrackIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun playSelectedTracks() {
        val selected = rawAudioTracks.value.filter { _selectedTrackIds.value.contains(it.id) }
        if (selected.isNotEmpty()) {
            playTrack(selected.first(), selected)
            clearSelection()
        }
    }

    fun addSelectedToFavorites() {
        viewModelScope.launch {
            repository.addFavorites(_selectedTrackIds.value.toList())
            clearSelection()
        }
    }

    fun addSelectedToQueue() {
        val selected = rawAudioTracks.value.filter { _selectedTrackIds.value.contains(it.id) }
        playerManager.addMultipleToQueue(selected)
        clearSelection()
    }

    // Playlist actions
    fun openAddToPlaylist(trackIds: List<String>) {
        _pendingPlaylistTrackIds.value = trackIds
        _showAddToPlaylistDialog.value = true
    }

    fun closeAddToPlaylist() {
        _showAddToPlaylistDialog.value = false
        _pendingPlaylistTrackIds.value = emptyList()
    }

    fun createPlaylist(name: String, trackIds: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.createPlaylist(name, trackIds)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            repository.addTracksToPlaylist(playlistId, trackIds)
            closeAddToPlaylist()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun onToggleFavorite(trackId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        repository.updatePermissionStatus(granted, permanentlyDenied)
        if (granted) {
            scanMedia()
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            repository.scanDeviceMedia()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        equalizerManager.setBandLevel(bandIndex, levelMb)
    }

    fun applyEqualizerPreset(presetName: String) {
        equalizerManager.applyPreset(presetName)
    }

    fun setBassBoostStrength(strength: Int) {
        equalizerManager.setBassBoostStrength(strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        equalizerManager.setVirtualizerStrength(strength)
    }

    fun resetEqualizerToDefault() {
        equalizerManager.resetToDefault()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
