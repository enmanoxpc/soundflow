package com.soundflow.music.data.downloader

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.soundflow.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Downloader lagu dari mode stream ke file audio offline di HP.
 * File disimpan di folder standar: Music/SoundFlow/
 * dan otomatis didaftarkan ke MediaStore agar muncul di tab Lagu Lokal.
 */
class MusicDownloader(private val context: Context) {

    private val httpClient = OkHttpClient()

    /**
     * Download audio stream dan simpan sebagai file audio lokal
     */
    suspend fun downloadSong(
        song: Song,
        streamUrl: String,
        onProgress: (Int) -> Unit = {},
        onCompleted: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "SoundFlow"
            )
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }

            // Bersihkan nama file dari karakter ilegal Android
            val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val fileName = "$safeArtist - $safeTitle.m4a"
            val targetFile = File(musicDir, fileName)

            val request = Request.Builder()
                .url(streamUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError("Gagal download: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    onError("Respon kosong dari server")
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgress(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Daftarkan ke MediaStore agar terdeteksi sebagai lagu lokal
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mp4", "audio/m4a", "audio/mpeg")
                ) { path, uri ->
                    // Berhasil terindeks di sistem HP
                }

                onCompleted(targetFile)
            }
        } catch (e: Exception) {
            onError("Error saat mengunduh: ${e.localizedMessage}")
        }
    }
}
