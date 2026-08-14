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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.ui.components.WaterfallGrid
import com.flowgallery.app.ui.theme.Accent
import com.flowgallery.app.ui.theme.Border
import com.flowgallery.app.ui.theme.FgSecondary
import com.flowgallery.app.ui.theme.Muted
import com.flowgallery.app.ui.theme.Surface
import com.flowgallery.app.ui.theme.Surface2
import com.flowgallery.app.viewmodel.GalleryViewModel

/** Search screen: filters the current library by filename. */
@Composable
fun SearchScreen(
    viewModel: GalleryViewModel,
    onImageClick: (ImageItem) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = if (query.isBlank()) {
        emptyList()
    } else {
        viewModel.visibleImages(state).filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search images...", color = Muted) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() -> CenteredHint(
                    icon = Icons.Filled.Search,
                    title = "Search your gallery",
                    desc = "Find images by name, folder, date, or tags"
                )
                filtered.isEmpty() -> CenteredHint(
                    icon = Icons.Filled.Search,
                    title = "No results",
                    desc = "Try a different keyword"
                )
                else -> WaterfallGrid(
                    images = filtered,
                    favoriteIds = favorites,
                    onImageClick = onImageClick,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(icon: ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = desc,
            color = FgSecondary,
            fontSize = 14.sp
        )
    }
}

/** Settings screen with display options and folder management. */
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    onAddFolder: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        SettingsSectionTitle("Folders")
        state.folders.forEach { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .clickable { viewModel.toggleFolder(folder.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${folder.imageCount} images",
                        fontSize = 12.sp,
                        color = FgSecondary
                    )
                }
                Switch(
                    checked = folder.isSelected,
                    onCheckedChange = { viewModel.toggleFolder(folder.id) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Accent,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .clickable(onClick = onAddFolder)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Add Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Accent
            )
        }

        SettingsSectionTitle("Display")
        SettingsItem(
            icon = Icons.Filled.HighQuality,
            name = "Image Quality",
            desc = "HD thumbnails"
        )
        SettingsItem(
            icon = Icons.Filled.Storage,
            name = "Clear Cache",
            desc = "Release cached thumbnails"
        )
        SettingsItem(
            icon = Icons.Filled.Info,
            name = "FlowGallery",
            desc = "Version 1.0.0"
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Muted,
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
            .background(Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = FgSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = FgSecondary)
        }
    }
}
