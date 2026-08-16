package com.flowgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowgallery.app.R
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.FolderType
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.data.model.HomeFilter
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.data.model.SortMode
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.viewmodel.GalleryViewModel

/** Main gallery screen: folder selector as a scrolling header + waterfall grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    onOpenFolderModal: () -> Unit,
    onOpenSearch: () -> Unit,
    onImageClick: (ImageItem) -> Unit,
    onRemoveFolder: (Folder) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    /** External scroll-to-top signal (system back from MainActivity). */
    scrollToTopSignal: Int = 0
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val visible = viewModel.visibleImages(state)
    val selectedFolders = state.folders.filter { it.isSelected }

    // Title + folder selector are a full-width HEADER ITEM of the waterfall:
    // they only appear at the top of the content and scroll away together
    // with the grid (identical speed, no separate animation).
    var headerHidden by remember { mutableStateOf(false) }
    var headerScrollTarget by remember { mutableStateOf(0) }

    // External back-press scroll-to-top: bump the internal target.
    androidx.compose.runtime.LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            headerScrollTarget = headerScrollTarget + 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Pull-to-refresh wraps the whole home content (FR-9)
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::rescan,
            modifier = Modifier.fillMaxSize()
        ) {
        // Waterfall grid or empty state
        // fillMaxWidth: without it the Box collapses to its content
        // width inside the Column, so align(Center) centers within a
        // narrow box and the spinner / empty state appear off-center.
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Empty content (refreshing or not) shows the guide state —
            // no endless spinner; pull-to-refresh provides its own indicator.
            if (visible.isEmpty()) {
                // Empty state must KEEP the header (title + folder selector)
                // visible, otherwise the user cannot switch folders back.
                Column(modifier = Modifier.fillMaxSize()) {
                    HomeHeader(
                        state = state,
                        selectedFolders = selectedFolders,
                        onOpenSearch = onOpenSearch,
                        onOpenFolderModal = onOpenFolderModal,
                        onSetSortMode = viewModel::setSortMode,
                        onToggleSingleColumn = viewModel::toggleSingleColumn,
                        onSelectFolder = viewModel::selectFilter,
                        onSelectSubFolder = viewModel::selectSubFolder
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            onAddFolder = onOpenFolderModal
                        )
                    }
                }
            } else {
                WaterfallGrid(
                    images = visible,
                    favoriteIds = favorites,
                    onImageClick = onImageClick,
                    onToggleFavorite = viewModel::toggleFavorite,
                    columnCount = effectiveColumnCount(state),
                    sortMode = state.sortMode,
                    header = {
                        HomeHeader(
                            state = state,
                            selectedFolders = selectedFolders,
                            onOpenSearch = onOpenSearch,
                            onOpenFolderModal = onOpenFolderModal,
                            onSetSortMode = viewModel::setSortMode,
                            onToggleSingleColumn = viewModel::toggleSingleColumn,
                            onSelectFolder = viewModel::selectFilter,
                            onSelectSubFolder = viewModel::selectSubFolder
                        )
                    },
                    onHeaderHidden = { hidden ->
                        if (headerHidden != hidden) {
                            headerHidden = hidden
                            onChromeVisibleChange(!hidden)
                        }
                    },
                    scrollToTopTrigger = headerScrollTarget
                )
            }
        }
        } // PullToRefreshBox

        // Floating folder pill — top-center, shown only while the header has
        // fully scrolled away (shows the current path). Fades in/out.
        androidx.compose.animation.AnimatedVisibility(
            visible = headerHidden,
            enter = androidx.compose.animation.fadeIn() +
                androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 12.dp)
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .clickable { headerScrollTarget = headerScrollTarget + 1 }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = currentFolderLabel(state, selectedFolders),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bottom row (left or right per Settings): stats pill + tap-to-top
        // button. The tap-to-top button always sits OUTERMOST (against the
        // screen edge), the stats pill inside it. Smooth motion when the
        // bottom nav bar hides/shows comes from MainActivity animating the
        // content's bottom padding (no manual offset here — it would push
        // the row off-screen once the bar is gone).
        Row(
            modifier = Modifier
                .align(if (state.pillAlignmentLeft) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.pillAlignmentLeft) {
                // Left-aligned: [ ↑ ][ stats ]
                TapToTopButton { headerScrollTarget = headerScrollTarget + 1 }
                FloatingStats(
                    imageCount = visible.size,
                    folderCount = selectedFolders.size,
                    hdCount = visible.count { it.isHd },
                    folders = selectedFolders,
                    currentFilter = state.currentFilter,
                    currentSubFolderId = state.currentSubFolderId,
                    onSelectFolder = viewModel::selectFilter,
                    onSelectSubFolder = viewModel::selectSubFolder
                )
            } else {
                // Right-aligned: [ stats ][ ↑ ]
                FloatingStats(
                    imageCount = visible.size,
                    folderCount = selectedFolders.size,
                    hdCount = visible.count { it.isHd },
                    folders = selectedFolders,
                    currentFilter = state.currentFilter,
                    currentSubFolderId = state.currentSubFolderId,
                    onSelectFolder = viewModel::selectFilter,
                    onSelectSubFolder = viewModel::selectSubFolder
                )
                TapToTopButton { headerScrollTarget = headerScrollTarget + 1 }
            }
        }
    } // Box
}

/** Circular tap-to-top button (scrolls the waterfall back to the top). */
@Composable
private fun TapToTopButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.cd_back_to_top),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Title row + folder selector — shared by the waterfall header and the
 *  empty/loading states (so the selector is always reachable). */
