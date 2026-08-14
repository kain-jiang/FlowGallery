package com.flowgallery.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.ui.components.FolderSelectionModal
import com.flowgallery.app.ui.components.ImageViewer
import com.flowgallery.app.ui.screens.HomeScreen
import com.flowgallery.app.ui.screens.SearchScreen
import com.flowgallery.app.ui.screens.SettingsScreen
import com.flowgallery.app.ui.theme.FlowGalleryTheme
import com.flowgallery.app.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    // SAF picker: let the user choose image folders ("图包" directories)
    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                // Persist read permission across reboots
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val name = queryDisplayName(uri) ?: "Folder ${uri.lastPathSegment ?: ""}"
                val vm = viewModel
                if (vm != null) {
                    vm.addFolder(uri, name)
                } else {
                    // viewModel not composed yet — persist now, it will load on init
                    val repo = com.flowgallery.app.data.repository.ImageRepository(applicationContext)
                    repo.addFolder(uri, name)
                }
            }
        }

    private var viewModel: GalleryViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowGalleryTheme {
                val vm: GalleryViewModel = viewModel()
                viewModel = vm
                MainScaffold(vm, onPickFolder = { folderPicker.launch(null) })
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            contentResolver.query(
                docUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: GalleryViewModel,
    onPickFolder: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var showFolderModal by remember { mutableStateOf(false) }

    val visibleImages = viewModel.visibleImages(state)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                GalleryTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.currentTab) {
                GalleryTab.Home -> HomeScreen(
                    viewModel = viewModel,
                    onOpenFolderModal = { showFolderModal = true },
                    onImageClick = { img ->
                        val idx = visibleImages.indexOfFirst { it.id == img.id }
                        if (idx >= 0) viewModel.openViewer(idx)
                    }
                )
                GalleryTab.Search -> SearchScreen(
                    viewModel = viewModel,
                    onImageClick = { img ->
                        val idx = viewModel.visibleImages(state).indexOfFirst { it.id == img.id }
                        if (idx >= 0) viewModel.openViewer(idx)
                    }
                )
                GalleryTab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onAddFolder = onPickFolder
                )
            }
        }
    }

    // Folder selection modal
    if (showFolderModal) {
        FolderSelectionModal(
            folders = state.folders,
            onToggle = viewModel::toggleFolder,
            onAddFolder = {
                showFolderModal = false
                onPickFolder()
            },
            onClose = { showFolderModal = false }
        )
    }

    // Full-screen viewer
    if (state.viewer.isOpen && visibleImages.isNotEmpty()) {
        val index = state.viewer.index.coerceIn(0, visibleImages.lastIndex)
        ImageViewer(
            images = visibleImages,
            currentIndex = index,
            favoriteIds = favorites,
            onNavigate = { newIdx ->
                viewModel.openViewer(newIdx)
            },
            onClose = viewModel::closeViewer,
            onToggleFavorite = viewModel::toggleFavorite
        )
    }
}

private fun tabIcon(tab: GalleryTab): ImageVector = when (tab) {
    GalleryTab.Home -> Icons.Filled.Home
    GalleryTab.Search -> Icons.Filled.Search
    GalleryTab.Settings -> Icons.Filled.Settings
}
