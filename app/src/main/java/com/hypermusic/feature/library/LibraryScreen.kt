package com.hypermusic.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hypermusic.core.designsystem.theme.LikedSongsGradient
import com.hypermusic.core.designsystem.theme.DownloadsGradient
import com.hypermusic.core.designsystem.theme.PodcastEpisodesGradient
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.designsystem.components.AppFilterChipsRow
import com.hypermusic.core.designsystem.components.CollapsibleHeaderLayout
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.rememberCollapsibleHeaderState
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.model.Track
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.navigation.NavigationEventBus
import com.hypermusic.feature.library.components.LibraryEntityCard
import com.hypermusic.feature.library.components.LibraryGridCard
import com.hypermusic.feature.library.components.LibraryShortcutCard
import com.hypermusic.feature.library.components.LibrarySortBar
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.utils.MediaActionHandler
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

data class LibraryDisplayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val trackType: String? = null,
    val isDeviceLocal: Boolean = false,
    val timestamp: Long = 0L,
    val rawAuthorOrArtist: String? = null,
    val track: Track? = null
)

private val libraryFilters = listOf("Playlists", "Albums", "Artists", "Podcasts", "Downloads", "History", "From Device")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialFilter: String? = null,
    viewModel: LibraryViewModel = koinViewModel(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    playerRepository: PlayerRepository = koinInject(),
    onNavigateToLikedSongs: () -> Unit = {},
    onNavigateToSavedEpisodes: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    downloadRepository: DownloadRepository = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject(),
    toastViewModel: com.hypermusic.feature.shared.state.GlobalToastViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeChip = uiState.activeChip
    val sortMode = uiState.sortMode
    val isGridView = uiState.isGridView
    val downloadedFlow = remember(downloadRepository) { downloadRepository.getAllDownloadedTracks() }
    val downloadedTracks by downloadedFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }
    val queueMap by downloadRepository.downloadQueue.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val currentTrack by playerRepository.currentTrack.collectAsState()
    val isPlaying by playerRepository.isPlaying.collectAsState()

    val filterTrigger by navigationEventBus.libraryFilterTrigger.collectAsState()

    // Apply library filters requested by widgets or launcher shortcuts.
    LaunchedEffect(filterTrigger) {
        filterTrigger?.let { filter ->
            viewModel.setActiveChip(filter)
            navigationEventBus.consumeLibraryFilter()
        }
    }

    LaunchedEffect(initialFilter) {
        if (!initialFilter.isNullOrBlank()) {
            viewModel.setActiveChip(initialFilter)
        }
    }

    BackHandler(enabled = activeChip != null) {
        viewModel.setActiveChip(null)
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermissions by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    var hasRequestedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        hasRequestedPermission = true
    }

    val currentActiveChip by rememberUpdatedState(activeChip)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val current = requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (current != hasPermissions) {
                    hasPermissions = current
                } else if (current && currentActiveChip == "From Device") {
                    viewModel.loadDeviceMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activeChip, hasPermissions) {
        if (activeChip == "From Device" && hasPermissions) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val contentObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    viewModel.loadDeviceMedia(debounceMs = 250L)
                }
            }
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
            onDispose {
                context.contentResolver.unregisterContentObserver(contentObserver)
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(activeChip, hasPermissions) {
        if (activeChip == "From Device" && hasPermissions) {
            viewModel.loadDeviceMedia()
        }
    }

    ScreenWrapper(
        disableSafeAreaTop = true,
        enableAmbientGlow = true
    ) {
        val likedTracks = uiState.likedTracks
        val recentHistory = uiState.recentHistory
        val playlists = uiState.playlists
        val albums = uiState.albums
        val artists = uiState.artists
        val deviceTracks = uiState.deviceTracks
        val podcasts = uiState.podcasts
        val savedEpisodes = uiState.savedEpisodes

        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val gridColumnCount = remember(screenWidthDp) {
            when {
                screenWidthDp >= 1000 -> 6
                screenWidthDp >= 800 -> 5
                screenWidthDp >= 600 -> 4
                screenWidthDp >= 480 -> 3
                else -> 2
            }
        }

        val sortedOverviewItems = remember(playlists, albums, artists, podcasts, sortMode) {
            val items = mutableListOf<LibraryDisplayItem>()
            items.addAll(playlists.map {
                LibraryDisplayItem(
                    id = it.playlistId,
                    title = it.name,
                    subtitle = "Playlist",
                    imageUrl = it.artworkUrl,
                    trackType = "playlist",
                    timestamp = if (sortMode == "Recently added") it.createdAt else it.updatedAt
                )
            })
            items.addAll(albums.map {
                LibraryDisplayItem(
                    id = it.albumId,
                    title = it.title,
                    subtitle = "Album • ${it.artistName}",
                    imageUrl = it.artworkUrl,
                    trackType = "album",
                    rawAuthorOrArtist = it.artistName,
                    timestamp = it.savedAt
                )
            })
            items.addAll(artists.map {
                LibraryDisplayItem(
                    id = it.artistId,
                    title = it.name,
                    subtitle = "Artist",
                    imageUrl = it.avatarUrl,
                    trackType = "artist",
                    rawAuthorOrArtist = it.name,
                    timestamp = it.savedAt
                )
            })
            items.addAll(podcasts.map {
                LibraryDisplayItem(
                    id = it.podcastId,
                    title = it.title,
                    subtitle = "Podcast • ${it.author}",
                    imageUrl = it.artworkUrl,
                    trackType = "podcast_show",
                    rawAuthorOrArtist = it.author,
                    timestamp = it.savedAt
                )
            })

            when (sortMode) {
                "Alphabetical" -> items.sortedBy { it.title.lowercase() }
                else -> items.sortedByDescending { it.timestamp }
            }
        }
        val chunkedOverview = remember(sortedOverviewItems, gridColumnCount) {
            sortedOverviewItems.chunked(gridColumnCount)
        }

        val rawDisplayItems = remember(activeChip, playlists, albums, artists, podcasts, recentHistory, downloadedTracks, deviceTracks, sortMode) {
            when (activeChip) {
                "Playlists" -> playlists.map {
                    LibraryDisplayItem(
                        id = it.playlistId,
                        title = it.name,
                        subtitle = "Playlist",
                        imageUrl = it.artworkUrl,
                        trackType = "playlist",
                        timestamp = if (sortMode == "Recently added") it.createdAt else it.updatedAt
                    )
                }
                "Albums" -> albums.map {
                    LibraryDisplayItem(
                        id = it.albumId,
                        title = it.title,
                        subtitle = "Album • ${it.artistName}",
                        imageUrl = it.artworkUrl,
                        trackType = "album",
                        rawAuthorOrArtist = it.artistName,
                        timestamp = it.savedAt
                    )
                }
                "Artists" -> artists.map {
                    LibraryDisplayItem(
                        id = it.artistId,
                        title = it.name,
                        subtitle = "Artist",
                        imageUrl = it.avatarUrl,
                        trackType = "artist",
                        rawAuthorOrArtist = it.name,
                        timestamp = it.savedAt
                    )
                }
                "Podcasts" -> podcasts.map {
                    LibraryDisplayItem(
                        id = it.podcastId,
                        title = it.title,
                        subtitle = "Podcast • ${it.author}",
                        imageUrl = it.artworkUrl,
                        trackType = "podcast_show",
                        rawAuthorOrArtist = it.author,
                        timestamp = it.savedAt
                    )
                }
                "History" -> recentHistory.map {
                    LibraryDisplayItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.artist,
                        imageUrl = it.artwork,
                        trackType = it.trackType,
                        isDeviceLocal = it.isDeviceLocal,
                        timestamp = it.lastPlayedAt ?: 0L,
                        track = it
                    )
                }
                "Downloads" -> downloadedTracks.map {
                    LibraryDisplayItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.artist,
                        imageUrl = it.artwork,
                        trackType = it.trackType,
                        isDeviceLocal = it.isDeviceLocal,
                        track = it
                    )
                }
                "From Device" -> deviceTracks.map {
                    LibraryDisplayItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.artist,
                        imageUrl = it.artwork,
                        trackType = it.trackType,
                        isDeviceLocal = true,
                        timestamp = it.lastPlayedAt ?: 0L,
                        track = it
                    )
                }
                else -> emptyList()
            }
        }

        val displayItems = remember(rawDisplayItems, sortMode) {
            when (sortMode) {
                "Alphabetical" -> rawDisplayItems.sortedBy { it.title.lowercase() }
                else -> rawDisplayItems.sortedByDescending { it.timestamp }
            }
        }
        val chunkedDisplayItems = remember(displayItems, gridColumnCount) {
            displayItems.chunked(gridColumnCount)
        }

        fun onOverviewClick(item: LibraryDisplayItem) {
            if (item.track != null) {
                val onlyTracks = sortedOverviewItems.mapNotNull { it.track }
                val trackIndex = onlyTracks.indexOf(item.track)
                if (trackIndex >= 0) {
                    playerRepository.playTracks(onlyTracks, trackIndex)
                } else {
                    playerRepository.playTrack(item.track)
                }
            } else {
                val type = if (item.trackType == "podcast_show" || item.trackType == "podcast") "podcast" else item.trackType ?: "playlist"
                val subtitleToPass = item.rawAuthorOrArtist ?: if (type == "artist") null else item.subtitle
                onNavigateToCollection(type, item.id, item.title, subtitleToPass, item.imageUrl)
            }
        }

        fun onOverviewMore(item: LibraryDisplayItem) {
            if (item.track != null) {
                actionSheetViewModel.openSheet(MediaActionHandler.resolveActionContext(item.track))
            } else if (item.trackType == "playlist") {
                actionSheetViewModel.openSheet(ActionContext.PlaylistContext(item.id, item.title, item.imageUrl, true))
            } else if (item.trackType == "album") {
                actionSheetViewModel.openSheet(ActionContext.AlbumContext(item.id, item.title, item.rawAuthorOrArtist ?: "", item.imageUrl, true))
            } else if (item.trackType == "podcast" || item.trackType == "podcast_show") {
                actionSheetViewModel.openSheet(ActionContext.PodcastContext(item.id, item.title, item.rawAuthorOrArtist ?: "", item.imageUrl))
            } else if (item.trackType == "artist") {
                actionSheetViewModel.openSheet(ActionContext.ArtistContext(item.id, item.title, item.imageUrl))
            }
        }

        var deviceTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var deviceTapSeq by remember { mutableStateOf(0L) }

        fun playFilteredTrack(item: LibraryDisplayItem) {
            val tracksPool = displayItems.mapNotNull { it.track }
            val trackIndex = tracksPool.indexOfFirst { it.id == item.id }
            if (trackIndex >= 0) {
                playerRepository.playTracks(tracksPool, trackIndex)
            } else if (item.track != null) {
                playerRepository.playTrack(item.track)
            }
        }

        fun onFilteredItemClick(item: LibraryDisplayItem) {
            when (activeChip) {
                "From Device" -> {
                    val tapToken = ++deviceTapSeq
                    deviceTapJob?.cancel()
                    deviceTapJob = coroutineScope.launch {
                        val readable = viewModel.isDeviceTrackReadable(item.id)
                        if (tapToken != deviceTapSeq) return@launch
                        if (!readable) {
                            toastViewModel.showToast(
                                "This device file is unavailable",
                                com.hypermusic.feature.shared.state.ToastType.ERROR
                            )
                            viewModel.loadDeviceMedia()
                            return@launch
                        }
                        playFilteredTrack(item)
                    }
                }
                "History", "Downloads" -> {
                    ++deviceTapSeq
                    deviceTapJob?.cancel()
                    playFilteredTrack(item)
                }
                else -> {
                    val type = if (activeChip == "Artists") "artist" else if (activeChip == "Albums") "album" else if (activeChip == "Podcasts") "podcast" else "playlist"
                    val subtitleToPass = if (type == "artist") null else item.rawAuthorOrArtist ?: item.subtitle
                    onNavigateToCollection(type, item.id, item.title, subtitleToPass, item.imageUrl)
                }
            }
        }

        fun onFilteredItemMore(item: LibraryDisplayItem) {
            when (activeChip) {
                "History" -> {
                    val track = item.track ?: recentHistory.find { it.id == item.id }
                    if (track != null) actionSheetViewModel.openSheet(ActionContext.HistoryTrackContext(track))
                }
                "Downloads" -> {
                    val track = item.track ?: downloadedTracks.find { it.id == item.id }
                    if (track != null) actionSheetViewModel.openSheet(ActionContext.DownloadTrackContext(track))
                }
                "From Device" -> {
                    val track = item.track ?: deviceTracks.find { it.id == item.id }
                    if (track != null) actionSheetViewModel.openSheet(ActionContext.DeviceLocalTrackContext(track))
                }
                "Playlists" -> {
                    actionSheetViewModel.openSheet(ActionContext.PlaylistContext(item.id, item.title, item.imageUrl, true))
                }
                "Albums" -> {
                    val album = albums.find { it.albumId == item.id }
                    actionSheetViewModel.openSheet(ActionContext.AlbumContext(item.id, item.title, album?.artistName ?: item.rawAuthorOrArtist ?: "", item.imageUrl, true))
                }
                "Artists" -> {
                    actionSheetViewModel.openSheet(ActionContext.ArtistContext(item.id, item.title, item.imageUrl))
                }
                "Podcasts" -> {
                    val podcast = podcasts.find { it.podcastId == item.id }
                    actionSheetViewModel.openSheet(ActionContext.PodcastContext(item.id, item.title, podcast?.author ?: item.rawAuthorOrArtist ?: "", item.imageUrl))
                }
            }
        }

        val headerState = rememberCollapsibleHeaderState()

        // Expand the header on filter changes so filter controls remain accessible.
        LaunchedEffect(activeChip) {
            headerState.snapToExpanded()
            listState.scrollToItem(0)
        }

        val density = LocalDensity.current
        val scrollThresholdPx = with(density) { 56.dp.toPx() }
        val scrollAlpha = remember(listState, scrollThresholdPx) {
            {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / scrollThresholdPx).coerceIn(0f, 1f)
                }
            }
        }

        CollapsibleHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            state = headerState,
            scrollAlpha = scrollAlpha,
            canScrollProvider = {
                listState.canScrollForward || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            },
            header = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (activeChip == "History") viewModel.setActiveChip(null)
                                else viewModel.setActiveChip("History")
                            }) {
                                Icon(
                                    imageVector = AppIcons.History,
                                    contentDescription = "History",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (activeChip == "History") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = onNavigateToDownloads) {
                                Icon(
                                    imageVector = AppIcons.ArrowDownCircle,
                                    contentDescription = "Downloads",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    AppFilterChipsRow(
                        chips = libraryFilters,
                        activeChip = activeChip,
                        onChipSelect = { viewModel.setActiveChip(it) },
                        titleProvider = { it }
                    )

                    LibrarySortBar(
                        currentSort = sortMode,
                        onSortSelected = { viewModel.setSortMode(it) },
                        isGridView = isGridView,
                        onToggleViewMode = { viewModel.toggleViewMode() }
                    )
                }
            }
        ) { contentPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 4.dp,
                    bottom = LocalBottomContentPadding.current
                )
            ) {
                if (activeChip == null) {
                    item(key = "overview_shortcuts") {
                        val configuration = LocalConfiguration.current
                        if (configuration.screenWidthDp > 600) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LibraryShortcutCard(
                                    title = "Liked Songs",
                                    subtitle = "${likedTracks.size} songs",
                                    icon = { Icon(AppIcons.Heart, contentDescription = null, tint = Color.White) },
                                    gradientColors = LikedSongsGradient,
                                    onClick = onNavigateToLikedSongs,
                                    modifier = Modifier.weight(1f)
                                )
                                LibraryShortcutCard(
                                    title = "Downloaded",
                                    subtitle = "${downloadedTracks.size} songs offline",
                                    icon = { Icon(AppIcons.ArrowDownToLine, contentDescription = null, tint = Color.White) },
                                    gradientColors = DownloadsGradient,
                                    onClick = { viewModel.setActiveChip("Downloads") },
                                    modifier = Modifier.weight(1f)
                                )
                                LibraryShortcutCard(
                                    title = "Episodes for later",
                                    subtitle = "${savedEpisodes.size} ${if (savedEpisodes.size == 1) "episode" else "episodes"}",
                                    icon = { Icon(AppIcons.BookmarkFilled, contentDescription = null, tint = Color.White) },
                                    gradientColors = PodcastEpisodesGradient,
                                    onClick = onNavigateToSavedEpisodes,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                LibraryShortcutCard(
                                    title = "Liked Songs",
                                    subtitle = "${likedTracks.size} songs",
                                    icon = { Icon(AppIcons.Heart, contentDescription = null, tint = Color.White) },
                                    gradientColors = LikedSongsGradient,
                                    onClick = onNavigateToLikedSongs
                                )
                                LibraryShortcutCard(
                                    title = "Downloaded",
                                    subtitle = "${downloadedTracks.size} songs offline",
                                    icon = { Icon(AppIcons.ArrowDownToLine, contentDescription = null, tint = Color.White) },
                                    gradientColors = DownloadsGradient,
                                    onClick = { viewModel.setActiveChip("Downloads") }
                                )
                                LibraryShortcutCard(
                                    title = "Episodes for later",
                                    subtitle = "${savedEpisodes.size} ${if (savedEpisodes.size == 1) "episode" else "episodes"}",
                                    icon = { Icon(AppIcons.BookmarkFilled, contentDescription = null, tint = Color.White) },
                                    gradientColors = PodcastEpisodesGradient,
                                    onClick = onNavigateToSavedEpisodes
                                )
                            }
                        }
                    }

                    if (sortedOverviewItems.isNotEmpty()) {
                        item(key = "overview_header") {
                            val headerTitle = when (sortMode) {
                                "Alphabetical" -> "Library (A-Z)"
                                "Recently added" -> "Recently added"
                                else -> "Recent activity"
                            }
                            Text(
                                text = headerTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (isGridView) {
                            items(chunkedOverview.size, key = { "ov_grid_row_${chunkedOverview[it].first().id}" }) { rowIndex ->
                                val rowItems = chunkedOverview[rowIndex]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            LibraryGridCard(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                imageUrl = item.imageUrl,
                                                isCircular = item.trackType == "artist",
                                                trackType = item.trackType,
                                                isDeviceLocal = item.isDeviceLocal,
                                                isCurrentTrack = item.id == currentTrack?.id,
                                                isPlaying = isPlaying,
                                                onClick = { onOverviewClick(item) },
                                                downloadProgress = if (item.track != null) queueMap[item.id]?.progress?.div(100f) else null,
                                                downloadStatus = if (item.track != null) queueMap[item.id]?.status else null,
                                                onLongClick = { onOverviewMore(item) }
                                            )
                                        }
                                    }
                                    repeat(gridColumnCount - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(sortedOverviewItems.size, key = { sortedOverviewItems[it].id }) { index ->
                                val item = sortedOverviewItems[index]
                                LibraryEntityCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    imageUrl = item.imageUrl,
                                    isCircular = item.trackType == "artist",
                                    trackType = item.trackType,
                                    isDeviceLocal = item.isDeviceLocal,
                                    onClick = { onOverviewClick(item) },
                                    onMoreClick = { onOverviewMore(item) },
                                    isCurrentTrack = item.id == currentTrack?.id,
                                    isPlaying = isPlaying,
                                    trackId = item.id,
                                    downloadProgress = if (item.track != null) queueMap[item.id]?.progress?.div(100f) else null,
                                    downloadStatus = if (item.track != null) queueMap[item.id]?.status else null
                                )
                            }
                        }
                    }
                } else {
                    if (activeChip == "From Device" && !hasPermissions) {
                        item(key = "permission_required") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Permission Required",
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "We need access to your device storage to find and play your local audio tracks.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val activity = context as? android.app.Activity
                                    val shouldShowRationale = requiredPermissions.any {
                                        activity?.shouldShowRequestPermissionRationale(it) == true
                                    }
                                    val isPermanentlyDenied = hasRequestedPermission && !shouldShowRationale

                                    Button(
                                        onClick = {
                                            if (isPermanentlyDenied) {
                                                val intent = android.content.Intent(
                                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    android.net.Uri.fromParts("package", context.packageName, null)
                                                )
                                                context.startActivity(intent)
                                            } else {
                                                hasRequestedPermission = true
                                                permissionLauncher.launch(requiredPermissions)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text(
                                            text = if (isPermanentlyDenied) "Open Settings" else "Grant Permission",
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    } else if (displayItems.isEmpty() && (activeChip != "Podcasts" || savedEpisodes.isEmpty())) {
                        item(key = "empty_state") {
                            val chipLabel = activeChip?.lowercase() ?: "items"
                            EmptyStateView(
                                icon = when (activeChip) {
                                    "Downloads" -> AppIcons.ArrowDown
                                    "Favorites" -> AppIcons.Heart
                                    "Playlists" -> AppIcons.ListMusic
                                    "Albums" -> AppIcons.Disc3
                                    "Artists" -> AppIcons.User
                                    "Podcasts" -> AppIcons.Podcast
                                    else -> AppIcons.Library
                                },
                                title = "No $chipLabel yet",
                                subtitle = "Your saved and downloaded $chipLabel will show up here.",
                                modifier = Modifier.padding(top = 32.dp)
                            )
                        }
                    } else {
                        if (activeChip == "Podcasts") {
                            if (savedEpisodes.isNotEmpty()) {
                                item(key = "episodes_for_later_pinned_card") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        LibraryShortcutCard(
                                            title = "Episodes for later",
                                            subtitle = "${savedEpisodes.size} saved ${if (savedEpisodes.size == 1) "episode" else "episodes"}",
                                            icon = { Icon(AppIcons.BookmarkFilled, contentDescription = null, tint = Color.White) },
                                            gradientColors = PodcastEpisodesGradient,
                                            onClick = onNavigateToSavedEpisodes
                                        )
                                    }
                                }
                            }
                            if (displayItems.isNotEmpty()) {
                                item(key = "followed_shows_header") {
                                    Text(
                                        text = "Followed Shows (${displayItems.size})",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                                    )
                                }
                            } else {
                                item(key = "no_shows_empty") {
                                    EmptyStateView(
                                        icon = AppIcons.Podcast,
                                        title = "No followed shows yet",
                                        subtitle = "Shows you follow will appear here.",
                                        modifier = Modifier.padding(top = 24.dp)
                                    )
                                }
                            }
                        }

                        if (isGridView && (activeChip != "Podcasts" || displayItems.isNotEmpty())) {
                            items(chunkedDisplayItems.size, key = { "grid_row_${chunkedDisplayItems[it].first().id}" }) { rowIndex ->
                                val rowItems = chunkedDisplayItems[rowIndex]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            LibraryGridCard(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                imageUrl = item.imageUrl,
                                                isCircular = activeChip == "Artists" || item.trackType == "artist",
                                                trackType = item.trackType,
                                                isDeviceLocal = item.isDeviceLocal,
                                                isCurrentTrack = item.id == currentTrack?.id,
                                                isPlaying = isPlaying,
                                                onClick = { onFilteredItemClick(item) },
                                                downloadProgress = if (activeChip == "History" || activeChip == "Downloads") queueMap[item.id]?.progress?.div(100f) else null,
                                                downloadStatus = if (activeChip == "History" || activeChip == "Downloads") queueMap[item.id]?.status else null,
                                                onLongClick = { onFilteredItemMore(item) }
                                            )
                                        }
                                    }
                                    repeat(gridColumnCount - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else if (!isGridView && (activeChip != "Podcasts" || displayItems.isNotEmpty())) {
                            items(displayItems.size, key = { displayItems[it].id }) { index ->
                                val item = displayItems[index]
                                LibraryEntityCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    imageUrl = item.imageUrl,
                                    isCircular = activeChip == "Artists" || item.trackType == "artist",
                                    trackType = item.trackType,
                                    isDeviceLocal = item.isDeviceLocal,
                                    onClick = { onFilteredItemClick(item) },
                                    onMoreClick = { onFilteredItemMore(item) },
                                    isCurrentTrack = item.id == currentTrack?.id,
                                    isPlaying = isPlaying,
                                    trackId = if (activeChip == "History" || activeChip == "Downloads") item.id else null,
                                    downloadProgress = if (activeChip == "History" || activeChip == "Downloads") queueMap[item.id]?.progress?.div(100f) else null,
                                    downloadStatus = if (activeChip == "History" || activeChip == "Downloads") queueMap[item.id]?.status else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
