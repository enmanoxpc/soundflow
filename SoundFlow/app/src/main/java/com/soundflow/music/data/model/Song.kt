package com.soundflow.music.data.model

import android.net.Uri

/**
 * Model representasi lagu, baik file lokal maupun hasil stream dari YouTube.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val mediaUri: String, // URI lokal (content://...) atau URL remote streaming
    val artworkUri: String? = null,
    val isLocal: Boolean = true,
    val isDownloaded: Boolean = false,
    val filePath: String? = null,
    val youtubeVideoId: String? = null
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
