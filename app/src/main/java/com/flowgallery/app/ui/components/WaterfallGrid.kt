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
import com.flowgallery.app.R
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
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
    contentPadding: PaddingValues = PaddingValues(12.dp)
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnCount),
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
            model = image.uriString,
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
