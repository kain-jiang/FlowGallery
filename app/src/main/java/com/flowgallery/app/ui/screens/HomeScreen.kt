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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.ui.theme.Accent
import com.flowgallery.app.ui.theme.AccentMuted
import com.flowgallery.app.ui.theme.Border
import com.flowgallery.app.ui.theme.FgSecondary
import com.flowgallery.app.ui.theme.Muted
import com.flowgallery.app.ui.theme.Surface
import com.flowgallery.app.ui.theme.Surface2
import com.flowgallery.app.viewmodel.GalleryViewModel

/** Main gallery screen: tab strip + stats bar + waterfall grid + FAB. */
@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    onOpenFolderModal: () -> Unit,
    onImageClick: (ImageItem) -> Unit
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
                text = "FlowGallery",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = FgSecondary)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.GridView, contentDescription = "Grid toggle", tint = FgSecondary)
            }
        }

        // Folder tab strip
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FolderTab(
                    label = "All",
                    count = visible.size,
                    isActive = state.currentFolderId == null,
                    onClick = { viewModel.selectFolder(null) }
                )
            }
            items(selectedFolders, key = { it.id }) { folder ->
                FolderTab(
                    label = folder.name,
                    count = folder.imageCount,
                    isActive = state.currentFolderId == folder.id,
                    onClick = { viewModel.selectFolder(folder.id) }
                )
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
                    color = Accent
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
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
        } // Column

        // FAB for adding folders
        FloatingActionButton(
            onClick = onOpenFolderModal,
            containerColor = Accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add folder")
        }
    } // Box
}

@Composable
private fun FolderTab(label: String, count: Int, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) AccentMuted else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isActive) Accent else FgSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) Accent else Surface2)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                color = if (isActive) Color.White else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatsBar(imageCount: Int, folderCount: Int, hdCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatItem(Icons.Filled.Image, "$imageCount", "IMAGES")
        StatItem(Icons.Filled.Folder, "$folderCount", "FOLDERS")
        StatItem(Icons.Filled.Image, "$hdCount", "HD")
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
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
                color = Muted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyState(onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No images yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the + button to add folders and start browsing your image packs",
            color = FgSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Add Folder",
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onAddFolder)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}
