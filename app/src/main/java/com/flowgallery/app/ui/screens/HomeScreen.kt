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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.flowgallery.app.data.model.SortMode
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
                // Sort menu (deep-style dropdown matching the app language)
                SortMenuButton(
                    current = state.sortMode,
                    onSelect = viewModel::setSortMode
                )
                // Grid density toggle 2<->3 columns (FR-1 decision #3)
                IconButton(onClick = viewModel::toggleColumns) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.cd_grid_toggle),
                        tint = if (state.threeColumns) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Folder selector: dropdown (All / Favorites / folders / subfolders)
            FolderDropdown(
                folders = selectedFolders,
                currentFilter = state.currentFilter,
                currentSubFolderId = state.currentSubFolderId,
                favoritesCount = favorites.size,
                totalCount = state.images.size,
                onSelectFolder = viewModel::selectFilter,
                onSelectSubFolder = viewModel::selectSubFolder
            )

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
                        columnCount = if (state.threeColumns) 3 else 2,
                        sortMode = state.sortMode
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

/** Dropdown folder selector — All / Favorites / root folders / subfolders. */
@Composable
private fun FolderDropdown(
    folders: List<Folder>,
    currentFilter: Long?,
    currentSubFolderId: Long?,
    favoritesCount: Int,
    totalCount: Int,
    onSelectFolder: (Long?) -> Unit,
    onSelectSubFolder: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    // Current selection label: "All" / "Favorites" / "Folder" / "Folder › Sub"
    val currentLabel: String
    val currentIcon: androidx.compose.ui.graphics.vector.ImageVector
    val currentCount: Int
    when {
        currentFilter == null -> {
            currentLabel = stringResource(R.string.all)
            currentIcon = Icons.Filled.GridView
            currentCount = totalCount
        }
        currentFilter == HomeFilter.FAVORITES -> {
            currentLabel = stringResource(R.string.favorites)
            currentIcon = Icons.Filled.Favorite
            currentCount = favoritesCount
        }
        else -> {
            val folder = folders.find { it.id == currentFilter }
            val sub = folder?.subFolders?.find { it.id == currentSubFolderId }
            if (sub != null) {
                currentLabel = "${folder!!.name} / ${sub.name}"
                currentIcon = Icons.Filled.FolderOpen
                currentCount = sub.imageCount
            } else {
                currentLabel = folder?.name ?: stringResource(R.string.all)
                currentIcon = Icons.Filled.Folder
                currentCount = folder?.imageCount ?: 0
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                currentIcon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = currentLabel,
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$currentCount",
                color = scheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            containerColor = scheme.surface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all), color = scheme.onSurface, fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = scheme.primary) },
                onClick = { onSelectFolder(null); expanded = false },
                colors = menuItemColors(scheme)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites), color = scheme.onSurface, fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFEF4444)) },
                onClick = { onSelectFolder(HomeFilter.FAVORITES); expanded = false },
                colors = menuItemColors(scheme)
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, color = scheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${folder.imageCount}",
                                color = scheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = scheme.primary) },
                    onClick = { onSelectFolder(folder.id); expanded = false },
                    colors = menuItemColors(scheme)
                )
                // First-level subfolders, indented under their parent (FR-2.1)
                folder.subFolders
                    .filter { it.imageCount > 0 }
                    .forEach { sub ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 20.dp)
                                ) {
                                    Text(
                                        sub.name,
                                        fontSize = 14.sp,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "${sub.imageCount}",
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = scheme.primary.copy(alpha = 0.6f)) },
                            onClick = {
                                onSelectFolder(folder.id)
                                onSelectSubFolder(sub.id)
                                expanded = false
                            },
                            colors = menuItemColors(scheme)
                        )
                    }
            }
        }
    }
}

@Composable
private fun menuItemColors(scheme: androidx.compose.material3.ColorScheme)
    : androidx.compose.material3.MenuItemColors {
    return androidx.compose.material3.MenuDefaults.itemColors(
        textColor = scheme.onSurface,
        leadingIconColor = scheme.primary,
        disabledTextColor = scheme.onSurfaceVariant,
        disabledLeadingIconColor = scheme.onSurfaceVariant
    )
}

/** Sort-mode dropdown button, styled like the folder selector menu. */
@Composable
private fun SortMenuButton(
    current: SortMode,
    onSelect: (SortMode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.Sort,
                contentDescription = stringResource(R.string.cd_sort),
                tint = if (current != SortMode.DEFAULT) scheme.primary else scheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
            containerColor = scheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            SortMode.entries.forEach { mode ->
                val label = when (mode) {
                    SortMode.DEFAULT -> stringResource(R.string.sort_default)
                    SortMode.LATEST -> stringResource(R.string.sort_latest)
                    SortMode.OLDEST -> stringResource(R.string.sort_oldest)
                    SortMode.LARGEST -> stringResource(R.string.sort_largest)
                    SortMode.SMALLEST -> stringResource(R.string.sort_smallest)
                    SortMode.QUALITY -> stringResource(R.string.sort_quality)
                }
                val icon = when (mode) {
                    SortMode.DEFAULT -> Icons.Filled.Sort
                    SortMode.LATEST -> Icons.Filled.Schedule
                    SortMode.OLDEST -> Icons.Filled.Schedule
                    SortMode.LARGEST -> Icons.Filled.ExpandMore
                    SortMode.SMALLEST -> Icons.Filled.ExpandLess
                    SortMode.QUALITY -> Icons.Filled.HighQuality
                }
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = scheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                            if (mode == current) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = scheme.primary) },
                    onClick = { onSelect(mode); expanded = false },
                    colors = menuItemColors(scheme)
                )
            }
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
