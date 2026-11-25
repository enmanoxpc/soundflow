package com.soundflow.music.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.soundflow.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart Audio Scanner untuk memindai lagu lokal dari HP
 * dan MEMFILTER secara cerdas audio dari WhatsApp, Telegram, Ringtone, & Sistem.
 */
class AudioScanner(private val context: Context) {

    // Daftar folder yang pasti di-blacklist (Voice Note WA, Ringtone, System, dll)
    private val blacklistedFolderKeywords = listOf(
        "whatsapp",
        "whatsapp audio",
        "whatsapp voice notes",
        "opus",
        "telegram",
        "notifications",
        "ringtones",
        "alarms",
        "recordings",
        "call_recordings",
        "sound_recorder",
        "android/media/com.whatsapp"
    )

    // Durasi minimal lagu (45 detik) - filter ampuh membuang efek suara, notifikasi, dan VN pendek
    var minDurationMs: Long = 45_000L

    suspend fun scanLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val contentResolver = context.contentResolver

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Proyeksi kolom yang dibutuhkan
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, // Path absolut file di memori
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM
        )

        // Hanya cari file musik murni, durasi di atas minDurationMs, bukan nada dering/notif
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: ""
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""

                    // 1. FILTER CERDAS: Cek apakah path mengandung kata kunci WhatsApp atau folder terlarang
                    val lowerPath = filePath.lowercase()
                    val isBlacklistedPath = blacklistedFolderKeywords.any { keyword ->
                        lowerPath.contains(keyword)
                    }

                    // Jika masuk folder WhatsApp / Recorder / Ringtone, lewati langsung
                    if (isBlacklistedPath) {
                        continue
                    }

                    // 2. FILTER NAMA FILE: Voice note WA biasanya berformat AUD-xxxx atau PTT-xxxx
                    val fileName = filePath.substringAfterLast("/").lowercase()
                    if (fileName.startsWith("aud-") || fileName.startsWith("ptt-") || fileName.startsWith("rec_")) {
                        continue
                    }

                    // URI konten untuk ExoPlayer
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // URI Cover Album
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    songsList.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = if (artist == "<unknown>") "Artis Tidak Dikenal" else artist,
                            album = album,
                            durationMs = duration,
                            mediaUri = contentUri.toString(),
                            artworkUri = artworkUri,
                            isLocal = true,
                            filePath = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songsList
    }
}