@Composable
private fun HomeHeader(
    state: com.flowgallery.app.viewmodel.GalleryUiState,
    selectedFolders: List<Folder>,
    onOpenSearch: () -> Unit,
    onOpenFolderModal: () -> Unit,
    onSetSortMode: (SortMode) -> Unit,
    onToggleSingleColumn: () -> Unit,
    onSelectFolder: (Long?) -> Unit,
    onSelectSubFolder: (Long) -> Unit
) {
    Column {
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
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Add-folder button (moved from the removed FAB)
            IconButton(onClick = onOpenFolderModal) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_folder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Sort menu (deep-style dropdown matching the app language)
            SortMenuButton(
                current = state.sortMode,
                onSelect = onSetSortMode
            )
            // Single-column force toggle (overrides the default columns)
            IconButton(onClick = onToggleSingleColumn) {
                Icon(
                    Icons.Filled.ViewAgenda,
                    contentDescription = stringResource(R.string.cd_grid_toggle),
                    tint = if (state.singleColumn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Folder selector: dropdown (All / folders / subfolders)
        FolderDropdown(
            folders = selectedFolders,
            currentFilter = state.currentFilter,
            currentSubFolderId = state.currentSubFolderId,
            totalCount = state.dedupedIds.size,
            onSelectFolder = onSelectFolder,
            onSelectSubFolder = onSelectSubFolder
        )

        Spacer(Modifier.height(4.dp))
    }
}

/** Compact floating stats pill at bottom-right (replaces the add FAB). */
@Composable
private fun FloatingStats(
    imageCount: Int,
    folderCount: Int,
    hdCount: Int,
    folders: List<Folder>,
    currentFilter: Long?,
    currentSubFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onSelectSubFolder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var folderMenuExpanded by remember { mutableStateOf(false) }

    // Folder stat reflects the CURRENTLY BROWSED folder:
    // - PACK (图包) folder → show its sub-folder count, tap to pick a
    //   sub-folder to enter.
    // - NORMAL folder / All view → no folder item at all.
    val currentFolder = folders.find { it.id == currentFilter }
    val showFolderItem = currentFolder != null &&
        currentFolder.type == FolderType.PACK &&
        currentFolder.subFolders.isNotEmpty()
    val subFolderCount = currentFolder?.subFolders?.size ?: 0

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface) // opaque — readable on any content
            // Consume taps anywhere on the pill so they never fall through
            // to the waterfall below (opening a viewer by accident).
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingStatItem(
            Icons.Filled.Image, "$imageCount"
        )
        // Folder item — only for a PACK folder being browsed: shows its
        // sub-folder count; tap lists the sub-folders to enter.
        if (showFolderItem) {
            Box {
                FloatingStatItem(
                    Icons.Filled.Folder, "$subFolderCount",
                    clickable = subFolderCount > 1,
                    onClick = { folderMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    containerColor = scheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    // Only the sub-folders represented by this count.
                    currentFolder!!.subFolders.forEach { sub ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        sub.name,
                                        color = scheme.onSurface,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (currentSubFolderId == sub.id) {
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
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = scheme.primary) },
                            onClick = {
                                folderMenuExpanded = false
                                onSelectSubFolder(sub.id)
                            },
                            colors = menuItemColors(scheme)
                        )
                    }
                }
            }
        }
        FloatingStatItem(
            Icons.Filled.HighQuality, "$hdCount"
        )
    }
}

@Composable
private fun FloatingStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (clickable) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(4.dp)
        } else {
            Modifier
        }
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
    }
}

/** Dropdown folder selector — All / root folders / subfolders. */
@Composable
private fun FolderDropdown(
    folders: List<Folder>,
    currentFilter: Long?,
    currentSubFolderId: Long?,
    totalCount: Int,
    onSelectFolder: (Long?) -> Unit,
    onSelectSubFolder: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    // Current selection label: "All" / "Folder" / "Folder › Sub"
    val currentLabel: String
    val currentIcon: androidx.compose.ui.graphics.vector.ImageVector
    val currentCount: Int
    when {
        currentFilter == null -> {
            currentLabel = stringResource(R.string.all)
            currentIcon = Icons.Filled.GridView
            currentCount = totalCount
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

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // The dropdown must match the selector's exact width and alignment:
        // same 16dp inset on both sides.
        val menuWidth = maxWidth - 32.dp
        val menuOffset = androidx.compose.ui.unit.DpOffset(16.dp, 0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            offset = menuOffset,
            modifier = Modifier
                .width(menuWidth)
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
    } // BoxWithConstraints
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
                Icons.AutoMirrored.Filled.Sort,
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
                    SortMode.DEFAULT -> Icons.AutoMirrored.Filled.Sort
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
private fun EmptyState(onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
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
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

/** Effective grid columns: the given single-column flag wins; otherwise the
 *  portrait/landscape default configured in Settings applies. */
@Composable
fun effectiveColumnCount(
    state: com.flowgallery.app.viewmodel.GalleryUiState,
    singleColumn: Boolean = state.singleColumn
): Int {
    if (singleColumn) return 1
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return if (isLandscape) state.landscapeColumns else state.portraitColumns
}

/** Short label of the current filter for the compact selector pill. */
@Composable
private fun currentFolderLabel(
    state: com.flowgallery.app.viewmodel.GalleryUiState,
    folders: List<Folder>
): String {
    val filter = state.currentFilter
    return when {
        filter == null -> stringResource(R.string.all)
        else -> {
            val folder = folders.find { it.id == filter }
            val sub = folder?.subFolders?.find { it.id == state.currentSubFolderId }
            when {
                sub != null -> "${folder!!.name} / ${sub.name}"
                folder != null -> folder.name
                else -> stringResource(R.string.all)
            }
        }
    }
}
