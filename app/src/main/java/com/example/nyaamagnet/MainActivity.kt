package com.example.nyaamagnet

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Architecture orientation control:
        // x86 -> Landscape mode with Image #5
        // arm v7 -> Portrait mode with Image #4
        val abis = Build.SUPPORTED_ABIS
        val isX86 = abis.any { it.contains("x86", ignoreCase = true) }
        val isArmV7 = abis.any { it.contains("armeabi-v7a", ignoreCase = true) || it.contains("armv7", ignoreCase = true) }

        if (isX86) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (isArmV7) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val bgRes = if (isX86) R.drawable.bg_landscape else R.drawable.bg_portrait

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF4CAF50), // Vibrant Green
                background = Color.Transparent,
                surface = Color(0xFF281912),
                surfaceVariant = Color(0xFF33221A)
            )) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = bgRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                    )

                    RootApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootApp(
    nyaaViewModel: NyaaViewModel = viewModel(),
    moviesViewModel: MoviesViewModel = viewModel()
) {
    var selectedEngine by remember { mutableIntStateOf(0) } // 0 = NYAA Anime, 1 = YTS Movies

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = selectedEngine, animationSpec = tween(200), label = "EngineFade") { engine ->
                if (engine == 0) {
                    NyaaApp(nyaaViewModel)
                } else {
                    MoviesApp(moviesViewModel)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().background(Color(0xFF241611))) {
            Text(
                "Created with love by Andi Mahmuti x Gemini AI x Deepseek 💛",
                fontSize = 9.sp,
                color = Color(0xFFB0A090),
                modifier = Modifier.padding(top = 4.dp),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedEngine == 0,
                    onClick = { selectedEngine = 0 },
                    label = { Text("⚡ NYAA Anime", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4CAF50),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF3B251D),
                        labelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedEngine == 1,
                    onClick = { selectedEngine = 1 },
                    label = { Text("🎬 YTS Movies", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4CAF50),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF3B251D),
                        labelColor = Color.White
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyaaApp(viewModel: NyaaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<TorrentItem?>(null) }
    var currentScreen by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Saved
    var query by remember { mutableStateOf("") }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            viewModel.fetchSuggestionsForItem(selectedItem!!)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Crossfade(targetState = currentScreen, animationSpec = tween(200), label = "ScreenFade") { screen ->
            if (screen == 0) {
                SearchScreen(
                    query, 
                    { query = it }, 
                    { viewModel.searchTorrents(query) }, 
                    { sort -> viewModel.fetchTrending(sort) },
                    { year -> viewModel.fetchTrending(year = year) },
                    { viewModel.loadNextPage() },
                    uiState, 
                    favorites, 
                    { selectedItem = it }, 
                    { viewModel.toggleFavorite(it) },
                    { currentScreen = 1 }
                )
            } else {
                FavoritesScreen(
                    favorites, 
                    "No Anime Saved", 
                    { selectedItem = it }, 
                    { viewModel.toggleFavorite(it) },
                    { currentScreen = 0 }
                )
            }
        }

        if (selectedItem != null) {
            DetailDialog(
                item = selectedItem!!,
                suggestions = suggestions,
                favorites = favorites,
                isLoadingSuggestions = isLoadingSuggestions,
                onSelectSuggestion = { suggestion ->
                    selectedItem = suggestion
                    viewModel.fetchSuggestionsForItem(suggestion)
                    viewModel.resolveMagnetForItem(suggestion) { updated ->
                        if (selectedItem?.title == updated.title) {
                            selectedItem = updated
                        }
                    }
                },
                onFavSuggestion = { suggestion ->
                    viewModel.toggleFavorite(suggestion)
                },
                onDismiss = { selectedItem = null }
            ) { magnet ->
                if (!magnet.isNullOrBlank() && magnet.startsWith("magnet:", ignoreCase = true)) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app found to open magnet link", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val searchTitle = selectedItem?.title ?: ""
                    selectedItem = null
                    if (searchTitle.isNotBlank()) {
                        query = searchTitle
                        currentScreen = 0
                        viewModel.searchTorrents(searchTitle)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesApp(viewModel: MoviesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<TorrentItem?>(null) }
    var currentScreen by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Saved
    var query by remember { mutableStateOf("") }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            viewModel.fetchSuggestionsForItem(selectedItem!!)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Crossfade(targetState = currentScreen, animationSpec = tween(200), label = "ScreenFade") { screen ->
            if (screen == 0) {
                MoviesSearchScreen(
                    query, 
                    { query = it }, 
                    { viewModel.searchMovies(query) }, 
                    { sort -> viewModel.fetchTrending(sort) },
                    { viewModel.loadNextPage() },
                    uiState, 
                    favorites, 
                    { selectedItem = it }, 
                    { viewModel.toggleFavorite(it) },
                    { currentScreen = 1 }
                )
            } else {
                FavoritesScreen(
                    favorites, 
                    "No Movie Saved", 
                    { selectedItem = it }, 
                    { viewModel.toggleFavorite(it) },
                    { currentScreen = 0 }
                )
            }
        }

        if (selectedItem != null) {
            DetailDialog(
                item = selectedItem!!,
                suggestions = suggestions,
                favorites = favorites,
                isLoadingSuggestions = isLoadingSuggestions,
                onSelectSuggestion = { suggestion ->
                    selectedItem = suggestion
                    viewModel.fetchSuggestionsForItem(suggestion)
                },
                onFavSuggestion = { suggestion ->
                    viewModel.toggleFavorite(suggestion)
                },
                onDismiss = { selectedItem = null }
            ) { magnet ->
                if (!magnet.isNullOrBlank() && magnet.startsWith("magnet:", ignoreCase = true)) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app found to open magnet link", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val searchTitle = selectedItem?.title ?: ""
                    selectedItem = null
                    if (searchTitle.isNotBlank()) {
                        query = searchTitle
                        currentScreen = 0
                        viewModel.searchMovies(searchTitle)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String, 
    onQuery: (String) -> Unit, 
    onSearch: () -> Unit, 
    onSort: (String) -> Unit,
    onYear: (Int) -> Unit,
    onLoadMore: () -> Unit,
    state: NyaaUiState, 
    favs: List<TorrentItem>, 
    onItem: (TorrentItem) -> Unit, 
    onFav: (TorrentItem) -> Unit,
    onOpenSaved: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = query, 
                onValueChange = onQuery, 
                modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)), 
                placeholder = { Text("Search Anime TITAN-X...", color = Color(0xFF908070), fontSize = 14.sp) }, 
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF281A14), unfocusedContainerColor = Color(0xFF281A14), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSearch, 
                modifier = Modifier.size(52.dp).shadow(8.dp, CircleShape), 
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.Black)
            ) { 
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp)) 
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onOpenSaved,
                modifier = Modifier.size(52.dp).background(Color(0xFF281A14), CircleShape).shadow(8.dp, CircleShape)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Saved", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
            }
        }
        
        Spacer(Modifier.height(10.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            val filterScrollState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            
            IconButton(onClick = { 
                coroutineScope.launch { filterScrollState.animateScrollBy(-400f) }
            }) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFB0A090)) }

            LazyRow(
                state = filterScrollState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(onClick = { onSort("TRENDING_DESC") }, label = { Text("Trending") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                item { FilterChip(onClick = { onSort("SCORE_DESC") }, label = { Text("Top Rated") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                item { FilterChip(onClick = { onSort("POPULARITY_DESC") }, label = { Text("Popular") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                items((1970..2025).reversed().toList()) { year ->
                    FilterChip(onClick = { onYear(year) }, label = { Text(year.toString()) }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White))
                }
            }

            IconButton(onClick = { 
                coroutineScope.launch { filterScrollState.animateScrollBy(400f) }
            }) { Icon(Icons.Default.ArrowForward, null, tint = Color(0xFFB0A090)) }
        }

        Spacer(Modifier.height(10.dp))

        when (state) {
            is NyaaUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4CAF50)) }
            is NyaaUiState.Success -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                        lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
                    }
                }

                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        onLoadMore()
                    }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { (it.magnet ?: "") + (it.title ?: "") }) { item ->
                        PremiumGridCard(
                            item = item, 
                            isFav = favs.any { (it.magnet ?: it.title) == (item.magnet ?: item.title) }, 
                            onFav = { onFav(item) },
                            onClick = { onItem(item) }
                        )
                    }
                    
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            Text("No more results", color = Color(0xFFB0A090), fontSize = 12.sp)
                        }
                    }
                }
            }
            is NyaaUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.message, color = Color(0xFFFF6B6B)) }
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Search for your anime Titan...", color = Color(0xFFB0A090)) }
        }
    }
}

