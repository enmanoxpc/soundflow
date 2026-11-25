package com.soundflow.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundflow.music.SoundFlowApp
import com.soundflow.music.data.downloader.MusicDownloader
import com.soundflow.music.data.local.AudioScanner
import com.soundflow.music.data.local.PlaylistEntity
import com.soundflow.music.data.local.PlaylistSongCrossRef
import com.soundflow.music.data.model.Song
import com.soundflow.music.data.remote.YouTubeService
import com.soundflow.music.service.MusicController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab {
    LOCAL, STREAM, PLAYLIST
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioScanner = AudioScanner(application)
    private val youtubeService = YouTubeService()
    private val downloader = MusicDownloader(application)
    private val playlistDao = (application as SoundFlowApp).database.playlistDao()

    val musicController = MusicController(application)

    private val _currentTab = MutableStateFlow(AppTab.LOCAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // State Lagu Lokal (Offline)
    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // State Stream YouTube (Online)
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, String>> = _downloadStatus.asStateFlow()

    // State Playlists
    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    init {
        musicController.connect()
        loadLocalSongs()
        loadPlaylists()

        // Loop untuk update posisi playback pada seekbar
        viewModelScope.launch {
            while (isActive) {
                musicController.updateProgress()
                delay(500)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun loadLocalSongs() {
        viewModelScope.launch {
            _isScanning.value = true
            val songs = audioScanner.scanLocalSongs()
            _localSongs.value = songs
            _isScanning.value = false
        }
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            val results = youtubeService.searchSongs(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            if (song.isLocal) {
                musicController.playSong(song)
            } else {
                // Jika dari YouTube, resolve direct stream url terlebih dahulu
                val videoId = song.youtubeVideoId ?: return@launch
                val streamUrl = youtubeService.resolveAudioStreamUrl(videoId)
                if (streamUrl != null) {
                    val streamSong = song.copy(mediaUri = streamUrl)
                    musicController.playSong(streamSong)
                }
            }
        }
    }

    fun downloadSong(song: Song) {
        val videoId = song.youtubeVideoId ?: return
        viewModelScope.launch {
            _downloadStatus.value = _downloadStatus.value + (song.id to "Menyiapkan...")
            val streamUrl = youtubeService.resolveAudioStreamUrl(videoId)
            if (streamUrl == null) {
                _downloadStatus.value = _downloadStatus.value + (song.id to "Gagal mendapatkan audio")
                return@launch
            }

            downloader.downloadSong(
                song = song,
                streamUrl = streamUrl,
                onProgress = { progress ->
                    _downloadStatus.value = _downloadStatus.value + (song.id to "Mengunduh $progress%")
                },
                onCompleted = {
                    _downloadStatus.value = _downloadStatus.value + (song.id to "Tersimpan!")
                    // Refresh lagu lokal agar langsung terdeteksi di tab Offline
                    loadLocalSongs()
                },
                onError = { err ->
                    _downloadStatus.value = _downloadStatus.value + (song.id to "Error: $err")
                }
            )
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.createPlaylist(PlaylistEntity(name = name))
            loadPlaylists()
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    mediaUri = song.mediaUri,
                    artworkUri = song.artworkUri,
                    durationMs = song.durationMs,
                    isLocal = song.isLocal
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicController.release()
    }
}
