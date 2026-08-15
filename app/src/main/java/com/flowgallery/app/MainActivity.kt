package com.flowgallery.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.FolderType
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.R
import com.flowgallery.app.ui.components.FolderSelectionModal
import com.flowgallery.app.ui.components.FolderTypeDialog
import com.flowgallery.app.ui.components.ImageViewer
import com.flowgallery.app.ui.screens.FavoritesScreen
import com.flowgallery.app.ui.screens.HomeScreen
import com.flowgallery.app.ui.screens.SearchScreen
import com.flowgallery.app.ui.screens.SettingsScreen
import com.flowgallery.app.ui.theme.FlowGalleryTheme
import com.flowgallery.app.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    /** Pending SAF selection awaiting folder-type choice in the UI. */
    private class PendingFolder(val uri: Uri, val name: String)

    private var pendingFolder: PendingFolder? = null

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
                val name = queryDisplayName(uri) ?: getString(R.string.default_folder_name)
                pendingFolder = PendingFolder(uri, name)
                pendingFolderTrigger.value = pendingFolderTrigger.value + 1
            }
        }

    /** Observable bump so Compose knows a folder is awaiting type selection. */
    private val pendingFolderTrigger = androidx.compose.runtime.mutableIntStateOf(0)

    private var viewModel: GalleryViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowGalleryTheme {
                val vm: GalleryViewModel = viewModel()
                viewModel = vm
                MainScaffold(
                    vm,
                    onPickFolder = { folderPicker.launch(null) },
                    pendingFolderUri = pendingFolder?.uri,
                    pendingFolderName = pendingFolder?.name,
                    pendingTrigger = pendingFolderTrigger.value,
                    onPendingConsumed = {
                        pendingFolder = null
                        // bump trigger so Compose recomposes and the dialog
                        // actually disappears (params are read at composition)
                        pendingFolderTrigger.value = pendingFolderTrigger.value + 1
                    }
                )
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
    onPickFolder: () -> Unit,
    pendingFolderUri: Uri?,
    pendingFolderName: String?,
    pendingTrigger: Int,
    onPendingConsumed: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var showFolderModal by remember { mutableStateOf(false) }
    var folderToRemove by remember { mutableStateOf<Folder?>(null) }
    var folderToEditType by remember { mutableStateOf<Folder?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                GalleryTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(tabIcon(tab), contentDescription = tabLabel(tab)) },
                        label = { Text(tabLabel(tab)) }
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
                    onOpenSearch = { showSearch = true },
                    onImageClick = { img ->
                        val idx = viewModel.visibleImages(state).indexOfFirst { it.id == img.id }
                        if (idx >= 0) viewModel.openViewer(idx)
                    },
                    onRemoveFolder = { folder ->
                        showFolderModal = false
                        folderToRemove = folder
                    }
                )
                GalleryTab.Favorites -> FavoritesScreen(
                    viewModel = viewModel,
                    onImageClick = { img ->
                        val idx = viewModel.visibleImages(state).indexOfFirst { it.id == img.id }
                        if (idx >= 0) viewModel.openViewer(idx)
                    }
                )
                GalleryTab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onAddFolder = onPickFolder,
                    onRemoveFolder = { folder -> folderToRemove = folder },
                    onEditType = { folder -> folderToEditType = folder }
                )
            }
        }
    }

    // Back handling: search overlay → close; viewer → close viewer;
    // folder modal → close modal
    if (showSearch) {
        BackHandler { showSearch = false }
    } else if (state.viewer.isOpen) {
        BackHandler { viewModel.closeViewer() }
    } else if (showFolderModal) {
        BackHandler { showFolderModal = false }
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

    // Full-screen viewer — shows ONLY the current subfolder's media; the
    // thumbnail strip stays within it, and boundary navigation crosses to
    // adjacent subfolders (currentSubFolderId syncs so returning to Home
    // lands on the subfolder being browsed).
    val viewerImages = viewModel.viewerImages(state)
    if (state.viewer.isOpen && viewerImages.isNotEmpty()) {
        val index = state.viewer.index.coerceIn(0, viewerImages.lastIndex)
        // Immersive mode: hide status bar + nav bar while viewing
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                if (window != null) {
                    WindowCompat.getInsetsController(window, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        ImageViewer(
            images = viewerImages,
            currentIndex = index,
            favoriteIds = favorites,
            onNavigate = { newIdx ->
                // absolute index (thumbnail taps): just move within current list
                viewModel.openViewer(newIdx)
            },
            onNavigateDelta = { delta ->
                // relative move (arrows / swipe): cross subfolder at boundaries
                viewModel.navigateViewer(delta)
            },
            onClose = viewModel::closeViewer,
            onToggleFavorite = viewModel::toggleFavorite
        )
    }

    // Remove-folder confirmation dialog (FR-2)
    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(stringResource(R.string.remove_folder_title)) },
            text = {
                Text(stringResource(R.string.remove_folder_msg, folder.name, folder.imageCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFolder(folder.id)
                    folderToRemove = null
                }) {
                    Text(stringResource(R.string.remove_folder_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text(stringResource(R.string.remove_folder_cancel))
                }
            }
        )
    }

    // Folder-type selection after SAF pick (recommended type pre-selected)
    if (pendingFolderUri != null && pendingFolderName != null && pendingTrigger > 0) {
        // Compute recommendation off the main thread context-free: quick check.
        val repo = com.flowgallery.app.data.repository.ImageRepository(
            androidx.compose.ui.platform.LocalContext.current
        )
        val recommended = remember(pendingTrigger) {
            if (repo.hasSubDirectories(pendingFolderUri)) FolderType.PACK else FolderType.NORMAL
        }
        FolderTypeDialog(
            folderName = pendingFolderName,
            recommended = recommended,
            onConfirm = { type ->
                viewModel.addFolder(pendingFolderUri, pendingFolderName, type)
                onPendingConsumed()
            },
            onDismiss = { onPendingConsumed() }
        )
    }

    // Edit existing folder's type from settings (badge tap)
    folderToEditType?.let { folder ->
        FolderTypeDialog(
            folderName = folder.name,
            recommended = folder.type,
            onConfirm = { type ->
                viewModel.updateFolderType(folder.id, type)
                folderToEditType = null
            },
            onDismiss = { folderToEditType = null }
        )
    }

    // Search overlay — fullscreen over Home, entered via the Home search icon
    if (showSearch) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            SearchScreen(
                viewModel = viewModel,
                onImageClick = { img ->
                    val idx = viewModel.visibleImages(state).indexOfFirst { it.id == img.id }
                    if (idx >= 0) viewModel.openViewer(idx)
                }
            )
            // Back button (top-left, consistent with viewer chrome)
            IconButton(
                onClick = { showSearch = false },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .padding(8.dp)
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun tabIcon(tab: GalleryTab): ImageVector = when (tab) {
    GalleryTab.Home -> Icons.Filled.Home
    GalleryTab.Favorites -> Icons.Filled.Favorite
    GalleryTab.Settings -> Icons.Filled.Settings
}

@Composable
private fun tabLabel(tab: GalleryTab): String = when (tab) {
    GalleryTab.Home -> stringResource(R.string.tab_home)
    GalleryTab.Favorites -> stringResource(R.string.tab_favorites)
    GalleryTab.Settings -> stringResource(R.string.tab_settings)
}
