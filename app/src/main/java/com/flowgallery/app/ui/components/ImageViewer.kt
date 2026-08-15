package com.flowgallery.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.flowgallery.app.R
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.ui.theme.Surface2
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Full-screen viewer: static image zoom/pan, animated GIF/WebP autoplay,
 * video playback via Media3 (FR-3 / FR-10), swipe navigation, thumbnail strip.
 */
@Composable
fun ImageViewer(
    images: List<ImageItem>,
    currentIndex: Int,
    favoriteIds: Set<Long>,
    onNavigate: (Int) -> Unit,
    onNavigateDelta: ((Int) -> Unit)? = null,
    onClose: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onShare: ((ImageItem) -> Unit)? = null,
    onSaveToGallery: ((ImageItem) -> Unit)? = null
) {
    if (images.isEmpty()) return
    val image = images[currentIndex]
    val isVideo = image.type.isVideo

    var chromeVisible by remember { mutableStateOf(true) }
    // Real dimensions resolved lazily from the loaded drawable (zero-IO scan).
    var resolvedWidth by remember(image.uriString) { mutableStateOf(image.width) }
    var resolvedHeight by remember(image.uriString) { mutableStateOf(image.height) }
    // Thumbnail strip scroll state — auto-centers the current item.
    val thumbListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(currentIndex) {
        val target = (currentIndex - 2).coerceAtLeast(0)
        thumbListState.animateScrollToItem(target)
    }
    // Duplicate files dialog state (content-dedup copies of this item).
    var showDuplicates by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val duplicates = remember(image.id) { image.duplicates }
    // Landscape pure-play fullscreen for videos (no browsing chrome).
    // rememberSaveable so a config change (rotation) preserves the state.
    var videoFullscreen by rememberSaveable(currentIndex) { mutableStateOf(false) }

    // HorizontalPager: items are laid out side by side; dragging moves them
    // together (ViewPager feel), vertical drags never navigate.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentIndex
    ) { images.size }

    // External navigation (arrows / thumbnails / subfolder crossing) syncs
    // the pager to the new current index.
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    // Pager page changes drive the external viewer index.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != currentIndex) onNavigate(page)
            }
    }

    // Subfolder-boundary crossing: at the first/last page, dragging further
    // past the threshold jumps into the adjacent subfolder (delta callback).
    LaunchedEffect(pagerState, images.size) {
        androidx.compose.runtime.snapshotFlow {
            pagerState.currentPage to pagerState.currentPageOffsetFraction
        }.collect { (page, fraction) ->
            val cb = onNavigateDelta
            if (cb == null) return@collect
            val last = images.lastIndex
            if (page == 0 && fraction < -0.5f) {
                cb(-1)
            } else if (page == last && fraction > 0.5f) {
                cb(1)
            }
        }
    }

    // Fullscreen: rotate the activity to landscape while in this mode.
    if (isVideo && videoFullscreen) {
        val activity = androidx.compose.ui.platform.LocalContext.current
            as? android.app.Activity
        DisposableEffect(Unit) {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            onDispose {
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        // Back exits fullscreen instead of closing the whole viewer.
        BackHandler { videoFullscreen = false }
    }

    /** Navigate by a relative delta; prefers the delta callback so boundary
     *  crossings can jump to adjacent subfolders. */
    fun navigateBy(delta: Int) {
        val cb = onNavigateDelta
        if (cb != null) cb(delta) else onNavigate(currentIndex + delta)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(currentIndex, videoFullscreen) {
                // Tap toggles the chrome (control bar) — including in video
                // fullscreen so the progress bar can be hidden/shown. Zoom &
                // double-tap live inside each page (ZoomableImage).
                detectTapGestures(
                    onTap = { chromeVisible = !chromeVisible }
                )
            }
    ) {
        // Main media — HorizontalPager: items sit side by side, dragging
        // moves them together (standard ViewPager feel), vertical drags
        // never trigger navigation. Each page owns its zoom/double-tap.
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            key = { images[it].id },
            beyondViewportPageCount = 1,
            // Video fullscreen = pure play: no page swiping.
            userScrollEnabled = !videoFullscreen,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = images[page]
            if (item.type.isVideo) {
                VideoPlayerView(
                    uriString = item.uriString,
                    chromeVisible = chromeVisible && page == pagerState.currentPage,
                    fullscreen = videoFullscreen,
                    isCurrentPage = page == pagerState.currentPage,
                    onToggleFullscreen = {
                        if (videoFullscreen) {
                            // exiting fullscreen: make sure browsing chrome is back
                            chromeVisible = true
                        }
                        videoFullscreen = !videoFullscreen
                    }
                )
            } else {
                ZoomableImage(
                    item = item,
                    onTap = { chromeVisible = !chromeVisible }
                )
            }
        }

        // Prev / Next arrows — shown when navigation is possible in that
        // direction (currentIndex > 0 / < last). At the subfolder boundary
        // the delta callback crosses into the adjacent subfolder. Hidden in
        // video fullscreen (pure play).
        if (chromeVisible && !videoFullscreen && currentIndex > 0) {
            IconButton(
                onClick = { navigateBy(-1) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_prev),
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            clip = true,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                )
            }
        }
        if (chromeVisible && !videoFullscreen && currentIndex < images.lastIndex) {
            IconButton(
                onClick = { navigateBy(1) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_next),
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            clip = true,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                )
            }
        }

        // Top bar — back button on the left, action group on the right.
        // Hidden in video fullscreen (pure play; back = exit fullscreen via
        // the player's own control bar).
        if (chromeVisible && !videoFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(
                                elevation = 9.dp,
                                shape = CircleShape,
                                clip = true,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onToggleFavorite(image.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (image.id in favoriteIds) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (image.id in favoriteIds) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .shadow(
                                    elevation = 9.dp,
                                    shape = CircleShape,
                                    clip = true,
                                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                        )
                    }
                    IconButton(
                        onClick = { onShare?.invoke(image) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.cd_share),
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .shadow(
                                    elevation = 9.dp,
                                    shape = CircleShape,
                                    clip = true,
                                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.cd_more),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .shadow(
                                        elevation = 9.dp,
                                        shape = CircleShape,
                                        clip = true,
                                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    )
                            )
                        }
                        // More menu: save to gallery
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_to_gallery)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onSaveToGallery?.invoke(image)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom area — filename row + thumbnail strip for BOTH images and
        // videos (consistent browsing experience). The video control bar
        // lives inside the frame, padded above this area. Hidden in video
        // fullscreen. Bound to the pager's current page so it updates
        // instantly while swiping (no reliance on the external index sync).
        val pagerCurrent = pagerState.currentPage.coerceIn(0, images.lastIndex)
        val currentItem = images[pagerCurrent]
        val curResolvedW = remember(currentItem.uriString) { mutableIntStateOf(currentItem.width) }
        val curResolvedH = remember(currentItem.uriString) { mutableIntStateOf(currentItem.height) }
        if (chromeVisible && !videoFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(top = 32.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentItem.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.dimensions, curResolvedW.intValue, curResolvedH.intValue),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                // Duplicate copies indicator — shows every file path/name/hash
                // of the same media when content-dedup found copies elsewhere.
                if (duplicates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { showDuplicates = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.duplicate_files, duplicates.size + 1),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = thumbListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(images) { idx, img ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Surface2)
                                .clickable { onNavigate(idx) }
                        ) {
                            SubcomposeAsyncImage(
                                model = coil.request.ImageRequest.Builder(
                                    androidx.compose.ui.platform.LocalContext.current
                                )
                                    .data(img.uriString)
                                    .apply {
                                        if (img.type.isVideo) {
                                            setParameter(
                                                com.flowgallery.app.data.SmartVideoFrameDecoder.KEY_VIDEO_URI,
                                                img.uriString
                                            )
                                        }
                                    }
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (idx == pagerCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.25f))
                                )
                            }
                            if (img.type.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    } // LazyRow
            }
        }
    }

    // Duplicate files dialog: lists every copy's folder path, file name and
    // content hash, so users can see where else the same media lives.
    if (showDuplicates && duplicates.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDuplicates = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.duplicate_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                // Current file first
                DuplicateRow(item = image, isCurrent = true)
                duplicates.forEach { dup ->
                    Spacer(Modifier.height(8.dp))
                    DuplicateRow(item = dup, isCurrent = false)
                }

                Spacer(Modifier.height(20.dp))
                // Close action — full-width tinted button, matching the
                // "Add New Folder" button style from the folder modal.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showDuplicates = false }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.duplicate_close),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateRow(item: ImageItem, isCurrent: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) scheme.primaryContainer else scheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File icon in a tinted container (matches folder modal icon style)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrent) scheme.primary else scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = if (isCurrent) Color.White else scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${item.folderName}${item.subFolderName?.let { " / $it" } ?: ""}",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            item.contentHash?.let { hash ->
                Text(
                    text = "MD5: $hash",
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.duplicate_current),
                    color = scheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Custom video player — Media3 surface + our own controls (FR-10). */
@Composable
private fun VideoPlayerView(
    uriString: String,
    chromeVisible: Boolean,
    fullscreen: Boolean = false,
    isCurrentPage: Boolean = true,
    onToggleFullscreen: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uriString))
            prepare()
            playWhenReady = false
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    /** non-null while the user is dragging the seek bar (immediate feedback) */
    var dragValue by remember { mutableStateOf<Float?>(null) }
    /** muted state (tap the volume icon to toggle) */
    var muted by remember { mutableStateOf(false) }

    // Reset playback state whenever the video changes (swipe navigation).
    // Without this, the previous video's isPlaying/duration linger and the
    // controls misbehave (no play button, dead pause, stale slider range).
    androidx.compose.runtime.LaunchedEffect(uriString) {
        isPlaying = false
        position = 0L
        duration = 0L
        dragValue = null
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause when this page is no longer the current one (pager keeps
    // adjacent pages composed — they must not keep playing).
    androidx.compose.runtime.LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && isPlaying) {
            exoPlayer.pause()
        }
    }

    // Poll position while playing so the progress bar stays live.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = exoPlayer.currentPosition
            kotlinx.coroutines.delay(250)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video surface (no native controller — we draw our own).
        // IMPORTANT: PlayerView must NOT consume touches, otherwise the
        // Compose swipe/tap gestures above never fire. SurfaceView renders
        // independently, so playback is unaffected by touch passthrough.
        // The factory runs once; `update` re-binds the player whenever the
        // uri changes (swipe to another video) — without it the view keeps
        // showing the old, already-released player (frozen frame, dead
        // controls).
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // First-frame cover while paused (renders a real preview image).
        if (!isPlaying) {
            SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(uriString)
                    .setParameter(
                        com.flowgallery.app.data.SmartVideoFrameDecoder.KEY_VIDEO_URI,
                        uriString
                    )
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Center play button when paused (independent of chrome).
        if (!isPlaying) {
            IconButton(
                onClick = { exoPlayer.play() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            clip = true,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                )
            }
        }

        // Control bar: play/pause + progress + time — hidden with the chrome.
        // No background scrim — clean overlay on the video.
        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (fullscreen) 8.dp else 150.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(
                                elevation = 9.dp,
                                shape = CircleShape,
                                clip = true,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatTime(position),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
                Slider(
                    // While dragging, show the finger position immediately
                    // (dragValue); commit the seek on release. Otherwise the
                    // thumb lags behind because position only updates every
                    // 250ms poll.
                    value = (dragValue ?: position.toFloat())
                        .coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        val target = dragValue
                        if (target != null) {
                            exoPlayer.seekTo(target.toLong())
                            // Sync the displayed position immediately so the
                            // thumb doesn't flash back to the old spot before
                            // the next poll updates it.
                            position = target.toLong()
                        }
                        dragValue = null
                    },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
                // Volume toggle (mute / unmute)
                IconButton(
                    onClick = {
                        muted = !muted
                        exoPlayer.volume = if (muted) 0f else 1f
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = stringResource(
                            if (muted) R.string.cd_unmute else R.string.cd_mute
                        ),
                        tint = if (muted) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(
                                elevation = 9.dp,
                                shape = CircleShape,
                                clip = true,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                    )
                }
                // Fullscreen toggle (rotate into landscape pure-play view)
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.cd_fullscreen),
                        tint = if (fullscreen) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(
                                elevation = 9.dp,
                                shape = CircleShape,
                                clip = true,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * One image page inside the pager: pinch-zoom (1x–4x), pan, and double-tap
 * to zoom 2x / restore 1x. Tap is forwarded for chrome toggling.
 */
@Composable
private fun ZoomableImage(
    item: ImageItem,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var resolvedW by remember(item.uriString) { mutableIntStateOf(item.width) }
    var resolvedH by remember(item.uriString) { mutableIntStateOf(item.height) }
    val scope = rememberCoroutineScope()

    fun animateTo(targetScale: Float) {
        scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = scale,
                targetValue = targetScale,
                animationSpec = androidx.compose.animation.core.tween(220)
            ) { value, _ ->
                scale = value
                if (targetScale <= 1f) {
                    offsetX = 0f
                    offsetY = 0f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onDoubleTap = {
                        animateTo(if (scale <= 1f) 2f else 1f)
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(item.id) {
                // Custom transform: pinch-zoom + pan, but a single-finger
                // horizontal drag at 1x is NOT consumed so the pager can
                // swipe between items (detectTransformGestures would eat it).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }
                        if (pressed) {
                            val multi = event.changes.size > 1
                            if (multi) {
                                // Two fingers: pinch zoom + pan.
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                if (newScale <= 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = newScale
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1f) {
                                // Single finger while zoomed: pan the image.
                                val pan = event.calculatePan()
                                offsetX += pan.x
                                offsetY += pan.y
                                event.changes.forEach { it.consume() }
                            }
                            // scale == 1f single finger: leave unconsumed so
                            // the pager handles horizontal swiping.
                        }
                    } while (pressed)
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = item.uriString,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val d = state.result.drawable
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    resolvedW = d.intrinsicWidth
                    resolvedH = d.intrinsicHeight
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}
