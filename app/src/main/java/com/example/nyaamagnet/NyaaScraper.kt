package com.example.nyaamagnet

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

object NyaaScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun fetchLiveResults(query: String, page: Int = 1): List<TorrentItem> {
        val url = "https://nyaa.si/?f=0&c=0_0&q=$query&s=seeders&o=desc&p=$page"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val rows = doc.select("tr.default, tr.success, tr.danger")
            
            rows.mapNotNull { row ->
                val columns = row.select("td")
                if (columns.size < 8) return@mapNotNull null
                
                // CRITICAL FIX: Select the actual title link, avoiding comment links
                val titleElement = columns[1].select("a").firstOrNull { 
                    val href = it.attr("href")
                    href.startsWith("/view/") && !href.contains("#comments") 
                }
                
                val title = titleElement?.text() ?: return@mapNotNull null
                val magnet = columns[2].select("a[href^=magnet:]").attr("href")
                val size = columns[3].text()
                val date = columns[4].text()
                val seeders = columns[5].text().toIntOrNull() ?: 0
                val leechers = columns[6].text().toIntOrNull() ?: 0
                val id = titleElement.attr("href").substringAfterLast("/")
                
                TorrentItem(
                    id = id,
                    title = title,
                    magnet = magnet,
                    size = size,
                    time = date,
                    seeders = seeders,
                    leechers = leechers
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
