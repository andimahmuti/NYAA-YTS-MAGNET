package com.example.nyaamagnet

data class MovieTorrent(
    val quality: String,
    val size: String,
    val seeders: Int,
    val magnet: String
)

data class TorrentItem(
    val title: String? = null,
    val magnet: String? = null,
    val size: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val time: String? = null,
    val category: String? = null,
    val link: String? = null,
    val torrent: String? = null,
    val downloads: Int? = null,
    val id: String? = null,
    var posterUrl: String? = null,
    var synopsis: String? = null,
    var rating: String? = null,
    var torrentOptions: List<MovieTorrent> = emptyList()
)
