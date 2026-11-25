package com.soundflow.music.data.remote

import com.soundflow.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service untuk pencarian lagu di YouTube dan resolusi URL audio streaming
 * menggunakan instance API Piped / Invidious publik yang cepat, stabil & tanpa iklan.
 */
class YouTubeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Daftar instance backend publik dengan fallback otomatis
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://piped-api.lunar.icu"
    )

    /**
     * Cari lagu di YouTube berdasarkan kata kunci (judul / artis)
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (baseInstance in pipedInstances) {
            try {
                val url = "$baseInstance/search?q=$encodedQuery&filter=music_songs"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SoundFlow-Android/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val responseBody = response.body?.string() ?: return@use
                    val json = JSONObject(responseBody)
                    val items = json.optJSONArray("items") ?: JSONArray()

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val itemType = item.optString("type")
                        if (itemType != "stream") continue

                        val urlSuffix = item.optString("url") // format: /watch?v=videoId
                        val videoId = urlSuffix.substringAfter("v=", "")
                        if (videoId.isEmpty()) continue

                        val title = item.optString("title")
                        val uploaderName = item.optString("uploaderName")
                        val thumbnail = item.optString("thumbnail")
                        val durationSeconds = item.optLong("duration", 0L)

                        songs.add(
                            Song(
                                id = "yt_$videoId",
                                title = title,
                                artist = uploaderName,
                                album = "YouTube Music",
                                durationMs = durationSeconds * 1000L,
                                mediaUri = "", // Akan di-resolve saat mau diputar / diunduh
                                artworkUri = thumbnail,
                                isLocal = false,
                                isDownloaded = false,
                                youtubeVideoId = videoId
                            )
                        )
                    }
                }

                if (songs.isNotEmpty()) {
                    return@withContext songs
                }
            } catch (e: Exception) {
                // Lanjut coba instance cadangan berikutnya jika terjadi timeout
                continue
            }
        }

        songs
    }

    /**
     * Dapatkan URL audio stream langsung (M4A / Opus) untuk diputar langsung di ExoPlayer
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        for (baseInstance in pipedInstances) {
            try {
                val url = "$baseInstance/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SoundFlow-Android/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val responseBody = response.body?.string() ?: return@use
                    val json = JSONObject(responseBody)
                    val audioStreams = json.optJSONArray("audioStreams") ?: return@use

                    // Cari stream audio dengan bitrate optimal (128kbps / 160kbps M4A / WebM)
                    var bestAudioUrl: String? = null
                    var maxBitrate = 0

                    for (i in 0 until audioStreams.length()) {
                        val streamObj = audioStreams.optJSONObject(i) ?: continue
                        val streamUrl = streamObj.optString("url")
                        val bitrate = streamObj.optInt("bitrate", 0)

                        if (streamUrl.isNotEmpty() && bitrate > maxBitrate) {
                            maxBitrate = bitrate
                            bestAudioUrl = streamUrl
                        }
                    }

                    if (bestAudioUrl != null) {
                        return@withContext bestAudioUrl
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }
}
