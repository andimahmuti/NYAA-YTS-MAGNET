package com.example.nyaamagnet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MoviesViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val sharedPrefs = application.getSharedPreferences("fav_movies_v1", 0)

    private val _uiState = MutableStateFlow<NyaaUiState>(NyaaUiState.Idle)
    val uiState: StateFlow<NyaaUiState> = _uiState.asStateFlow()

    private val _favorites = MutableStateFlow<List<TorrentItem>>(emptyList())
    val favorites: StateFlow<List<TorrentItem>> = _favorites.asStateFlow()

    private val _suggestions = MutableStateFlow<List<TorrentItem>>(emptyList())
    val suggestions: StateFlow<List<TorrentItem>> = _suggestions.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private var currentSearchQuery = ""
    private var currentPage = 1
    private var currentSort = "seeds"
    private var currentYear: Int? = null
    private var isLastPage = false

    init {
        loadFavorites()
        fetchTrending()
    }

    private fun serializeItem(item: TorrentItem): String {
        val obj = JSONObject()
        obj.put("id", item.id ?: "")
        obj.put("title", item.title ?: "")
        obj.put("magnet", item.magnet ?: "")
        obj.put("size", item.size ?: "")
        obj.put("seeders", item.seeders ?: 0)
        obj.put("time", item.time ?: "")
        obj.put("posterUrl", item.posterUrl ?: "")
        obj.put("synopsis", item.synopsis ?: "")
        obj.put("rating", item.rating ?: "")

        val optArray = JSONArray()
        item.torrentOptions.forEach { opt ->
            val optObj = JSONObject()
            optObj.put("q", opt.quality)
            optObj.put("s", opt.size)
            optObj.put("sd", opt.seeders)
            optObj.put("m", opt.magnet)
            optArray.put(optObj)
        }
        obj.put("options", optArray)
        return obj.toString()
    }

    private fun deserializeOptions(jsonStr: String): List<MovieTorrent> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<MovieTorrent>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MovieTorrent(
                        quality = obj.optString("q", ""),
                        size = obj.optString("s", ""),
                        seeders = obj.optInt("sd", 0),
                        magnet = obj.optString("m", "")
                    )
                )
            }
        } catch (e: Exception) {
            // Ignored
        }
        return list
    }

    private fun deserializeItem(rawStr: String): TorrentItem? {
        if (rawStr.isBlank()) return null
        return try {
            if (rawStr.trimStart().startsWith("{")) {
                val obj = JSONObject(rawStr)
                val optionsList = mutableListOf<MovieTorrent>()
                val optArray = obj.optJSONArray("options")
                if (optArray != null) {
                    for (i in 0 until optArray.length()) {
                        val optObj = optArray.getJSONObject(i)
                        optionsList.add(
                            MovieTorrent(
                                quality = optObj.optString("q", ""),
                                size = optObj.optString("s", ""),
                                seeders = optObj.optInt("sd", 0),
                                magnet = optObj.optString("m", "")
                            )
                        )
                    }
                }
                val itemId = obj.optString("id", "").ifEmpty { null }
                val itemTitle = obj.optString("title", "").ifEmpty { null }
                val itemMagnet = obj.optString("magnet", "").ifEmpty { null }
                val itemSize = obj.optString("size", "").ifEmpty { null }
                val itemSeeders = obj.optInt("seeders", 0)
                val itemTime = obj.optString("time", "").ifEmpty { null }
                val itemPoster = obj.optString("posterUrl", "").ifEmpty { null }
                val itemSynopsis = obj.optString("synopsis", "").ifEmpty { null }
                val itemRating = obj.optString("rating", "").ifEmpty { null }

                if (optionsList.isEmpty() && !itemMagnet.isNullOrBlank()) {
                    optionsList.add(
                        MovieTorrent(
                            quality = itemSize ?: "DOWNLOAD",
                            size = "",
                            seeders = itemSeeders,
                            magnet = itemMagnet
                        )
                    )
                }

                TorrentItem(
                    id = itemId,
                    title = itemTitle,
                    magnet = itemMagnet,
                    size = itemSize,
                    seeders = itemSeeders,
                    time = itemTime,
                    posterUrl = itemPoster,
                    synopsis = itemSynopsis,
                    rating = itemRating,
                    torrentOptions = optionsList
                )
            } else {
                val p = rawStr.split("|||", limit = 9)
                if (p.size >= 7) {
                    val rating = if (p.size >= 8) p[7].ifEmpty { null } else null
                    val options = if (p.size >= 9) deserializeOptions(p[8]) else emptyList()
                    val mutableOptions = options.toMutableList()

                    val title = p[0].ifEmpty { null }
                    val magnet = p[1].ifEmpty { null }
                    val size = p[2].ifEmpty { null }
                    val seeders = p[3].toIntOrNull()
                    val time = p[4].ifEmpty { null }
                    val posterUrl = p[5].ifEmpty { null }
                    val synopsis = p[6].ifEmpty { null }

                    if (mutableOptions.isEmpty() && !magnet.isNullOrBlank()) {
                        mutableOptions.add(
                            MovieTorrent(
                                quality = size ?: "DOWNLOAD",
                                size = "",
                                seeders = seeders ?: 0,
                                magnet = magnet
                            )
                        )
                    }

                    TorrentItem(
                        title = title,
                        magnet = magnet,
                        size = size,
                        seeders = seeders,
                        time = time,
                        posterUrl = posterUrl,
                        synopsis = synopsis,
                        rating = rating,
                        torrentOptions = mutableOptions
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFavorites() {
        val list = mutableListOf<TorrentItem>()
        sharedPrefs.all.forEach { (_, v) ->
            if (v is String) {
                val item = deserializeItem(v)
                if (item != null) list.add(item)
            }
        }
        _favorites.value = list
    }

    fun toggleFavorite(item: TorrentItem) {
        val keyId = item.magnet?.ifEmpty { null } ?: item.title ?: return
        val key = "f_${keyId.hashCode()}"
        val curr = _favorites.value.toMutableList()
        val index = curr.indexOfFirst { 
            val k = it.magnet?.ifEmpty { null } ?: it.title
            k == keyId
        }

        if (index != -1) {
            curr.removeAt(index)
            sharedPrefs.edit().remove(key).apply()
        } else {
            curr.add(item)
            val data = serializeItem(item)
            sharedPrefs.edit().putString(key, data).apply()
        }
        _favorites.value = curr
    }

    fun fetchTrending(sort: String = "seeds", year: Int? = null) {
        currentSearchQuery = ""
        currentPage = 1
        currentSort = sort
        currentYear = year
        isLastPage = false
        viewModelScope.launch {
            _uiState.value = NyaaUiState.Loading
            try {
                val items = withContext(Dispatchers.IO) { MoviesScraper.fetchLiveResults("", 1, currentSort, currentYear) }
                if (items.isEmpty()) _uiState.value = NyaaUiState.Error("No movies found")
                else _uiState.value = NyaaUiState.Success(items)
            } catch (e: Exception) {
                _uiState.value = NyaaUiState.Error("Load failed")
            }
        }
    }

    fun searchMovies(q: String) {
        if (q.isBlank()) { fetchTrending(); return }
        currentSearchQuery = q
        currentPage = 1
        isLastPage = false
        viewModelScope.launch {
            _uiState.value = NyaaUiState.Loading
            try {
                val items = withContext(Dispatchers.IO) { MoviesScraper.fetchLiveResults(q, 1, currentSort, currentYear) }
                if (items.isEmpty()) _uiState.value = NyaaUiState.Error("No results found")
                else _uiState.value = NyaaUiState.Success(items)
            } catch (e: Exception) {
                _uiState.value = NyaaUiState.Error("Search failed")
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is NyaaUiState.Success || isLastPage) return
        viewModelScope.launch {
            currentPage++
            try {
                val next = withContext(Dispatchers.IO) { MoviesScraper.fetchLiveResults(currentSearchQuery, currentPage, currentSort, currentYear) }
                if (next.isEmpty()) isLastPage = true
                else {
                    _uiState.value = NyaaUiState.Success(state.items + next)
                }
            } catch (e: Exception) {
                currentPage--
            }
        }
    }

    fun fetchSuggestionsForItem(item: TorrentItem) {
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            _suggestions.value = emptyList()

            val result = fetchMovieSuggestions(item).toMutableList()

            if (result.size < 30) {
                val currentList = (uiState.value as? NyaaUiState.Success)?.items ?: emptyList()
                val candidates = (currentList + favorites.value).distinctBy { it.title }
                for (candidate in candidates) {
                    if (result.size >= 30) break
                    if (!candidate.title.isNullOrBlank() && candidate.title != item.title && result.none { it.title == candidate.title }) {
                        result.add(candidate)
                    }
                }
            }

            _suggestions.value = result.take(30)
            _isLoadingSuggestions.value = false
        }
    }

    private suspend fun fetchMovieSuggestions(item: TorrentItem): List<TorrentItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TorrentItem>()
        val movieId = item.id

        if (!movieId.isNullOrBlank() && movieId != "0") {
            val urls = listOf(
                "https://yts.mx/api/v2/movie_suggestions.json?movie_id=$movieId",
                "https://movies-api.accel.li/api/v2/movie_suggestions.json?movie_id=$movieId"
            )
            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) continue
                    val jsonStr = response.body?.string() ?: continue
                    val root = JSONObject(jsonStr)
                    val data = root.optJSONObject("data") ?: continue
                    val movies = data.optJSONArray("movies") ?: continue

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
                    if (list.isNotEmpty()) break
                } catch (e: Exception) {
                    // Try next mirror
                }
            }
        }

        if (list.size < 30) {
            val firstWord = item.title?.split(" ")?.firstOrNull { it.length > 2 } ?: ""
            if (firstWord.isNotBlank()) {
                val searchResults = MoviesScraper.fetchLiveResults(query = firstWord, page = 1)
                for (resItem in searchResults) {
                    if (list.size >= 30) break
                    if (resItem.title != item.title && list.none { it.title == resItem.title }) {
                        list.add(resItem)
                    }
                }
            }
        }

        return@withContext list.take(30)
    }
}
