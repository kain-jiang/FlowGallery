package com.flowgallery.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.FolderType
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.data.model.HomeFilter
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.SortMode
import com.flowgallery.app.data.model.ViewerState
import com.flowgallery.app.data.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val folders: List<Folder> = emptyList(),
    /** FULL set of scanned media — subfolder/folder views must keep every
     *  item even if it duplicates one elsewhere (dedup applies ONLY to the
     *  "All" view via [dedupedIds]). */
    val images: List<ImageItem> = emptyList(),
    /** ids kept after content-dedup — used exclusively by the All view */
    val dedupedIds: Set<Long> = emptySet(),
    val currentTab: GalleryTab = GalleryTab.Home,
    /** null = All, HomeFilter.FAVORITES = favorites, else root folder id */
    val currentFilter: Long? = null,
    /** selected first-level subfolder inside the current root folder (null = root level) */
    val currentSubFolderId: Long? = null,
    val viewer: ViewerState = ViewerState(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** Default portrait grid columns (2/3/4), set in Settings (FR-1). */
    val portraitColumns: Int = 2,
    /** Default landscape grid columns (2/3/4), set in Settings (FR-1). */
    val landscapeColumns: Int = 3,
    /** Force single-column layout (top-bar toggle, overrides both). */
    val singleColumn: Boolean = false,
    /** home grid sort mode */
    val sortMode: SortMode = SortMode.DEFAULT,
    /** HD thumbnail toggle (FR-8) */
    val hdThumbnails: Boolean = true,
    /** type filter for search: null = all, else MediaType name */
    val mediaTypeFilter: String? = null
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImageRepository(app)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    init {
        val savedSort = prefs.getString(KEY_SORT_MODE, null)?.let { name ->
            runCatching { SortMode.valueOf(name) }.getOrNull()
        } ?: SortMode.DEFAULT
        _uiState.update {
            it.copy(
                portraitColumns = prefs.getInt(KEY_PORTRAIT_COLUMNS, 2).coerceIn(2, 4),
                landscapeColumns = prefs.getInt(KEY_LANDSCAPE_COLUMNS, 3).coerceIn(2, 4),
                singleColumn = prefs.getBoolean(KEY_SINGLE_COLUMN, false),
                sortMode = savedSort,
                hdThumbnails = prefs.getBoolean(KEY_HD_THUMBNAILS, true)
            )
        }
        refreshFolders()
    }

    // ------------------------------------------------------------------ folders

    /** Reload persisted folders and rescan their images. */
    fun refreshFolders() {
        viewModelScope.launch {
            val folders = repository.loadFolders()
            _uiState.update { it.copy(folders = folders) }
            if (folders.isNotEmpty()) rescan()
        }
    }

    /** Add a folder from SAF picker result with an explicit type. */
    fun addFolder(uri: Uri, displayName: String, type: FolderType) {
        viewModelScope.launch {
            val added = repository.addFolder(uri, displayName, type)
            if (added) refreshFolders()
        }
    }

    /** Change an existing folder's type (Normal ↔ Pack), then rescan. */
    fun updateFolderType(id: Long, type: FolderType) {
        viewModelScope.launch {
            val folders = _uiState.value.folders.map {
                if (it.id == id) it.copy(type = type, subFolders = emptyList()) else it
            }
            repository.saveFolders(folders)
            _uiState.update { it.copy(folders = folders) }
            rescan()
        }
    }

    /** Toggle a folder's selected flag and rescan. */
    fun toggleFolder(id: Long) {
        viewModelScope.launch {
            val folders = _uiState.value.folders.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            repository.saveFolders(folders)
            _uiState.update { it.copy(folders = folders) }
            rescan()
        }
    }

    /**
     * Remove a folder from the library entirely (FR-2): releases SAF
     * permission and clears the current filter if it pointed at this folder.
     */
    fun removeFolder(id: Long) {
        viewModelScope.launch {
            repository.removeFolder(id)
            val folders = repository.loadFolders()
            val st = _uiState.value
            val filterReset = if (st.currentFilter == id) null else st.currentFilter
            _uiState.update {
                it.copy(folders = folders, currentFilter = filterReset, currentSubFolderId = null)
            }
            rescan()
        }
    }

    // ------------------------------------------------------------------ scanning

    /** Rescan all selected folders (pull-to-refresh / after folder changes). */
    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val selected = _uiState.value.folders.filter { it.isSelected }
            val result = runCatching { repository.scanAll(selected) }
            result.onSuccess { scanResults ->
                // Keep the FULL set — dedup applies only to the All view.
                val images = scanResults.flatMap { it.items }
                // Update per-folder counts and subfolder breakdowns in one pass
                for (res in scanResults) {
                    repository.updateFolderSubFolders(res.folderId, res.subFolders, res.items.size)
                }
                val folders = repository.loadFolders()
                _uiState.update {
                    it.copy(
                        images = images,
                        folders = folders,
                        isRefreshing = false
                    )
                }
                // Resolve real dimensions in the background so HD/SD badges,
                // stats and masonry ratios become accurate (zero-IO scan
                // leaves width/height at 0).
                resolveDimensionsInBackground(images)
                // Compute All-view dedup ids in the background.
                computeDedupInBackground(images)
            }.onFailure { e ->
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    /** Background content-dedup for the All view (size + MD5). */
    private fun computeDedupInBackground(images: List<ImageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriDeduped = images.distinctBy { it.uriString }
            val deduped = repository.dedupByContent(uriDeduped)
            val ids = deduped.map { it.id }.toSet()
            _uiState.update { it.copy(dedupedIds = ids) }
        }
    }

    /** Background dimension resolution, batched to avoid excessive recomposition. */
    private fun resolveDimensionsInBackground(images: List<ImageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = repository.resolveDimensions(images)
            // Update in chunks so the UI streams results progressively
            val chunk = 40
            for (i in resolved.indices step chunk) {
                val end = (i + chunk).coerceAtMost(resolved.size)
                val partial = resolved.subList(0, end).associateBy { it.id }
                _uiState.update { st ->
                    st.copy(images = st.images.map { partial[it.id] ?: it })
                }
                if (end < resolved.size) {
                    kotlinx.coroutines.delay(50)
                }
            }
        }
    }

    // ------------------------------------------------------------------ navigation / filters

    fun selectTab(tab: GalleryTab) = _uiState.update { it.copy(currentTab = tab) }

    /** Select a home filter: null = All, HomeFilter.FAVORITES, or a root folder id. */
    fun selectFilter(id: Long?) =
        _uiState.update { it.copy(currentFilter = id, currentSubFolderId = null) }

    /** Drill into a first-level subfolder of the current root folder. */
    fun selectSubFolder(subId: Long) =
        _uiState.update { it.copy(currentSubFolderId = subId) }

    /** Toggle single-column layout (top-bar button), persisted. */
    fun toggleSingleColumn() {
        val newVal = !_uiState.value.singleColumn
        prefs.edit().putBoolean(KEY_SINGLE_COLUMN, newVal).apply()
        _uiState.update { it.copy(singleColumn = newVal) }
    }

    /** Set default portrait grid columns (Settings picker), persisted. */
    fun setPortraitColumns(count: Int) {
        val c = count.coerceIn(2, 4)
        prefs.edit().putInt(KEY_PORTRAIT_COLUMNS, c).apply()
        _uiState.update { it.copy(portraitColumns = c) }
    }

    /** Set default landscape grid columns (Settings picker), persisted. */
    fun setLandscapeColumns(count: Int) {
        val c = count.coerceIn(2, 4)
        prefs.edit().putInt(KEY_LANDSCAPE_COLUMNS, c).apply()
        _uiState.update { it.copy(landscapeColumns = c) }
    }

    /** Toggle HD thumbnails (FR-8), persisted. */
    fun toggleHdThumbnails() {
        val newVal = !_uiState.value.hdThumbnails
        prefs.edit().putBoolean(KEY_HD_THUMBNAILS, newVal).apply()
        _uiState.update { it.copy(hdThumbnails = newVal) }
    }

    /** Set search media-type filter (null = all). */
    fun setMediaTypeFilter(type: String?) =
        _uiState.update { it.copy(mediaTypeFilter = type) }

    /** Set home grid sort mode (persisted). */
    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT_MODE, mode.name).apply()
        _uiState.update { it.copy(sortMode = mode) }
    }

    // ------------------------------------------------------------------ viewer

    /** Open the viewer at [index] within the currently selected subfolder. */
    fun openViewer(index: Int) {
        _uiState.update {
            it.copy(
                viewer = ViewerState(
                    isOpen = true,
                    index = index,
                    subFolderId = it.currentSubFolderId
                )
            )
        }
    }

    fun closeViewer() = _uiState.update { it.copy(viewer = ViewerState()) }

    /**
     * Navigate by [delta] (±1). At the first/last item of the current
     * subfolder, cross into the adjacent subfolder: forward → next
     * subfolder's first item; backward → previous subfolder's last item.
     * At the very first/last subfolder of the pack, stop (no wrapping).
     * Non-subfolder views simply clamp at the ends (no wrapping).
     */
    fun navigateViewer(delta: Int) {
        _uiState.update { st ->
            val images = viewerImages(st)
            if (images.isEmpty()) return@update st

            val newIndex = st.viewer.index + delta
            if (newIndex in images.indices) {
                return@update st.copy(viewer = st.viewer.copy(index = newIndex))
            }

            // Boundary reached: attempt to cross to adjacent subfolder.
            val subId = st.viewer.subFolderId ?: return@update st // root view: clamp
            val subs = orderedSubs(st)
            val pos = subs.indexOfFirst { it.id == subId }
            if (pos < 0) return@update st

            val targetPos = pos + delta
            if (targetPos !in subs.indices) return@update st // no wrapping

            val targetSub = subs[targetPos]
            // Keep the same sort order as the grid in the target subfolder.
            val targetImages = applySort(
                st.images.filter { it.subFolderUri == targetSub.uriString },
                st.sortMode
            )
            if (targetImages.isEmpty()) return@update st

            val targetIndex = if (delta > 0) 0 else targetImages.lastIndex
            st.copy(
                currentSubFolderId = targetSub.id,
                viewer = st.viewer.copy(
                    index = targetIndex,
                    subFolderId = targetSub.id
                )
            )
        }
    }

    // ------------------------------------------------------------------ favorites

    fun toggleFavorite(imageId: Long) {
        val newSet = _favorites.value.let { favs ->
            if (imageId in favs) favs - imageId else favs + imageId
        }
        _favorites.value = newSet
        saveFavorites(newSet)
    }

    private fun loadFavorites(): Set<Long> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    private fun saveFavorites(set: Set<Long>) {
        prefs.edit().putStringSet(KEY_FAVORITES, set.map { it.toString() }.toSet()).apply()
    }

    // ------------------------------------------------------------------ derived

    /** Images visible under the current tab + filter, sorted by the active mode.
     *  Dedup applies ONLY to the All view; folder/subfolder views keep full set. */
    fun visibleImages(state: GalleryUiState = _uiState.value): List<ImageItem> {
        var list = state.images
        when (state.currentFilter) {
            null -> list = list.filter { it.id in state.dedupedIds } // All: deduped
            HomeFilter.FAVORITES -> list = list.filter { it.id in _favorites.value }
            else -> {
                list = if (state.currentSubFolderId != null) {
                    // Match by stable subfolder URI — ids drift between scans
                    // when folder contents change, which broke the filter.
                    val activeSub = state.folders
                        .flatMap { it.subFolders }
                        .find { it.id == state.currentSubFolderId }
                    if (activeSub != null) {
                        list.filter { it.subFolderUri == activeSub.uriString }
                    } else {
                        list.filter { it.subFolderId == state.currentSubFolderId }
                    }
                } else {
                    list.filter { it.folderId == state.currentFilter }
                }
            }
        }
        return applySort(list, state.sortMode)
    }

    /**
     * Full-screen browse list: ONLY the subfolder currently being viewed
     * (thumbnails strip shows just this subfolder), ordered by the SAME
     * sort mode as the Home grid so viewer order matches the grid.
     */
    fun viewerImages(state: GalleryUiState = _uiState.value): List<ImageItem> {
        val subId = state.viewer.subFolderId ?: return visibleImages(state)
        val activeSub = state.folders
            .flatMap { it.subFolders }
            .find { it.id == subId }
        val filtered = if (activeSub != null) {
            state.images.filter { it.subFolderUri == activeSub.uriString }
        } else {
            state.images.filter { it.subFolderId == subId }
        }
        return applySort(filtered, state.sortMode)
    }

    /** Ordered subfolders of the current root folder (non-empty only). */
    private fun orderedSubs(state: GalleryUiState): List<com.flowgallery.app.data.model.SubFolder> {
        val rootFolder = state.folders.find { it.id == state.currentFilter } ?: return emptyList()
        return rootFolder.subFolders.filter { it.imageCount > 0 }
    }

    private fun applySort(list: List<ImageItem>, mode: SortMode): List<ImageItem> = when (mode) {
        SortMode.DEFAULT -> list
        SortMode.LATEST -> list.sortedByDescending { it.modifiedTime }
        SortMode.OLDEST -> list.sortedBy { it.modifiedTime }
        SortMode.LARGEST -> list.sortedByDescending { it.sizeBytes }
        SortMode.SMALLEST -> list.sortedBy { it.sizeBytes }
        SortMode.QUALITY -> list.sortedByDescending {
            when {
                it.type.isVideo -> 0
                it.isHd -> 2
                else -> 1
            }
        }
    }

    private val prefs get() = getApplication<Application>().getSharedPreferences("flowgallery", android.content.Context.MODE_PRIVATE)

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_PORTRAIT_COLUMNS = "portrait_columns"
        const val KEY_LANDSCAPE_COLUMNS = "landscape_columns"
        const val KEY_SINGLE_COLUMN = "single_column"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_HD_THUMBNAILS = "hd_thumbnails"
    }
}
