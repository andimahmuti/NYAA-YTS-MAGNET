package com.example.nyaamagnet

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MoviesScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchLiveResults(query: String = "", page: Int = 1, sort: String = "seeds", year: Int? = null): List<TorrentItem> {
        val params = StringBuilder("limit=50&page=$page&sort_by=$sort&order_by=desc")
        if (query.isNotBlank()) {
            params.append("&query_term=").append(URLEncoder.encode(query, "UTF-8"))
        }
        if (year != null) {
            params.append("&year=").append(year)
        }

        val urls = listOf(
            "https://yts.mx/api/v2/list_movies.json?$params",
            "https://movies-api.accel.li/api/v2/list_movies.json?$params"
        )

        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue
                val jsonStr = response.body?.string() ?: continue
                val root = JSONObject(jsonStr)
                val data = root.optJSONObject("data") ?: continue
                val movies = data.optJSONArray("movies") ?: continue

                val list = mutableListOf<TorrentItem>()
                for (i in 0 until movies.length()) {
                    val m = movies.getJSONObject(i)
                    val title = m.optString("title_long", m.optString("title", "Unknown Movie"))
                    val synopsis = m.optString("description_full", m.optString("summary", "No description available."))
                    val posterUrl = m.optString("large_cover_image", m.optString("medium_cover_image", ""))
                    val ratingVal = m.optDouble("rating", 0.0)
                    val yearVal = m.optInt("year", 0)
                    
                    val torrents = m.optJSONArray("torrents")
                    val optionsList = mutableListOf<MovieTorrent>()

                    if (torrents != null) {
                        for (j in 0 until torrents.length()) {
                            val t = torrents.getJSONObject(j)
                            val q = t.optString("quality", "")
                            val type = t.optString("type", "").lowercase()
                            val hash = t.optString("hash", "")
                            val size = t.optString("size", "N/A")
                            val seeds = t.optInt("seeds", 0)

                            if (q != "720p" && q != "1080p") continue

                            val qualityLabel = when {
                                q == "720p" && (type.contains("web") || type.contains("webdl") || type.contains("webrip")) -> "720p W"
                                q == "720p" -> "720p B"
                                q == "1080p" && (type.contains("web") || type.contains("webdl") || type.contains("webrip")) -> "1080p W"
                                else -> "1080p B"
                            }

                            val magnet = if (hash.isNotEmpty()) {
                                "magnet:?xt=urn:btih:$hash&dn=${URLEncoder.encode(title, "UTF-8")}&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.coppersurfer.tk:6969&tr=udp://tracker.leechers-paradise.org:6969"
                            } else {
                                t.optString("url", "")
                            }

                            optionsList.add(MovieTorrent(quality = qualityLabel, size = size, seeders = seeds, magnet = magnet))
                        }
                    }

                    if (optionsList.isEmpty()) continue

                    val mainMagnet = optionsList.firstOrNull()?.magnet ?: ""
                    val mainSize = optionsList.joinToString(" • ") { it.quality }
                    val mainSeeders = optionsList.maxOfOrNull { it.seeders } ?: 0
                    val ratingStr = if (ratingVal > 0) String.format("%.1f", ratingVal) else "N/A"

                    list.add(
                        TorrentItem(
                            id = m.optInt("id").toString(),
                            title = title,
                            magnet = mainMagnet,
                            size = mainSize,
                            seeders = mainSeeders,
                            time = if (yearVal > 0) yearVal.toString() else "N/A",
                            posterUrl = posterUrl.ifEmpty { null },
                            synopsis = synopsis,
                            rating = ratingStr,
                            torrentOptions = optionsList
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // Try next API mirror
            }
        }
        return emptyList()
    }
}
