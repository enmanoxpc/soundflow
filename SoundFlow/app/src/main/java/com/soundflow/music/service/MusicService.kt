package com.soundflow.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.soundflow.music.MainActivity

/**
 * Service latar belakang (Background Foreground Service).
 * Memastikan musik TETAP MENYALA meskipun:
 * 1. Layar HP dimatikan / dikunci.
 * 2. Aplikasi di-minimize atau dibuka app lain (seperti buka game / medsos).
 * 3. Menghindari force close oleh Battery Saver Android dengan WAKE_MODE.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Konfigurasi Audio Attributes untuk Musik
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Buat ExoPlayer dengan WakeMode (CPU tetap hidup saat layar mati)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Otomatis pause jika headset dicabut
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL) // Anti mati saat layar mati
            .build()

        // 3. PendingIntent agar notifikasi lockscreen bisa diklik untuk membuka aplikasi kembali
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Inisialisasi MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
