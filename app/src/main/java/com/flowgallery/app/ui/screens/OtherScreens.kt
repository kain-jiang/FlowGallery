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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgallery.app.BuildConfig
import com.flowgallery.app.R
import com.flowgallery.app.data.Updater
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.ui.theme.Accent
import com.flowgallery.app.ui.theme.Border
import com.flowgallery.app.ui.theme.FgSecondary
import com.flowgallery.app.ui.theme.Muted
import com.flowgallery.app.ui.theme.Surface
import com.flowgallery.app.ui.theme.Surface2
import com.flowgallery.app.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

/** Search screen: filters the current library by filename. */
@Composable
fun SearchScreen(
    viewModel: GalleryViewModel,
    onImageClick: (ImageItem) -> Unit,
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = if (query.isBlank()) {
        emptyList()
    } else {
        viewModel.visibleImages(state).filter {
            it.name.contains(query, ignoreCase = true)
        }.filter { item ->
            when (state.mediaTypeFilter) {
                null -> true
                "IMAGE" -> item.type == MediaType.STATIC_IMAGE
                "ANIMATED" -> item.type.isAnimated
                "VIDEO" -> item.type.isVideo
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header row: back button + title, below the status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.outline) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Spacer(Modifier.height(8.dp))

        // Media-type filter row: All / Images / Animated / Videos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchTypeChip(
                label = stringResource(R.string.search_type_all),
                selected = state.mediaTypeFilter == null,
                onClick = { viewModel.setMediaTypeFilter(null) }
            )
            SearchTypeChip(
                label = stringResource(R.string.search_type_image),
                selected = state.mediaTypeFilter == "IMAGE",
                onClick = { viewModel.setMediaTypeFilter("IMAGE") }
            )
            SearchTypeChip(
                label = stringResource(R.string.search_type_animated),
                selected = state.mediaTypeFilter == "ANIMATED",
                onClick = { viewModel.setMediaTypeFilter("ANIMATED") }
            )
            SearchTypeChip(
                label = stringResource(R.string.search_type_video),
                selected = state.mediaTypeFilter == "VIDEO",
                onClick = { viewModel.setMediaTypeFilter("VIDEO") }
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() -> CenteredHint(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_empty_title),
                    desc = stringResource(R.string.search_empty_desc)
                )
                filtered.isEmpty() -> CenteredHint(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_no_results),
                    desc = stringResource(R.string.search_no_results_desc)
                )
                else -> WaterfallGrid(
                    images = filtered,
                    favoriteIds = favorites,
                    onImageClick = onImageClick,
                    onToggleFavorite = viewModel::toggleFavorite,
                    columnCount = effectiveColumnCount(state)
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(icon: ImageVector, title: String, desc: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            text = desc,
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

/** Settings screen with display options and folder management. */
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Folder) -> Unit,
    onEditType: (Folder) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        item { SettingsSectionTitle(stringResource(R.string.section_folders)) }
        items(state.folders, key = { it.id }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { viewModel.toggleFolder(folder.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        // Tap the leading icon to change the folder type
                        .clickable { onEditType(folder) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (folder.type == com.flowgallery.app.data.model.FolderType.PACK) {
                            Icons.Filled.Collections
                        } else {
                            Icons.Filled.FolderOpen
                        },
                        contentDescription = stringResource(
                            if (folder.type == com.flowgallery.app.data.model.FolderType.PACK) {
                                R.string.folder_type_pack
                            } else {
                                R.string.folder_type_normal
                            }
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            folder.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = stringResource(R.string.image_count, folder.imageCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = folder.isSelected,
                    onCheckedChange = { viewModel.toggleFolder(folder.id) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White
                    )
                )
                // Remove folder (FR-2)
                IconButton(onClick = { onRemoveFolder(folder) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_remove_folder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onAddFolder)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.add_folder),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.section_display)) }
        item {
            // HD thumbnails toggle (interactive switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setting_quality),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.setting_quality_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.hdThumbnails,
                    onCheckedChange = { viewModel.toggleHdThumbnails() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }
        item {
            // Monet (Material You) dynamic colors toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setting_monet),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.setting_monet_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.monetColors,
                    onCheckedChange = { viewModel.toggleMonetColors() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }
        item {
            // Clear cache (actually clears Coil memory + disk caches)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        // Clear Coil memory + disk image caches (FR-8)
                        (context.applicationContext as com.flowgallery.app.FlowGalleryApp)
                            .clearImageCache()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setting_cache),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.setting_cache_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        item {
            // Portrait grid columns — picker (2 / 3 / 4)
            GridColumnsSetting(
                title = stringResource(R.string.setting_grid_columns_portrait),
                current = state.portraitColumns,
                options = listOf(2, 3, 4),
                onChange = viewModel::setPortraitColumns
            )
        }
        item {
            // Landscape grid columns — picker (wider range: 2-6 columns,
            // landscape screens have room for more)
            GridColumnsSetting(
                title = stringResource(R.string.setting_grid_columns_landscape),
                current = state.landscapeColumns,
                options = listOf(2, 3, 4, 5, 6),
                onChange = viewModel::setLandscapeColumns
            )
        }
        item {
            // About — real version from BuildConfig (matches the APK)
            SettingsItem(
                icon = Icons.Filled.Info,
                name = stringResource(R.string.setting_about),
                desc = stringResource(R.string.setting_version_name, BuildConfig.VERSION_NAME)
            )
        }
        item {
            // Check for updates (GitHub Releases self-update)
            UpdateCheckItem()
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(icon: ImageVector, name: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Settings row with a grid-column picker, check-marked. */
@Composable
private fun GridColumnsSetting(
    title: String,
    current: Int,
    options: List<Int>,
    onChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    @Composable
    fun labelOf(count: Int): String = stringResource(
        when (count) {
            4 -> R.string.setting_grid_4
            3 -> R.string.setting_grid_3
            6 -> R.string.setting_grid_6
            5 -> R.string.setting_grid_5
            else -> R.string.setting_grid_2
        }
    )

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Menu width; anchored to the right edge (with margin) so it never
        // hugs the left screen edge.
        val menuWidth = if (isLandscape) 360.dp else 220.dp
        val rightMargin = 16.dp
        val menuOffset = androidx.compose.ui.unit.DpOffset(
            x = maxWidth - menuWidth - rightMargin,
            y = 0.dp
        )

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    labelOf(current),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset,
            modifier = Modifier
                .widthIn(min = menuWidth)
                .clip(RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            options.forEach { count ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                labelOf(count),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (current == count) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        expanded = false
                        onChange(count)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    } // Box (anchor)
    } // BoxWithConstraints
}

/** Small filter chip for the search type row. */
@Composable
private fun SearchTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primary else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * "Check for updates" settings row: queries the GitHub latest release,
 * and if newer, prompts to download + install the APK.
 */
@Composable
private fun UpdateCheckItem() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf("idle") } // idle | checking | update | upToDate | downloading | error
    var updateInfo by remember { mutableStateOf<Updater.UpdateInfo?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    val statusText = when (state) {
        "checking" -> stringResource(R.string.update_checking)
        "update" -> stringResource(R.string.update_available, updateInfo?.versionName ?: "")
        "upToDate" -> stringResource(R.string.update_up_to_date)
        "downloading" -> stringResource(R.string.update_downloading, (progress * 100).toInt())
        "error" -> stringResource(R.string.update_error)
        else -> stringResource(R.string.update_check)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .clickable(enabled = state != "checking" && state != "downloading") {
                scope.launch {
                    state = "checking"
                    val info = Updater.checkForUpdate(BuildConfig.VERSION_CODE)
                    if (info == null) {
                        // null also covers failures — retryable
                        state = "upToDate"
                    } else {
                        updateInfo = info
                        state = "update"
                        showDownloadDialog = true
                    }
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state == "downloading") Icons.Filled.Download else Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.setting_update),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface
            )
            Text(
                statusText,
                fontSize = 12.sp,
                color = if (state == "update") scheme.primary else scheme.onSurfaceVariant
            )
        }
    }

    // New-version dialog → download → install (custom app-styled dialog)
    if (showDownloadDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDownloadDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surface)
                    .padding(24.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.outline)
                )
                Spacer(Modifier.height(20.dp))

                // Icon chip
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.update_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.update_prompt,
                        updateInfo?.versionName ?: "", BuildConfig.VERSION_NAME
                    ),
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                // Confirm (primary) + cancel row — app-style buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel — subtle outline button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .clickable { showDownloadDialog = false }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = scheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Download — primary filled button
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primary)
                            .clickable {
                                showDownloadDialog = false
                                val info = updateInfo ?: return@clickable
                                scope.launch {
                                    state = "downloading"
                                    val apk = java.io.File(context.cacheDir, "shared/flowgallery-update.apk")
                                    val ok = Updater.downloadApk(info.apkUrl, apk)
                                    if (ok) {
                                        state = "idle"
                                        installApk(context, apk)
                                    } else {
                                        state = "error"
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.update_download),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** Kick off the system package installer for the downloaded APK. */
private fun installApk(context: android.content.Context, apk: java.io.File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