@Composable
fun MoviesSearchScreen(
    query: String, 
    onQuery: (String) -> Unit, 
    onSearch: () -> Unit, 
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    state: NyaaUiState, 
    favs: List<TorrentItem>, 
    onItem: (TorrentItem) -> Unit, 
    onFav: (TorrentItem) -> Unit,
    onOpenSaved: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = query, 
                onValueChange = onQuery, 
                modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)), 
                placeholder = { Text("Search Movies (YTS)...", color = Color(0xFF908070), fontSize = 14.sp) }, 
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF281A14), unfocusedContainerColor = Color(0xFF281A14), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSearch, 
                modifier = Modifier.size(52.dp).shadow(8.dp, CircleShape), 
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.Black)
            ) { 
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp)) 
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onOpenSaved,
                modifier = Modifier.size(52.dp).background(Color(0xFF281A14), CircleShape).shadow(8.dp, CircleShape)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Saved", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
            }
        }
        
        Spacer(Modifier.height(10.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            val filterScrollState = rememberLazyListState()
            
            LazyRow(
                state = filterScrollState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(onClick = { onSort("seeds") }, label = { Text("Seeds") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                item { FilterChip(onClick = { onSort("rating") }, label = { Text("Top Rated") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                item { FilterChip(onClick = { onSort("download_count") }, label = { Text("Downloads") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
                item { FilterChip(onClick = { onSort("year") }, label = { Text("Latest") }, selected = false, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF281A14), labelColor = Color.White)) }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (state) {
            is NyaaUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4CAF50)) }
            is NyaaUiState.Success -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                        lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
                    }
                }

                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        onLoadMore()
                    }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { (it.magnet ?: "") + (it.title ?: "") }) { item ->
                        PremiumGridCard(
                            item = item, 
                            isFav = favs.any { (it.magnet ?: it.title) == (item.magnet ?: item.title) }, 
                            onFav = { onFav(item) },
                            onClick = { onItem(item) }
                        )
                    }
                    
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            Text("No more results", color = Color(0xFFB0A090), fontSize = 12.sp)
                        }
                    }
                }
            }
            is NyaaUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.message, color = Color(0xFFFF6B6B)) }
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Search for your favorite movies...", color = Color(0xFFB0A090)) }
        }
    }
}

