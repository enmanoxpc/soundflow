package com.soundflow.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.soundflow.music.ui.AppTab
import com.soundflow.music.ui.MainViewModel
import com.soundflow.music.ui.components.AddToPlaylistDialog
import com.soundflow.music.ui.components.BottomPlayerBar
import com.soundflow.music.ui.screens.LocalSongsScreen
import com.soundflow.music.ui.screens.PlaylistScreen
import com.soundflow.music.ui.screens.StreamScreen
import com.soundflow.music.ui.theme.DarkBackground
import com.soundflow.music.ui.theme.DarkSurface
import com.soundflow.music.ui.theme.PurplePrimary
import com.soundflow.music.ui.theme.SoundFlowTheme
import com.soundflow.music.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) {
            viewModel.loadLocalSongs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            SoundFlowTheme {
                val currentTab by viewModel.currentTab.collectAsState()
                val currentSong by viewModel.musicController.currentSong.collectAsState()
                val isPlaying by viewModel.musicController.isPlaying.collectAsState()
                val currentPosition by viewModel.musicController.currentPosition.collectAsState()
                val duration by viewModel.musicController.duration.collectAsState()
                val songForPlaylist by viewModel.songForPlaylistDialog.collectAsState()
                val playlists by viewModel.playlists.collectAsState()

                Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        Column {
                            BottomPlayerBar(
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                duration = duration,
                                onTogglePlayPause = { viewModel.musicController.togglePlayPause() },
                                onSkipNext = { viewModel.musicController.skipToNext() },
                                onSkipPrev = { viewModel.musicController.skipToPrevious() },
                                onSeekTo = { viewModel.musicController.seekTo(it) }
                            )

                            NavigationBar(
                                containerColor = DarkSurface,
                                tonalElevation = 4.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == AppTab.LOCAL,
                                    onClick = { viewModel.selectTab(AppTab.LOCAL) },
                                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Offline") },
                                    label = { Text("Lagu HP") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PurplePrimary,
                                        selectedTextColor = PurplePrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentTab == AppTab.STREAM,
                                    onClick = { viewModel.selectTab(AppTab.STREAM) },
                                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Stream") },
                                    label = { Text("Stream YouTube") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PurplePrimary,
                                        selectedTextColor = PurplePrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentTab == AppTab.PLAYLIST,
                                    onClick = { viewModel.selectTab(AppTab.PLAYLIST) },
                                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlist") },
                                    label = { Text("Playlist") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PurplePrimary,
                                        selectedTextColor = PurplePrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            AppTab.LOCAL -> {
                                val localSongs by viewModel.localSongs.collectAsState()
                                val isScanning by viewModel.isScanning.collectAsState()
                                LocalSongsScreen(
                                    songs = localSongs,
                                    isScanning = isScanning,
                                    onRefreshScan = { viewModel.loadLocalSongs() },
                                    onSongClick = { viewModel.playSong(it) },
                                    onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) }
                                )
                            }
                            AppTab.STREAM -> {
                                val searchResults by viewModel.searchResults.collectAsState()
                                val recommendedSongs by viewModel.recommendedSongs.collectAsState()
                                val isSearching by viewModel.isSearching.collectAsState()
                                val downloadStatus by viewModel.downloadStatus.collectAsState()
                                StreamScreen(
                                    searchResults = searchResults,
                                    recommendedSongs = recommendedSongs,
                                    isSearching = isSearching,
                                    downloadStatus = downloadStatus,
                                    onSearch = { viewModel.searchYouTube(it) },
                                    onPlayStream = { viewModel.playSong(it) },
                                    onDownload = { viewModel.downloadSong(it) },
                                    onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) }
                                )
                            }
                            AppTab.PLAYLIST -> {
                                PlaylistScreen(
                                    playlists = playlists,
                                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                    onGetPlaylistSongs = { viewModel.getSongsForPlaylist(it) },
                                    onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                                    onPlaySong = { viewModel.playSong(it) },
                                    onPlayPlaylist = { viewModel.playPlaylist(it) }
                                )
                            }
                        }
                    }

                    // Dialog Tambah ke Playlist
                    songForPlaylist?.let { song ->
                        AddToPlaylistDialog(
                            song = song,
                            playlists = playlists,
                            onDismiss = { viewModel.closeAddToPlaylistDialog() },
                            onAddToPlaylist = { playlistId, targetSong ->
                                viewModel.addSongToPlaylist(playlistId, targetSong)
                            },
                            onCreatePlaylist = { name ->
                                viewModel.createPlaylist(name)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
