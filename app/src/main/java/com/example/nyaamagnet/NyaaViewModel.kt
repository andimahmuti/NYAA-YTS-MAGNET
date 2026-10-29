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
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed class NyaaUiState {
    object Idle : NyaaUiState()
    object Loading : NyaaUiState()
    data class Success(val items: List<TorrentItem>) : NyaaUiState()
    data class Error(val message: String) : NyaaUiState()
}

data class AnilistAnime(val title: String, val image: String, val description: String, val score: Double, val type: String)

class NyaaViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient()
    private val sharedPrefs = application.getSharedPreferences("fav_v12", 0)

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
    private var isTrendingMode = true
    private var currentSort = "TRENDING_DESC"
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
        return obj.toString()
    }

    private fun deserializeItem(rawStr: String): TorrentItem? {
        if (rawStr.isBlank()) return null
        return try {
            if (rawStr.trimStart().startsWith("{")) {
                val obj = JSONObject(rawStr)
                TorrentItem(
                    id = obj.optString("id", "").ifEmpty { null },
                    title = obj.optString("title", "").ifEmpty { null },
                    magnet = obj.optString("magnet", "").ifEmpty { null },
                    size = obj.optString("size", "").ifEmpty { null },
                    seeders = obj.optInt("seeders", 0),
                    time = obj.optString("time", "").ifEmpty { null },
                    posterUrl = obj.optString("posterUrl", "").ifEmpty { null },
                    synopsis = obj.optString("synopsis", "").ifEmpty { null }
                )
            } else {
                val p = rawStr.split("|||")
                if (p.size >= 7) {
                    TorrentItem(
                        title = p[0].ifEmpty { null },
                        magnet = p[1].ifEmpty { null },
                        size = p[2].ifEmpty { null },
                        seeders = p[3].toIntOrNull(),
                        time = p[4].ifEmpty { null },
                        posterUrl = p[5].ifEmpty { null },
                        synopsis = p[6].ifEmpty { null }
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

    fun fetchTrending(sort: String = "TRENDING_DESC", year: Int? = null) {
        currentSearchQuery = ""
        currentPage = 1
        isTrendingMode = true
        currentSort = sort
        currentYear = year
        isLastPage = false
        viewModelScope.launch {
            _uiState.value = NyaaUiState.Loading
            try {
                val animeList = fetchAnilistTop(currentSort, currentYear, currentPage)
                _uiState.value = NyaaUiState.Success(animeList.map { TorrentItem(title=it.title, posterUrl=it.image, synopsis=it.description, seeders=it.score.toInt(), size=it.type, time="Top Rated") })
            } catch (e: Exception) { _uiState.value = NyaaUiState.Error("Load failed") }
        }
    }

    fun searchTorrents(q: String) {
        if (q.isBlank()) { fetchTrending(); return }
        currentSearchQuery = q; currentPage = 1; isTrendingMode = false; isLastPage = false
        viewModelScope.launch {
            _uiState.value = NyaaUiState.Loading
            try {
                val items = withContext(Dispatchers.IO) { NyaaScraper.fetchLiveResults(q, 1) }.sortedByDescending { it.seeders ?: 0 }
                if (items.isEmpty()) _uiState.value = NyaaUiState.Error("No results")
                else {
                    _uiState.value = NyaaUiState.Success(items)
                    fetchAnilistMetadata(items)
                }
            } catch (e: Exception) { _uiState.value = NyaaUiState.Error("Search failed") }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is NyaaUiState.Success || isLastPage) return
        viewModelScope.launch {
            currentPage++
            try {
                val next = if (isTrendingMode) {
                    fetchAnilistTop(currentSort, currentYear, currentPage).map { TorrentItem(title=it.title, posterUrl=it.image, synopsis=it.description, seeders=it.score.toInt(), size=it.type, time="Top Rated") }
                } else {
                    withContext(Dispatchers.IO) { NyaaScraper.fetchLiveResults(currentSearchQuery, currentPage) }.sortedByDescending { it.seeders ?: 0 }
                }
                if (next.isEmpty()) isLastPage = true else {
                    _uiState.value = NyaaUiState.Success(state.items + next)
                    if (!isTrendingMode) fetchAnilistMetadata(next)
                }
            } catch (e: Exception) { currentPage-- }
        }
    }

    fun fetchSuggestionsForItem(item: TorrentItem) {
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            _suggestions.value = emptyList()

            val list = mutableListOf<TorrentItem>()
            val name = extractShowName(item.title ?: "")

            if (name.isNotBlank()) {
                val recs = fetchAnimeRecommendations(name)
                for (rec in recs) {
                    if (list.size >= 30) break
                    if (rec.title != null && !rec.title.equals(item.title, ignoreCase = true) && list.none { it.title.equals(rec.title, ignoreCase = true) }) {
                        list.add(rec)
                    }
                }
            }

            // Fallback to current items / favorites if fewer than 30
            if (list.size < 30) {
                val currentList = (uiState.value as? NyaaUiState.Success)?.items ?: emptyList()
                val candidates = (currentList + favorites.value).distinctBy { it.title }
                for (candidate in candidates) {
                    if (list.size >= 30) break
                    if (!candidate.title.isNullOrBlank() && candidate.title != item.title && list.none { it.title == candidate.title }) {
                        list.add(candidate)
                    }
                }
            }

            _suggestions.value = list.take(30)
            _isLoadingSuggestions.value = false
        }
    }

    fun resolveMagnetForItem(item: TorrentItem, onResolved: (TorrentItem) -> Unit) {
        if (!item.magnet.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val name = extractShowName(item.title ?: "")
                if (name.isNotBlank()) {
                    val results = withContext(Dispatchers.IO) { NyaaScraper.fetchLiveResults(name, 1) }
                    val best = results.maxByOrNull { it.seeders ?: 0 }
                    if (best != null && !best.magnet.isNullOrBlank()) {
                        val updated = item.copy(
                            magnet = best.magnet,
                            size = best.size ?: item.size,
                            seeders = best.seeders ?: item.seeders
                        )
                        onResolved(updated)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private suspend fun fetchAnimeRecommendations(showName: String): List<TorrentItem> = withContext(Dispatchers.IO) {
        val query = "query (\$s: String) { Media (search: \$s, type: ANIME) { id recommendations (perPage: 30, sort: [RATING_DESC]) { nodes { mediaRecommendation { id title { english romaji } coverImage { large } description averageScore format } } } } }"
        val body = JSONObject().put("query", query).put("variables", JSONObject().put("s", showName)).toString().toRequestBody("application/json".toMediaType())
        val list = mutableListOf<TorrentItem>()
        try {
            val res = client.newCall(Request.Builder().url("https://graphql.anilist.co").post(body).build()).execute().body?.string() ?: ""
            val mediaObj = JSONObject(res).optJSONObject("data")?.optJSONObject("Media")
            val recNodes = mediaObj?.optJSONObject("recommendations")?.optJSONArray("nodes")

            if (recNodes != null) {
                for (i in 0 until recNodes.length()) {
                    val recMedia = recNodes.getJSONObject(i).optJSONObject("mediaRecommendation") ?: continue
                    val idStr = recMedia.optInt("id", 0).toString()
                    val titleObj = recMedia.optJSONObject("title")
                    val englishTitle = titleObj?.optString("english")
                    val romajiTitle = titleObj?.optString("romaji")
                    val title = if (!englishTitle.isNullOrEmpty() && englishTitle != "null") englishTitle else (romajiTitle ?: "Anime")

                    val poster = recMedia.optJSONObject("coverImage")?.optString("large")
                    val desc = recMedia.optString("description", "").replace(Regex("<.*?>"), "")
                    val score = recMedia.optDouble("averageScore", 0.0)
                    val format = recMedia.optString("format", "TV")

                    list.add(
                        TorrentItem(
                            id = idStr,
                            title = title,
                            posterUrl = poster,
                            synopsis = desc,
                            rating = if (score > 0) String.format("%.1f", score / 10.0) else "N/A",
                            size = format,
                            time = "Top"
                        )
                    )
                }
            }
        } catch (e: Exception) {}
        list
    }

    private fun fetchAnilistMetadata(items: List<TorrentItem>) {
        viewModelScope.launch {
            val seen = mutableSetOf<String>()
            val itemsToProcess = items.take(30)
            
            itemsToProcess.forEach { item ->
                val name = extractShowName(item.title ?: "")
                if (name.length < 2 || seen.contains(name)) {
                    updateSimilarItems(name, items)
                    return@forEach
                }
                seen.add(name)
                
                try {
                    delay(300)
                    var res = searchOnAnilist(name)
                    
                    if (res == null && name.split(" ").size > 2) {
                        val simpleName = name.split(" ").take(2).joinToString(" ")
                        delay(300)
                        res = searchOnAnilist(simpleName)
                    }
                    
                    if (res != null) {
                        applyMetadataToAllMatches(name, res, items)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun applyMetadataToAllMatches(name: String, res: Pair<String, String>, items: List<TorrentItem>) {
        val currentState = _uiState.value
        if (currentState is NyaaUiState.Success) {
            val updated = currentState.items.map { 
                if (extractShowName(it.title ?: "") == name) it.copy(posterUrl=res.first, synopsis=res.second) else it 
            }
            _uiState.value = NyaaUiState.Success(updated)
        }
    }

    private fun updateSimilarItems(name: String, items: List<TorrentItem>) {
        val match = items.firstOrNull { it.posterUrl != null && extractShowName(it.title ?: "") == name }
        if (match != null) {
            applyMetadataToAllMatches(name, (match.posterUrl!! to (match.synopsis ?: "")), items)
        }
    }

    private suspend fun fetchAnilistTop(sort: String, year: Int?, page: Int): List<AnilistAnime> = withContext(Dispatchers.IO) {
        val query = "query (\$s: [MediaSort], \$y: Int, \$p: Int) { Page (page: \$p, perPage: 30) { media (type: ANIME, sort: \$s, seasonYear: \$y) { title { english romaji } coverImage { large } description averageScore format } } }"
        val vars = JSONObject().put("s", sort).put("y", year ?: JSONObject.NULL).put("p", page)
        val body = JSONObject().put("query", query).put("variables", vars).toString().toRequestBody("application/json".toMediaType())
        val res = client.newCall(Request.Builder().url("https://graphql.anilist.co").post(body).build()).execute().body?.string() ?: ""
        val list = mutableListOf<AnilistAnime>()
        try {
            val nodes = JSONObject(res).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
            for (i in 0 until nodes.length()) {
                val o = nodes.getJSONObject(i)
                val t = o.getJSONObject("title").optString("english").let { if (it == "null" || it.isEmpty()) o.getJSONObject("title").getString("romaji") else it }
                list.add(AnilistAnime(t, o.getJSONObject("coverImage").getString("large"), o.optString("description", "").replace(Regex("<.*?>"), ""), o.optDouble("averageScore", 0.0), o.optString("format", "TV")))
            }
        } catch (e: Exception) {}
        list
    }

    private suspend fun searchOnAnilist(name: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val query = "query (\$s: String) { Media (search: \$s, type: ANIME) { coverImage { large } description } }"
        val body = JSONObject().put("query", query).put("variables", JSONObject().put("s", name)).toString().toRequestBody("application/json".toMediaType())
        try {
            val res = client.newCall(Request.Builder().url("https://graphql.anilist.co").post(body).build()).execute().body?.string()
            val data = JSONObject(res ?: "").optJSONObject("data")?.optJSONObject("Media")
            if (data != null) return@withContext data.getJSONObject("coverImage").getString("large") to data.optString("description", "No info.").replace(Regex("<.*?>"), "")
        } catch (e: Exception) {}
        null
    }

    private fun extractShowName(title: String): String {
        var t = title.replace(".", " ").replace("_", " ")
        t = t.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "").trim()
        val stopIndex = Regex("(?i)[-–]|\\d+v|\\d+\\s+Episodes?|\\s+\\d+|\\s+E\\d+|\\s+S\\d+|\\d{3,4}p").find(t)?.range?.first ?: t.length
        t = t.substring(0, stopIndex).trim()
        t = t.replace(Regex("(?i)COMPLETE|Batch|DBox|REGRADE|Season|Series|Dual Audio|Multi-Sub|Remastered|UNCUT|Restored|BD|BluRay"), "").trim()
        return t.replace(Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$"), "").trim()
    }
}
