package com.flowgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowgallery.app.R
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.data.model.HomeFilter
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.viewmodel.GalleryViewModel

/** Main gallery screen: tab strip + stats bar + waterfall grid + FAB. */
@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    onOpenFolderModal: () -> Unit,
    onImageClick: (ImageItem) -> Unit,
    onRemoveFolder: (Folder) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val visible = viewModel.visibleImages(state)
    val selectedFolders = state.folders.filter { it.isSelected }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Grid density toggle 2<->3 columns (FR-1 decision #3)
                IconButton(onClick = viewModel::toggleColumns) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.cd_grid_toggle),
                        tint = if (state.threeColumns) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Row 1: All → Favorites → root folders
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FolderTab(
                        label = stringResource(R.string.all),
                        count = state.images.size,
                        isActive = state.currentFilter == null,
                        onClick = { viewModel.selectFilter(null) }
                    )
                }
                item {
                    FolderTab(
                        label = stringResource(R.string.favorites),
                        count = favorites.size,
                        isActive = state.currentFilter == HomeFilter.FAVORITES,
                        isFavorite = true,
                        onClick = { viewModel.selectFilter(HomeFilter.FAVORITES) }
                    )
                }
                items(selectedFolders, key = { it.id }) { folder ->
                    FolderTab(
                        label = folder.name,
                        count = folder.imageCount,
                        isActive = state.currentFilter == folder.id && state.currentSubFolderId == null,
                        onClick = { viewModel.selectFilter(folder.id) }
                    )
                }
            }

            // Row 2: drill-down subfolder tabs for the selected root folder (FR-2.1)
            val activeFolder = selectedFolders.find { it.id == state.currentFilter }
            if (activeFolder != null && activeFolder.subFolders.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SubFolderTab(
                            label = activeFolder.name,
                            count = activeFolder.subFolders.sumOf { it.imageCount },
                            isActive = state.currentSubFolderId == null,
                            onClick = { viewModel.selectSubFolder(-1L) }
                        )
                    }
                    items(activeFolder.subFolders, key = { it.id }) { sub ->
                        SubFolderTab(
                            label = sub.name,
                            count = sub.imageCount,
                            isActive = state.currentSubFolderId == sub.id,
                            onClick = { viewModel.selectSubFolder(sub.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats bar
            StatsBar(
                imageCount = visible.size,
                folderCount = selectedFolders.size,
                hdCount = visible.count { it.isHd }
            )

            // Waterfall grid or empty state
            Box(modifier = Modifier.weight(1f)) {
                if (state.isRefreshing && visible.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (visible.isEmpty()) {
                    EmptyState(
                        onAddFolder = onOpenFolderModal,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    WaterfallGrid(
                        images = visible,
                        favoriteIds = favorites,
                        onImageClick = onImageClick,
                        onToggleFavorite = viewModel::toggleFavorite,
                        columnCount = if (state.threeColumns) 3 else 2
                    )
                }
            }
        } // Column

        // FAB for adding folders
        FloatingActionButton(
            onClick = onOpenFolderModal,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_folder))
        }
    } // Box
}

@Composable
private fun FolderTab(
    label: String,
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    isFavorite: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) scheme.primaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = if (isActive) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (isActive) scheme.primary else scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) scheme.primary else scheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                color = if (isActive) Color.White else scheme.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Second-row drill-down tab for a first-level subfolder (FR-2.1). */
@Composable
private fun SubFolderTab(
    label: String,
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isActive) scheme.primary else scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) scheme.primary else scheme.surface)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "$count",
                color = if (isActive) Color.White else scheme.outline,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatsBar(imageCount: Int, folderCount: Int, hdCount: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatItem(Icons.Filled.Image, "$imageCount", stringResource(R.string.stat_images))
        StatItem(Icons.Filled.Folder, "$folderCount", stringResource(R.string.stat_folders))
        StatItem(Icons.Filled.HighQuality, "$hdCount", stringResource(R.string.stat_hd))
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = scheme.outline,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyState(onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = scheme.outline,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_desc),
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.add_folder),
            color = scheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onAddFolder)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}