@Composable
fun FavoritesScreen(
    favs: List<TorrentItem>, 
    emptyMessage: String,
    onItem: (TorrentItem) -> Unit, 
    onFav: (TorrentItem) -> Unit,
    onBackToHome: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("MY COLLECTION", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB0A090))
            TextButton(onClick = onBackToHome) {
                Text("← Back to Search", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (favs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { 
                Text(emptyMessage, color = Color(0xFFB0A090), fontSize = 16.sp, fontWeight = FontWeight.Bold) 
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favs, key = { (it.magnet ?: "") + (it.title ?: "") }) { item -> 
                    PremiumGridCard(
                        item = item, 
                        isFav = true, 
                        onFav = { onFav(item) },
                        onClick = { onItem(item) }
                    ) 
                }
            }
        }
    }
}

@Composable
fun PremiumGridCard(
    item: TorrentItem, 
    isFav: Boolean, 
    onFav: () -> Unit, 
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowColor by infiniteTransition.animateColor(
        initialValue = Color(0xFF4CAF50).copy(alpha = 0.15f),
        targetValue = Color(0xFF4CAF50).copy(alpha = 0.45f),
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ), label = "glowColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = glowColor,
                spotColor = glowColor
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF281A14))
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B120E)), Alignment.Center) {
                SubcomposeAsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(Modifier.fillMaxSize().background(Color(0xFF1B120E)), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF4CAF50))
                        }
                    },
                    error = {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.DarkGray)
                    }
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(0.7f)),
                                startY = 100f
                            )
                        )
                )

                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onFav,
                        modifier = Modifier.size(32.dp).background(Color.Black.copy(0.6f), CircleShape)
                    ) { 
                        Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) Color(0xFF4CAF50) else Color.White, modifier = Modifier.size(18.dp)) 
                    }
                }
            }
            
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title ?: "", 
                    maxLines = 2, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 12.sp, 
                    color = Color.White, 
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.time ?: "N/A", 
                        color = Color(0xFFB0A090), 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Star, null, Modifier.size(12.dp), Color(0xFF4CAF50))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.rating ?: (item.seeders?.toString() ?: "0"), 
                        fontSize = 10.sp, 
                        color = Color(0xFF4CAF50), 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DetailDialog(
    item: TorrentItem,
    suggestions: List<TorrentItem>,
    favorites: List<TorrentItem>,
    isLoadingSuggestions: Boolean,
    onSelectSuggestion: (TorrentItem) -> Unit,
    onFavSuggestion: (TorrentItem) -> Unit,
    onDismiss: () -> Unit,
    onMagnet: (String?) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF241611))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF33221A), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(135.dp, 200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B120E))
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = item.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            },
                            error = {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = item.title ?: "Details",
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = item.synopsis ?: "No description available.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFD0C0B0)
                )

                Spacer(Modifier.height(16.dp))

                val options = item.torrentOptions
                if (options.isNotEmpty()) {
                    Text(
                        text = "AVAILABLE QUALITIES & MAGNETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )

                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { opt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF33221A)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFF4CAF50),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = opt.quality,
                                                    color = Color.Black,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 2.dp
                                                    )
                                                )
                                            }
                                            Text(
                                                opt.size,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${opt.seeders} seeds",
                                            fontSize = 10.sp,
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = { onMagnet(opt.magnet); onDismiss() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50),
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "DOWNLOAD",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val hasValidMagnet =
                        !item.magnet.isNullOrBlank() && item.magnet.startsWith("magnet:", ignoreCase = true)
                    val buttonText =
                        if (hasValidMagnet) "DOWNLOAD TORRENT" else "OPEN TORRENT PAGE"

                    Button(
                        onClick = { onMagnet(item.magnet); onDismiss() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF38261E), thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SUGGESTED FOR YOU (TOP 30)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (isLoadingSuggestions) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else if (suggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestions) { suggestedItem ->
                            val isFav = favorites.any { (it.magnet ?: it.title) == (suggestedItem.magnet ?: suggestedItem.title) }
                            SuggestionCard(
                                item = suggestedItem,
                                isFav = isFav,
                                onFav = { onFavSuggestion(suggestedItem) },
                                onClick = { onSelectSuggestion(suggestedItem) }
                            )
                        }
                    }
                } else {
                    Text(
                        "No recommendations available.",
                        fontSize = 11.sp,
                        color = Color(0xFFB0A090)
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(
    item: TorrentItem,
    isFav: Boolean,
    onFav: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(105.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF33221A))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B120E)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    },
                    error = {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )

                Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onFav,
                        modifier = Modifier.size(28.dp).background(Color.Black.copy(0.6f), CircleShape)
                    ) { 
                        Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) Color(0xFF4CAF50) else Color.White, modifier = Modifier.size(14.dp)) 
                    }
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.title ?: "",
                    maxLines = 2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.White,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, Modifier.size(10.dp), Color(0xFF4CAF50))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.rating ?: (item.time ?: "N/A"),
                        fontSize = 9.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
