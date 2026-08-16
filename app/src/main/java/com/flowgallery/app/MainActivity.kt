package com.flowgallery.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.R
import com.flowgallery.app.ui.components.FolderSelectionModal
import com.flowgallery.app.ui.components.FolderTypeDialog
import com.flowgallery.app.ui.components.ImageViewer
import com.flowgallery.app.ui.components.SourcePickerDialog
import com.flowgallery.app.ui.screens.FavoritesScreen
import com.flowgallery.app.ui.screens.HomeScreen
import com.flowgallery.app.ui.screens.IndexScreen
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
            val vm: GalleryViewModel = viewModel()
            viewModel = vm
            val state by vm.uiState.collectAsState()
            FlowGalleryTheme(monet = state.monetColors) {
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
    val uiContext = androidx.compose.ui.platform.LocalContext.current

    // Surface scan / connection errors as a toast.
    val scanError = state.error
    androidx.compose.runtime.LaunchedEffect(scanError) {
        if (scanError != null) {
            android.widget.Toast.makeText(
                uiContext, scanError, android.widget.Toast.LENGTH_LONG
            ).show()
            viewModel.clearError()
        }
    }

    // One-shot index notice ("indexing new content…") as a toast.
    val indexNotice = state.indexNotice
    androidx.compose.runtime.LaunchedEffect(indexNotice) {
        if (indexNotice != null) {
            android.widget.Toast.makeText(
                uiContext, indexNotice, android.widget.Toast.LENGTH_SHORT
            ).show()
            viewModel.clearIndexNotice()
        }
    }

    // Monet (Material You) dynamic colors: the system wallpaper colors are
    // read once when the Activity composes — a wallpaper change does NOT
    // refresh them by itself. Listen for color changes and recreate the
    // Activity so dynamicDarkColorScheme re-reads the new palette.
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        androidx.compose.runtime.DisposableEffect(state.monetColors) {
            val activity = uiContext as? android.app.Activity
            val wallpaperManager = android.app.WallpaperManager.getInstance(uiContext)
            val listener = object : android.app.WallpaperManager.OnColorsChangedListener {
                override fun onColorsChanged(colors: android.app.WallpaperColors?, which: Int) {
                    if (state.monetColors) {
                        activity?.recreate()
                    }
                }
            }
            wallpaperManager.addOnColorsChangedListener(
                listener,
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            onDispose {
                wallpaperManager.removeOnColorsChangedListener(listener)
            }
        }
    }

    var showFolderModal by remember { mutableStateOf(false) }
    var folderToRemove by remember { mutableStateOf<Folder?>(null) }
    var folderToEditType by remember { mutableStateOf<Folder?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // First back on Home scrolls to top; the second one opens the exit dialog.
    var pendingExitConfirm by remember { mutableStateOf(false) }
    var scrollToTopSignal by remember { mutableStateOf(0) }
    var showSourcePicker by remember { mutableStateOf(false) }
    // Bottom nav + system bars auto-hide while browsing down (Home only)
    var bottomBarVisible by remember { mutableStateOf(true) }

    // Fully immersive while browsing: when the Home chrome is hidden, also
    // hide the system status bar and navigation bar (white pill).
    val immersiveView = LocalView.current
    DisposableEffect(bottomBarVisible, state.currentTab, state.viewer.isOpen) {
        val window = (immersiveView.context as? android.app.Activity)?.window
        if (window != null && !state.viewer.isOpen) {
            val controller = WindowCompat.getInsetsController(window, immersiveView)
            if (state.currentTab == GalleryTab.Home && !bottomBarVisible) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }

    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Landscape/tablet: no bottom bar (side rail instead).
            if (!isLandscape) {
                AnimatedVisibility(
                    // Home hides with scroll; other tabs always show the bar
                    visible = state.currentTab != GalleryTab.Home || bottomBarVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
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
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
            // Landscape/tablet layout: a fixed side rail on the left — it
            // does NOT participate in the Home scroll-hide behavior.
            if (isLandscape) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxHeight()
                        // Keep the rail clear of the punch-hole cutout too
                        .windowInsetsPadding(WindowInsets.displayCutout)
                ) {
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(12.dp)
                    )
                    GalleryTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = state.currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(tabIcon(tab), contentDescription = tabLabel(tab)) },
                            label = { Text(tabLabel(tab), fontSize = 10.sp) }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // In landscape, keep clear of the punch-hole camera cutout
                    // (the cutout sits on the left/right in landscape).
                    .windowInsetsPadding(if (isLandscape) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0))
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        // Animate the bottom inset so the home chrome (stats
                        // pill / tap-to-top) glides smoothly when the bottom
                        // nav bar hides or shows, instead of jumping.
                        bottom = androidx.compose.animation.core.animateDpAsState(
                            targetValue = innerPadding.calculateBottomPadding(),
                            animationSpec = androidx.compose.animation.core.tween(250),
                            label = "bottomInset"
                        ).value,
                        start = if (isLandscape) 0.dp else innerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        end = if (isLandscape) 0.dp else innerPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                    )
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
                        },
                        onChromeVisibleChange = { visible -> bottomBarVisible = visible },
                        scrollToTopSignal = scrollToTopSignal
                    )
                    GalleryTab.Favorites -> FavoritesScreen(
                        viewModel = viewModel,
                        onImageClick = { img ->
                            // Browse ONLY the favorited sequence in the viewer
                            val favList = state.images.filter { it.id in favorites }
                            val idx = favList.indexOfFirst { it.id == img.id }
                            if (idx >= 0) viewModel.openViewer(idx, favoritesOnly = true)
                        }
                    )
                    GalleryTab.Settings -> SettingsScreen(
                        viewModel = viewModel,
                        onAddFolder = { showSourcePicker = true },
                        onRemoveFolder = { folder -> folderToRemove = folder },
                        onEditType = { folder -> folderToEditType = folder }
                    )
                    GalleryTab.Index -> IndexScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Back handling: search overlay → close; viewer → close viewer;
    // folder modal → close modal; Home → scroll to top first, then exit
    // confirm dialog; other tabs → exit confirm directly.
    if (showSearch) {
        BackHandler { showSearch = false }
    } else if (state.viewer.isOpen) {
        BackHandler { viewModel.closeViewer() }
    } else if (showFolderModal) {
        BackHandler { showFolderModal = false }
    } else if (showExitConfirm) {
        BackHandler { showExitConfirm = false }
    } else {
        BackHandler {
            if (state.currentTab == GalleryTab.Home) {
                if (pendingExitConfirm) {
                    showExitConfirm = true
                } else {
                    scrollToTopSignal = scrollToTopSignal + 1
                    pendingExitConfirm = true
                }
            } else {
                showExitConfirm = true
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

    // Full-screen viewer — shows ONLY the current subfolder's media; the
    // thumbnail strip stays within it, and boundary navigation crosses to
    // adjacent subfolders (currentSubFolderId syncs so returning to Home
    // lands on the subfolder being browsed).
    val viewerImages = viewModel.viewerImages(state)
    if (state.viewer.isOpen && viewerImages.isNotEmpty()) {
        val index = state.viewer.index.coerceIn(0, viewerImages.lastIndex)
        val mediaContext = androidx.compose.ui.platform.LocalContext.current
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
            // Double-arrow affordance: only when an adjacent subfolder exists
            canCrossBackward = viewModel.canCrossViewer(-1),
            canCrossForward = viewModel.canCrossViewer(1),
            onClose = viewModel::closeViewer,
            onToggleFavorite = viewModel::toggleFavorite,
            onShare = { img -> mediaContext.shareMedia(img) },
            onSaveToGallery = { img -> mediaContext.saveMediaToGallery(img) }
        )
    }

    // Remove-folder confirmation dialog (FR-2) — app-styled
    folderToRemove?.let { folder ->
        val scheme = MaterialTheme.colorScheme
        androidx.compose.ui.window.Dialog(onDismissRequest = { folderToRemove = null }) {
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

                // Warning icon chip
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = scheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.remove_folder_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.remove_folder_msg, folder.name, folder.imageCount),
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                // Cancel + confirm buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .clickable { folderToRemove = null }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.remove_folder_cancel),
                            color = scheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.error.copy(alpha = 0.15f))
                            .clickable {
                                viewModel.removeFolder(folder.id)
                                folderToRemove = null
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.remove_folder_confirm),
                            color = scheme.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Exit-confirm dialog — app-styled (drag handle + icon + buttons)
    if (showExitConfirm) {
        val scheme = MaterialTheme.colorScheme
        androidx.compose.ui.window.Dialog(onDismissRequest = { showExitConfirm = false }) {
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
                        Icons.Filled.ExitToApp,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.exit_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.exit_message),
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                // Cancel + exit buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .clickable {
                                showExitConfirm = false
                                pendingExitConfirm = false
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.exit_cancel),
                            color = scheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primary.copy(alpha = 0.15f))
                            .clickable {
                                (uiContext as? android.app.Activity)?.finish()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.exit_confirm),
                            color = scheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Source picker before adding a folder (only LOCAL implemented)
    if (showSourcePicker) {
        SourcePickerDialog(
            onPickLocal = {
                showSourcePicker = false
                onPickFolder()
            },
            onPickExternal = { type ->
                showSourcePicker = false
                android.widget.Toast.makeText(
                    uiContext,
                    uiContext.getString(
                        com.flowgallery.app.R.string.source_wip,
                        type.label
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showSourcePicker = false }
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
        SearchScreen(
            viewModel = viewModel,
            onImageClick = { img ->
                val idx = viewModel.visibleImages(state).indexOfFirst { it.id == img.id }
                if (idx >= 0) viewModel.openViewer(idx)
            },
            onBack = { showSearch = false }
        )
    }
}

/**
 * Share a media item via the system share sheet (ACTION_SEND + FileProvider).
 * The SAF document is copied into cacheDir/shared (the only cache path the
 * FileProvider exposes) so the URI resolves; shows a toast on failure.
 */
private fun android.content.Context.shareMedia(item: ImageItem) {
    val uri = Uri.parse(item.uriString)
    try {
        val shareDir = java.io.File(cacheDir, "shared").apply { mkdirs() }
        val outFile = java.io.File(shareDir, "share_${System.currentTimeMillis()}_${item.name}")
        val copied = contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied) {
            toast(getString(R.string.share_failed))
            return
        }
        val shareUri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", outFile
        )
        val mime = if (item.type == com.flowgallery.app.data.model.MediaType.VIDEO) "video/*" else "image/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, null))
    } catch (e: Exception) {
        toast(getString(R.string.share_failed))
    }
}

/**
 * Save a media item into the system gallery (MediaStore, no permission needed
 * on API 29+). Shows a toast on success or failure.
 */
private fun android.content.Context.saveMediaToGallery(item: ImageItem) {
    val uri = Uri.parse(item.uriString)
    val mime = if (item.type == com.flowgallery.app.data.model.MediaType.VIDEO) "video/mp4" else "image/jpeg"
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, item.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FlowGallery")
        }
        val collection = android.provider.MediaStore.Images.Media.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val saved = contentResolver.insert(collection, values)?.let { dest ->
            contentResolver.openInputStream(uri)?.use { input ->
                contentResolver.openOutputStream(dest)?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } ?: false
        toast(if (saved) getString(R.string.save_success) else getString(R.string.save_failed))
    } catch (e: Exception) {
        toast(getString(R.string.save_failed))
    }
}

/** Show a short toast on the main thread. */
private fun android.content.Context.toast(msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun tabIcon(tab: GalleryTab): ImageVector = when (tab) {
    GalleryTab.Home -> Icons.Filled.Home
    GalleryTab.Favorites -> Icons.Filled.Favorite
    GalleryTab.Settings -> Icons.Filled.Settings
    GalleryTab.Index -> Icons.Filled.Bolt
}

@Composable
private fun tabLabel(tab: GalleryTab): String = when (tab) {
    GalleryTab.Home -> stringResource(R.string.tab_home)
    GalleryTab.Favorites -> stringResource(R.string.tab_favorites)
    GalleryTab.Settings -> stringResource(R.string.tab_settings)
    GalleryTab.Index -> stringResource(R.string.tab_index)
}
