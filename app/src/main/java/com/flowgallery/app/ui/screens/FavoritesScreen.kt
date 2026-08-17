package com.flowgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgallery.app.R
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.viewmodel.GalleryViewModel

/** Favorites page — standalone tab replacing the former Search tab. */
@Composable
fun FavoritesScreen(
    viewModel: GalleryViewModel,
    onImageClick: (ImageItem) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    // Favorites from ENABLED folders only — disabled folders' images are
    // hidden from the whole app (toggle in settings, no rescan).
    val enabledIds = state.folders.filter { it.isSelected }.map { it.id }.toSet()
    val favImages = state.images.filter { it.uriString in favorites && it.folderId in enabledIds }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_favorites),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Favorites' own single-column toggle (independent of Home)
            IconButton(onClick = viewModel::toggleFavoritesSingleColumn) {
                Icon(
                    Icons.Filled.ViewAgenda,
                    contentDescription = stringResource(R.string.cd_grid_toggle),
                    tint = if (state.favoritesSingleColumn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (favImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.favorites_empty_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            WaterfallGrid(
                images = favImages,
                favoriteIds = favorites,
                onImageClick = onImageClick,
                columnCount = effectiveColumnCount(state, state.favoritesSingleColumn),
                onToggleFavorite = viewModel::toggleFavorite
            )
        }
    }
}

/** Favorites grid follows the same adaptive columns as Home. */
@Composable
private fun favAdaptiveColumns(): Int {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val width = config.screenWidthDp
    return when {
        width >= 840 -> 4
        width >= 600 -> 3
        else -> 2
    }
}
