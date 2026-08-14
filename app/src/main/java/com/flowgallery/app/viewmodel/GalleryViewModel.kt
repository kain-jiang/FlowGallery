package com.flowgallery.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.GalleryTab
import com.flowgallery.app.data.model.HomeFilter
import com.flowgallery.app.data.model.ImageItem
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
    val images: List<ImageItem> = emptyList(),
    val currentTab: GalleryTab = GalleryTab.Home,
    /** null = All, HomeFilter.FAVORITES = favorites, else folder id or subfolder id */
    val currentFilter: Long? = null,
    val isSubFolderFilter: Boolean = false,
    val viewer: ViewerState = ViewerState(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** true = 3 columns, false = 2 columns (FR-1 decision #3) */
    val threeColumns: Boolean = false,
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
        _uiState.update { it.copy(threeColumns = prefs.getBoolean(KEY_THREE_COLUMNS, false)) }
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

    /** Add a folder from SAF picker result, persist permission + scan it. */
    fun addFolder(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val added = repository.addFolder(uri, displayName)
            if (added) refreshFolders()
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
            val filterReset = if (!st.isSubFolderFilter && st.currentFilter == id) null else st.currentFilter
            _uiState.update {
                it.copy(folders = folders, currentFilter = filterReset)
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
                // Merge all items (deduped by folderId groups)
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
            }.onFailure { e ->
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    // ------------------------------------------------------------------ navigation / filters

    fun selectTab(tab: GalleryTab) = _uiState.update { it.copy(currentTab = tab) }

    /** Select a home filter: null = All, HomeFilter.FAVORITES, folder id, or (id, true) for subfolder. */
    fun selectFilter(id: Long?, isSubFolder: Boolean = false) =
        _uiState.update { it.copy(currentFilter = id, isSubFolderFilter = isSubFolder) }

    /** Toggle grid density 2<->3 columns (FR-1 decision #3), persisted. */
    fun toggleColumns() {
        val newVal = !_uiState.value.threeColumns
        prefs.edit().putBoolean(KEY_THREE_COLUMNS, newVal).apply()
        _uiState.update { it.copy(threeColumns = newVal) }
    }

    /** Set search media-type filter (null = all). */
    fun setMediaTypeFilter(type: String?) =
        _uiState.update { it.copy(mediaTypeFilter = type) }

    // ------------------------------------------------------------------ viewer

    fun openViewer(index: Int) = _uiState.update { it.copy(viewer = ViewerState(isOpen = true, index = index)) }

    fun closeViewer() = _uiState.update { it.copy(viewer = ViewerState()) }

    fun navigateViewer(delta: Int) {
        _uiState.update { st ->
            val count = visibleImages(st).size
            if (count == 0) return@update st
            val next = (st.viewer.index + delta).coerceIn(0, count - 1)
            st.copy(viewer = st.viewer.copy(index = next))
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

    /** Images visible under the current tab + filter. */
    fun visibleImages(state: GalleryUiState = _uiState.value): List<ImageItem> {
        var list = state.images
        when (state.currentFilter) {
            null -> {} // All
            HomeFilter.FAVORITES -> list = list.filter { it.id in _favorites.value }
            else -> list = if (state.isSubFolderFilter) {
                list.filter { it.subFolderId == state.currentFilter }
            } else {
                list.filter { it.folderId == state.currentFilter }
            }
        }
        return list
    }

    private val prefs get() = getApplication<Application>().getSharedPreferences("flowgallery", android.content.Context.MODE_PRIVATE)

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_THREE_COLUMNS = "three_columns"
    }
}
