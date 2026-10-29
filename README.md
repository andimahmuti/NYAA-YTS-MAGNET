[README.md](https://github.com/user-attachments/files/34143145/README.md)
<div align="center">

# ⚡ NYAA MAGNET & YTS MOVIES 🎬
### *The Ultimate 10-Foot & Mobile Anime & Movie Streaming & Torrent Companion*

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-blue.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material_3-Theming-orange.svg?style=for-the-badge&logo=materialdesign)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)]()

</div>

---

## 🌟 Overview

**NYAA Magnet & YTS Movies** is a cutting-edge, high-performance Android application designed for true anime enthusiasts and movie buffs. It bridges two massive content worlds—**Nyaa.si** (Anime) and **YTS.mx** (Movies)—into a single, gorgeously crafted Material 3 dark-themed UI with ultra-fast search engines, smart collections, and instant magnet link dispatch.

---

## 🔥 Key Features & Capabilities

### ⚡ 1. Dual Engine Architecture (Anime + Movies)
- Instantly switch between **⚡ NYAA Anime** and **🎬 YTS Movies** engines right from the bottom navigation bar.
- Dedicated scraper pipelines optimized for high-speed response and robust fallback parsing.

### 🔍 2. Powerful Search & Quick Clear
- Lightning-fast live search bars with a dedicated **`x` clear text button** for effortless query resetting.
- Rich filtering options: Browse by **Trending**, **Top Rated**, **Popular**, **Downloads**, or filter by release year (1970–2025).

### ❤️ 3. Smart Collections & Saved Items
- Bookmark any anime or movie with a single tap on the **Heart icon**.
- Dedicated **Saved / Favorites** view with customized empty states (**`No Anime Saved`** / **`No Movie Saved`**) and instant collection management.

### 🎯 4. Massive Related Suggestions (Top 30)
- Detailed dialogs featuring cover posters, synopses, ratings, and **up to 30 related/suggested movies or anime**.
- Add or remove recommendations to your favorites directly from the suggestion cards.

### 📥 5. Multiple Qualities & Magnet Dispatch
- Select from various torrent qualities (**720p W**, **720p B**, **1080p W**, **1080p B**) with file sizes and seed counts.
- One-tap **DOWNLOAD** or **OPEN TORRENT PAGE** integration that launches your preferred torrent client (`Flud`, `LibreTorrent`, etc.) instantly via magnet links.

### ✨ 6. Silky-Smooth Animations & Performance
- Built with Jetpack Compose `Crossfade` and `animate*AsState` for buttery-smooth **FADE** transitions across every page and tab switch.
- Fully optimized state caching ensuring zero reload latency when navigating between engines.

---

## 🛠️ Tech Stack & Architecture

- **UI Toolkit**: Jetpack Compose, Material 3, Coil (async image loading)
- **Architecture**: MVVM (Model-View-ViewModel), Kotlin Coroutines & `StateFlow`
- **Scraper & Networking**: OkHttp, Jsoup (HTML DOM parsing), AniList GraphQL API, YTS REST API v2
- **Performance & Build**: R8 code shrinking, Resource shrinking, ABI splits (`armeabi-v7a` and `x86`), Ultra-compressed APK generation.

---

## 📱 Screenshots & UI Layout

```
+---------------------------------------------------+
|  [🔍 Search Bar...]                 [x]      [❤️] |  <-- Top: Search & Favorites
+---------------------------------------------------+
|                                                   |
|                [Poster Grid View]                 |
|       (Avengers, Interstellar, Death Note...)     |
|                                                   |
+---------------------------------------------------+
|  [⚡ NYAA Anime]               [🎬 YTS Movies]    |  <-- Bottom: Engine Switcher
+---------------------------------------------------+
```

---

## 🚀 Installation & Building from Source

1. Clone or download the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/nyaa-yts-android.git
   ```
2. Open the project in **Android Studio** (Hedgehog or newer).
3. Let Gradle sync dependencies.
4. Build or run debug/release APKs (`Build > Build Bundle(s) / APK(s) > Build APK(s)`).

---

## 💛 Credits & Acknowledgments

Created with passion and dedication by **Andi Mahmuti** x **Gemini AI** x **Deepseek**.

---

<div align="center">
  <i>⭐ Star this repository if you find it useful!</i>
</div>
