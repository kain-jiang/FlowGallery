package com.flowgallery.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.flowgallery.app.R
import com.flowgallery.app.data.SmartVideoFrameDecoder
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.data.model.SortMode
import com.flowgallery.app.ui.theme.Success
import com.flowgallery.app.ui.theme.Warning

/**
 * Pinterest-style waterfall (staggered) grid. Items keep their natural aspect
 * ratio, producing the classic masonry look of the design prototype.
 * Supports 2/3 column density (FR-1 decision #3) and media-type badges (FR-10).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaterfallGrid(
    images: List<ImageItem>,
    favoriteIds: Set<Long>,
    onImageClick: (ImageItem) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columnCount: Int = 2,
    sortMode: SortMode = SortMode.DEFAULT,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    /** Reports scroll direction: true = scrolling down (browsing deeper), false = up. */
    onScrollDirection: ((Boolean) -> Unit)? = null
) {
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    var lastIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Detect direction via firstVisibleItemIndex only — the scroll offset
    // jumps when the staggered grid crosses items, which made the old
    // position-based math misreport upward scrolls as downward.
    androidx.compose.runtime.LaunchedEffect(gridState) {
        androidx.compose.runtime.snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index ->
                if (index != lastIndex) {
                    // Reaching the top always restores the chrome.
                    onScrollDirection?.invoke(if (index == 0) false else index > lastIndex)
                    lastIndex = index
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnCount),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding
    ) {
        items(
            count = images.size,
            key = { images[it].id }
        ) { index ->
            val img = images[index]
            WaterfallCard(
                image = img,
                isFavorite = img.id in favoriteIds,
                sortMode = sortMode,
                onClick = { onImageClick(img) },
                onToggleFavorite = { onToggleFavorite(img.id) }
            )
        }
    }
}

@Composable
private fun WaterfallCard(
    image: ImageItem,
    isFavorite: Boolean,
    sortMode: SortMode,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    // Real dimensions are unknown at scan time (zero-IO scan); they are
    // resolved in the background by the ViewModel. Keep the ratio in sync
    // and also fall back to resolving from the loaded drawable.
    var resolvedRatio by remember(image.uriString, image.width, image.height) {
        mutableStateOf(if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        val ratio = resolvedRatio.coerceIn(0.4f, 2.5f)
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(image.uriString)
                .apply {
                    if (image.type.isVideo) {
                        setParameter(SmartVideoFrameDecoder.KEY_VIDEO_URI, image.uriString)
                    }
                }
                .build(),
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val d = state.result.drawable
                val w = d.intrinsicWidth
                val h = d.intrinsicHeight
                if (w > 0 && h > 0) {
                    resolvedRatio = w.toFloat() / h
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // Quality badge (HD / SD) — only for images; videos show type badge only
        if (!image.type.isVideo) {
            Text(
                text = if (image.isHd) "HD" else "SD",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (image.isHd) Success.copy(alpha = 0.85f) else Warning.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        // Sort-value display (bottom-right): shows the value the grid is
        // currently sorted by — time or size. Quality mode reuses the badge.
        val sortValue = when (sortMode) {
            SortMode.LATEST, SortMode.OLDEST ->
                if (image.modifiedTime > 0) formatTimeShort(image.modifiedTime) else null
            SortMode.LARGEST, SortMode.SMALLEST ->
                if (image.sizeBytes > 0) formatSize(image.sizeBytes) else null
            else -> null
        }
        if (sortValue != null) {
            Text(
                text = sortValue,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        // Media type badge (GIF / WEBP / video play icon) — top-left, FR-10
        if (image.type != MediaType.STATIC_IMAGE) {
            TypeBadge(image.type, Modifier.align(Alignment.TopStart))
        }

        // Favorite toggle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite
                ),
                tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TypeBadge(type: MediaType, modifier: Modifier = Modifier) {
    val label = when (type) {
        MediaType.ANIMATED_GIF -> "GIF"
        MediaType.ANIMATED_WEBP -> "WEBP"
        MediaType.VIDEO -> "VIDEO"
        MediaType.STATIC_IMAGE -> return
    }
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        if (type == MediaType.VIDEO) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Text(
                text = label,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Short file-time label, e.g. "08-14" or "2025-08-14". */
private fun formatTimeShort(ms: Long): String {
    return try {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val now = java.util.Calendar.getInstance()
        val sameYear = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
        val fmt = java.text.SimpleDateFormat(
            if (sameYear) "MM-dd" else "yyyy-MM-dd",
            java.util.Locale.getDefault()
        )
        fmt.format(java.util.Date(ms))
    } catch (e: Exception) {
        ""
    }
}

/** Human-readable file size, e.g. "2.3 MB". */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}
