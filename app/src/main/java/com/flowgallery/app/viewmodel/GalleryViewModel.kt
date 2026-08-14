package com.flowgallery.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.GalleryTab
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
    val currentFolderId: Long? = null,          // null = "All"
    val viewer: ViewerState = ViewerState(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImageRepository(app)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        refreshFolders()
    }

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

    /** Remove a folder from the library entirely. */
    fun removeFolder(id: Long) {
        viewModelScope.launch {
            val folders = _uiState.value.folders.filterNot { it.id == id }
            repository.saveFolders(folders)
            _uiState.update { it.copy(folders = folders) }
            rescan()
        }
    }

    /** Rescan all selected folders (pull-to-refresh / after folder changes). */
    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val selected = _uiState.value.folders.filter { it.isSelected }
            val result = runCatching { repository.scanAll(selected) }
            result.onSuccess { images ->
                val counts = images.groupBy { it.folderId }.mapValues { it.value.size }
                repository.updateFolderCounts(counts)
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

    fun selectTab(tab: GalleryTab) = _uiState.update { it.copy(currentTab = tab) }

    fun selectFolder(id: Long?) = _uiState.update { it.copy(currentFolderId = id) }

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

    /** Images visible under the current tab + folder filter. */
    fun visibleImages(state: GalleryUiState = _uiState.value): List<ImageItem> {
        var list = state.images
        state.currentFolderId?.let { fid ->
            list = list.filter { it.folderId == fid }
        }
        return list
    }

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    fun toggleFavorite(imageId: Long) {
        _favorites.update { favs ->
            if (imageId in favs) favs - imageId else favs + imageId
        }
    }
}
