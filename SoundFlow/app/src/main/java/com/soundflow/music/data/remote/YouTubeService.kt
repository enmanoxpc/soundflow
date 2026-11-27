package com.soundflow.music.data.remote

import com.soundflow.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service pencarian lagu dan resolusi URL audio YouTube
 * menggunakan YouTube InnerTube API resmi (cepat & stabil)
 * serta resolver audio stream via Invidious/Cobalt backend.
 */
class YouTubeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Resolver stream audio
    private val streamResolvers = listOf(
        "https://yt.chocolatemoo53.com",
        "https://invidious.nerdvpn.de",
        "https://inv.nadeko.net",
        "https://pipedapi.tokhmi.xyz"
    )

    /**
     * Pencarian YouTube resmi via InnerTube API
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20230526.01.00")
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("query", query)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use

                val responseStr = response.body?.string() ?: return@use
                val rootJson = JSONObject(responseStr)

                val contents = rootJson.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return@use

                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue
                    val itemSection = section.optJSONObject("itemSectionRenderer") ?: continue
                    val items = itemSection.optJSONArray("contents") ?: continue

                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val video = item.optJSONObject("videoRenderer") ?: continue

                        val videoId = video.optString("videoId")
                        if (videoId.isEmpty()) continue

                        // Ambil judul
                        val titleRuns = video.optJSONObject("title")?.optJSONArray("runs")
                        val title = titleRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Title"

                        // Ambil nama artis / channel
                        val channelRuns = video.optJSONObject("ownerText")?.optJSONArray("runs")
                            ?: video.optJSONObject("shortBylineText")?.optJSONArray("runs")
                        val channel = channelRuns?.optJSONObject(0)?.optString("text") ?: "YouTube Music"

                        // Thumbnail
                        val thumbs = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")

                        // Durasi (Cek lengthText, jika kosong cek thumbnailOverlays)
                        var durationText = video.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                        if (durationText.isBlank()) {
                            val overlays = video.optJSONArray("thumbnailOverlays")
                            if (overlays != null) {
                                for (k in 0 until overlays.length()) {
                                    val overlayObj = overlays.optJSONObject(k)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                                    val textObj = overlayObj?.optJSONObject("text")
                                    val overlayText = textObj?.optString("simpleText") ?: ""
                                    if (overlayText.isNotEmpty()) {
                                        durationText = overlayText
                                        break
                                    }
                                }
                            }
                        }

                        songs.add(
                            Song(
                                id = "yt_$videoId",
                                title = title,
                                artist = channel,
                                album = "YouTube Music",
                                durationMs = parseDuration(durationText),
                                mediaUri = "",
                                artworkUri = thumbUrl,
                                isLocal = false,
                                isDownloaded = false,
                                youtubeVideoId = videoId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songs
    }

    /**
     * Dapatkan daftar rekomendasi lagu hits terkini
     */
    suspend fun getTrendingMusic(): List<Song> {
        return searchSongs("Lagu Populer Indonesia Terkini")
    }

    /**
     * Dapatkan URL streaming audio langsung (M4A)
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        for (baseUrl in streamResolvers) {
            try {
                val url = "$baseUrl/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SoundFlow-Android/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val responseStr = response.body?.string() ?: return@use
                    val json = JSONObject(responseStr)
                    val formats = json.optJSONArray("adaptiveFormats") ?: return@use

                    var bestUrl: String? = null
                    var bestBitrate = 0

                    for (i in 0 until formats.length()) {
                        val format = formats.optJSONObject(i) ?: continue
                        val type = format.optString("type")
                        val streamUrl = format.optString("url")
                        val bitrate = format.optInt("bitrate", 0)

                        if (type.startsWith("audio/") && streamUrl.isNotEmpty()) {
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = streamUrl
                            }
                        }
                    }

                    if (bestUrl != null) {
                        return@withContext bestUrl
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 180_000L
        val parts = text.split(":")
        var totalSec = 0L
        for (p in parts) {
            totalSec = totalSec * 60 + (p.toLongOrNull() ?: 0L)
        }
        return totalSec * 1000L
    }
}
