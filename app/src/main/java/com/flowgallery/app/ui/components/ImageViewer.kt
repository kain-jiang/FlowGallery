package com.flowgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onToggleFavorite: (Long) -> Unit
) {
    if (images.isEmpty()) return
    val image = images[currentIndex]
    val isVideo = image.type.isVideo

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
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
    val duplicates = remember(image.id) { image.duplicates }
    // Horizontal swipe accumulator for cross-media navigation.
    var swipeAccum by remember(currentIndex) { mutableFloatStateOf(0f) }

    /** Navigate by a relative delta; prefers the delta callback so boundary
     *  crossings can jump to adjacent subfolders. */
    fun navigateBy(delta: Int) {
        val cb = onNavigateDelta
        if (cb != null) cb(delta) else onNavigate(currentIndex + delta)
    }

    // Reset transform when switching images
    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        swipeAccum = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(currentIndex) {
                // Tap toggles the chrome for both images and videos.
                detectTapGestures { chromeVisible = !chromeVisible }
            }
            .pointerInput(currentIndex) {
                if (isVideo) {
                    // Videos: horizontal swipe navigates (no zoom).
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        swipeAccum += dragAmount
                        if (kotlin.math.abs(swipeAccum) > 90f) {
                            navigateBy(if (swipeAccum < 0) 1 else -1)
                            swipeAccum = 0f
                        }
                    }
                } else {
                    // Images: pinch-zoom/pan, and swipe to navigate at 1x.
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        if (newScale <= 1f) {
                            // At 1x: horizontal drags navigate instead of pan.
                            swipeAccum += pan.x
                            if (kotlin.math.abs(swipeAccum) > 90f) {
                                navigateBy(if (swipeAccum < 0) 1 else -1)
                                swipeAccum = 0f
                            }
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = newScale
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                }
            }
    ) {
        // Main media: video player / animated image / static image
        when {
            image.type.isVideo -> VideoPlayerView(
                uriString = image.uriString,
                chromeVisible = chromeVisible
            )
            else -> SubcomposeAsyncImage(
                model = image.uriString,
                contentDescription = image.name,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val d = state.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        resolvedWidth = d.intrinsicWidth
                        resolvedHeight = d.intrinsicHeight
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

        // Prev / Next arrows — shown when navigation is possible in that
        // direction (currentIndex > 0 / < last). At the subfolder boundary
        // the delta callback crosses into the adjacent subfolder.
        if (chromeVisible && currentIndex > 0) {
            IconButton(
                onClick = { navigateBy(-1) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(12.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev), tint = Color.White)
            }
        }
        if (chromeVisible && currentIndex < images.lastIndex) {
            IconButton(
                onClick = { navigateBy(1) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next), tint = Color.White)
            }
        }

        // Top bar — back button on the left, action group on the right
        if (chromeVisible) {
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
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(
                        onClick = { onToggleFavorite(image.id) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (image.id in favoriteIds) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (image.id in favoriteIds) Color(0xFFEF4444) else Color.White
                        )
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share), tint = Color.White)
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = Color.White)
                    }
                }
            }
        }

        // Bottom area — filename row + thumbnail strip for BOTH images and
        // videos (consistent browsing experience). The video control bar
        // lives inside the frame, padded above this area.
        if (chromeVisible) {
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
                        text = image.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.dimensions, resolvedWidth, resolvedHeight),
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
                                model = img.uriString,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (idx == currentIndex) {
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
    chromeVisible: Boolean
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
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // First-frame cover while paused (renders a real preview image).
        if (!isPlaying) {
            SubcomposeAsyncImage(
                model = uriString,
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
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Control bar: play/pause + progress + time — hidden with the chrome.
        // Padded up so it never overlaps the thumbnail strip below.
        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = Color.White
                    )
                }
                Text(
                    text = formatTime(position),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { exoPlayer.seekTo(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
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
