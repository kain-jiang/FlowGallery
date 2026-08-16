package com.flowgallery.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowgallery.app.data.index.IndexEntry
import com.flowgallery.app.data.index.IndexStore
import com.flowgallery.app.data.index.MediaIndexer
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
    /** one-shot user notice (shown as a toast, then cleared) */
    val indexNotice: String? = null,
    /** Default portrait grid columns (2/3/4), set in Settings (FR-1). */
    val portraitColumns: Int = 2,
    /** Default landscape grid columns (2/3/4), set in Settings (FR-1). */
    val landscapeColumns: Int = 3,
    /** Force single-column layout (top-bar toggle, overrides both). */
    val singleColumn: Boolean = false,
    /** Favorites tab's own single-column toggle (independent of Home). */
    val favoritesSingleColumn: Boolean = false,
    /** home grid sort mode */
    val sortMode: SortMode = SortMode.DEFAULT,
    /** HD thumbnail toggle (FR-8) */
    val hdThumbnails: Boolean = true,
    /** Monet (Material You) dynamic color from wallpaper (Android 12+) */
    val monetColors: Boolean = false,
    /** Floating pills (stats + tap-to-top) anchored left instead of right */
    val pillAlignmentLeft: Boolean = false,
    /** type filter for search: null = all, else MediaType name */
    val mediaTypeFilter: String? = null
)

/** State of the manual indexing job (Index tab). */
data class IndexJobState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val force: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val extracted: Int = 0,
    val lastIndexedAt: Long = 0L,
    val entryCount: Int = 0,
    /** folder ids to index; null = ALL selected folders */
    val indexFolders: Set<Long>? = null
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImageRepository(app)
    private val indexStore = IndexStore(app)
    private val mediaIndexer = MediaIndexer(app, repository.sourceRegistry)

    /** In-memory metadata index (uri → entry). Loaded at startup. */
    private var mediaIndex: Map<String, IndexEntry> = emptyMap()

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    /** Manual index job state (Index tab). */
    private val _indexJob = MutableStateFlow(IndexJobState())
    val indexJob: StateFlow<IndexJobState> = _indexJob.asStateFlow()

    /** Cancellation flag for the running manual index job. */
    private val indexCancelRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        val savedSort = prefs.getString(KEY_SORT_MODE, null)?.let { name ->
            runCatching { SortMode.valueOf(name) }.getOrNull()
        } ?: SortMode.DEFAULT
        _uiState.update {
            it.copy(
                portraitColumns = prefs.getInt(KEY_PORTRAIT_COLUMNS, 2).coerceIn(2, 4),
                landscapeColumns = prefs.getInt(KEY_LANDSCAPE_COLUMNS, 3).coerceIn(2, 6),
                singleColumn = prefs.getBoolean(KEY_SINGLE_COLUMN, false),
                favoritesSingleColumn = prefs.getBoolean(KEY_FAVORITES_SINGLE_COLUMN, false),
                sortMode = savedSort,
                hdThumbnails = prefs.getBoolean(KEY_HD_THUMBNAILS, true),
                monetColors = prefs.getBoolean(KEY_MONET_COLORS, false),
                pillAlignmentLeft = prefs.getBoolean(KEY_PILL_ALIGNMENT_LEFT, false)
            )
        }
        // Load the metadata index, then show the last cached scan immediately
        // (no empty flash / re-load wait), then refresh in the background.
        mediaIndex = indexStore.load()
        _indexJob.update {
            it.copy(
                entryCount = mediaIndex.size,
                lastIndexedAt = mediaIndex.values.maxOfOrNull { e -> e.indexedAt } ?: 0L
            )
        }
        val cached = repository.loadScanCache()
        if (cached.isNotEmpty()) {
            _uiState.update { it.copy(images = applyIndex(cached)) }
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

    /** Add an SMB share folder, then rescan. */
    fun addSmbFolder(config: com.flowgallery.app.data.source.SmbConfig, name: String?, type: FolderType) {
        viewModelScope.launch {
            val added = repository.addSmbFolder(config, name, type)
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
            val state0 = _uiState.value
            val folders = state0.folders.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            repository.saveFolders(folders)
            // If the currently-filtered folder was just deselected, fall back
            // to "All" so the home screen never gets stuck showing nothing.
            val folderNow = folders.find { it.id == id }
            val filteredOff = folderNow != null && !folderNow.isSelected
            _uiState.update {
                if (filteredOff && it.currentFilter == id) {
                    it.copy(folders = folders, currentFilter = null, currentSubFolderId = null)
                } else {
                    it.copy(folders = folders)
                }
            }
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
                // Fall back to previously-known dimensions while the index
                // heals: applyIndex() only fills COMPLETE entries, so items
                // whose index entry is broken keep their last good size
                // instead of flashing to 0.
                val oldByUri = _uiState.value.images.associateBy { it.uriString }
                val indexedImages = applyIndex(images).map { item ->
                    if (item.width > 0 && item.height > 0) item
                    else oldByUri[item.uriString]?.takeIf { it.width > 0 && it.height > 0 } ?: item
                }
                _uiState.update {
                    it.copy(
                        images = indexedImages,
                        folders = folders,
                        isRefreshing = false
                    )
                }
                // Persist so the next launch shows content instantly — with
                // the best-known dimensions (never store bare items over
                // previously saved sizes).
                repository.saveScanCache(indexedImages)
                // Diagnostic: why does the fresh scan match so few entries?
                if (images.isNotEmpty()) {
                    val sample = images.first().uriString
                    val indexKey = mediaIndex.keys.firstOrNull()
                    android.util.Log.d("IndexMatch", "scanUri=$sample")
                    android.util.Log.d("IndexMatch", "indexUri=$indexKey")
                }
                // Auto-index only when there IS new/changed content — if
                // everything is already indexed, skip it entirely.
                val missing = needsIndexing(images)
                if (missing) {
                    // Tell the user the background index is running.
                    val notice = getApplication<Application>().getString(
                        com.flowgallery.app.R.string.index_auto_notice
                    )
                    _uiState.update {
                        it.copy(indexNotice = notice)
                    }
                    indexImagesInBackground(images)
                }
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

    /**
     * Background incremental index pass: merge scan items with the persisted
     * index (only new/changed files get metadata extracted), persist, then
     * stream the enriched items into the UI.
     */
    private fun indexImagesInBackground(images: List<ImageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val (newIndex, _) = mediaIndexer.merge(images, mediaIndex)
            mediaIndex = newIndex
            indexStore.save(newIndex.values)
            val enriched = applyIndex(images)
            val enrichedById = enriched.associateBy { it.id }
            // Update in chunks so the UI streams results progressively.
            val chunk = 60
            for (i in enriched.indices step chunk) {
                val end = (i + chunk).coerceAtMost(enriched.size)
                _uiState.update { st ->
                    st.copy(images = st.images.map { enrichedById[it.id] ?: it })
                }
                if (end < enriched.size) {
                    kotlinx.coroutines.delay(30)
                }
            }
            // Persist enriched items (dimensions included) for next launch.
            repository.saveScanCache(enriched)
        }
    }

    /** Fill items with metadata from the in-memory index. */
    private fun applyIndex(items: List<ImageItem>): List<ImageItem> {
        val enriched = items.map { item ->
            val e = mediaIndex[item.uriString] ?: return@map item
            // Only apply COMPLETE dimensions (w>0 && h>0); a partial entry
            // like (1000, 0) from a broken video extract would show garbage.
            if (e.width > 0 && e.height > 0) {
                item.copy(
                    width = e.width,
                    height = e.height,
                    durationMs = e.durationMs ?: item.durationMs,
                    contentHash = e.contentHash
                )
            } else item
        }
        // Diagnostic: how many items actually got dimensions from the index.
        android.util.Log.d("ApplyIndex", "items=${items.size} index=${mediaIndex.size} " +
            "withDim=${enriched.count { it.width > 0 && it.height > 0 }}")
        return enriched
    }

    // ------------------------------------------------------------- index job

    /**
     * Start a manual full index over all selected folders (Index tab).
     * [force] re-extracts every file ("re-index"); otherwise only new or
     * changed files are touched. Progress is reported via [indexJob].
     */
    fun startIndex(force: Boolean = false) {
        if (_indexJob.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            indexCancelRequested.set(false)
            // Respect the per-folder selection (null = all folders,
            // regardless of their enabled state).
            val selection = _indexJob.value.indexFolders
            val folders = _uiState.value.folders.filter {
                selection == null || it.id in selection
            }
            if (folders.isEmpty()) {
                _indexJob.update { it.copy(running = false) }
                return@launch
            }
            // File listing is cheap; metadata extraction is the slow part.
            val items = runCatching { repository.scanAll(folders) }
                .getOrElse { emptyList() }
                .flatMap { it.items }
            if (indexCancelRequested.get()) {
                _indexJob.update { it.copy(running = false, paused = false) }
                return@launch
            }
            _indexJob.update {
                it.copy(
                    running = true,
                    paused = false,
                    force = force,
                    done = 0,
                    total = items.size,
                    extracted = 0
                )
            }
            val (newIndex, extracted) = mediaIndexer.merge(
                items = items,
                existing = if (force) emptyMap() else mediaIndex,
                force = force,
                onProgress = { done, total ->
                    // Pause support (onProgress runs on the IO dispatcher —
                    // Thread.sleep is fine here).
                    while (_indexJob.value.paused) {
                        Thread.sleep(150)
                    }
                    _indexJob.update {
                        it.copy(done = done, total = total)
                    }
                },
                onCancelCheck = { indexCancelRequested.get() }
            )
            // Save whatever was indexed — including a cancelled run's partial
            // results (so a re-open keeps what was already done).
            mediaIndex = newIndex
            indexStore.save(newIndex.values)
            val cancelled = indexCancelRequested.get()
            if (cancelled) {
                _indexJob.update { it.copy(running = false, paused = false) }
                return@launch
            }
            _indexJob.update {
                it.copy(
                    running = false,
                    paused = false,
                    extracted = extracted,
                    entryCount = newIndex.size,
                    lastIndexedAt = System.currentTimeMillis()
                )
            }
            // Refresh the home grid with the enriched metadata.
            val enriched = applyIndex(_uiState.value.images)
            _uiState.update { st -> st.copy(images = enriched) }
            repository.saveScanCache(enriched)
        }
    }

    /** Toggle pause/resume of the running index job. */
    fun toggleIndexPause() {
        if (!_indexJob.value.running) return
        _indexJob.update { it.copy(paused = !it.paused) }
    }

    /** Cancel the running index job (keeps already-indexed entries). */
    fun cancelIndex() {
        if (!_indexJob.value.running) return
        indexCancelRequested.set(true)
        _indexJob.update { it.copy(running = false, paused = false) }
    }

    /**
     * Toggle a folder in/out of the index selection. null selection means
     * "all selected folders"; unchecking the first folder materializes the
     * set as "everything except it", so the semantics stay intuitive.
     */
    fun toggleIndexFolder(id: Long) {
        val all = _uiState.value.folders.filter { it.isSelected }.map { it.id }.toSet()
        val cur = _indexJob.value.indexFolders
        val effective = cur ?: all
        val newSet = if (id in effective) effective - id else effective + id
        _indexJob.update { it.copy(indexFolders = newSet) }
    }

    /** Select all (null = follow all selected folders) or none (empty set). */
    fun setAllIndexFolders(selectAll: Boolean) {
        _indexJob.update {
            it.copy(indexFolders = if (selectAll) null else emptySet())
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

    /** Toggle the Favorites tab's single-column layout, persisted. */
    fun toggleFavoritesSingleColumn() {
        val newVal = !_uiState.value.favoritesSingleColumn
        prefs.edit().putBoolean(KEY_FAVORITES_SINGLE_COLUMN, newVal).apply()
        _uiState.update { it.copy(favoritesSingleColumn = newVal) }
    }

    /** Set default portrait grid columns (Settings picker), persisted. */
    fun setPortraitColumns(count: Int) {
        val c = count.coerceIn(2, 4)
        prefs.edit().putInt(KEY_PORTRAIT_COLUMNS, c).apply()
        _uiState.update { it.copy(portraitColumns = c) }
    }

    /** Set default landscape grid columns (Settings picker), persisted.
     *  Landscape screens allow a wider range (2-6 columns). */
    fun setLandscapeColumns(count: Int) {
        val c = count.coerceIn(2, 6)
        prefs.edit().putInt(KEY_LANDSCAPE_COLUMNS, c).apply()
        _uiState.update { it.copy(landscapeColumns = c) }
    }

    /** Toggle HD thumbnails (FR-8), persisted. */
    fun toggleHdThumbnails() {
        val newVal = !_uiState.value.hdThumbnails
        prefs.edit().putBoolean(KEY_HD_THUMBNAILS, newVal).apply()
        _uiState.update { it.copy(hdThumbnails = newVal) }
    }

    /** Toggle Monet (Material You) dynamic colors, persisted. */
    fun toggleMonetColors() {
        val newVal = !_uiState.value.monetColors
        prefs.edit().putBoolean(KEY_MONET_COLORS, newVal).apply()
        _uiState.update { it.copy(monetColors = newVal) }
    }

    /** Set floating-pill alignment (left/right), persisted. */
    fun setPillAlignment(left: Boolean) {
        prefs.edit().putBoolean(KEY_PILL_ALIGNMENT_LEFT, left).apply()
        _uiState.update { it.copy(pillAlignmentLeft = left) }
    }

    /** Clear the last scan/connection error (after it was shown as a toast). */
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Clear the one-shot index notice after it was toasted. */
    fun clearIndexNotice() = _uiState.update { it.copy(indexNotice = null) }

    /** True when there is genuinely NEW content to index, or a failed entry
     *  is due for a retry. Failed extracts (zero dimensions) are kept but
     *  throttled to one retry per day — otherwise every launch would re-index
     *  and toast forever. */
    private fun needsIndexing(items: List<ImageItem>): Boolean =
        items.any { item ->
            val e = mediaIndex[item.uriString]
            when {
                e == null -> true
                e.sizeBytes != item.sizeBytes || e.modifiedTime != item.modifiedTime -> true
                // Broken entry: retry at most once a day.
                (e.width <= 0 || e.height <= 0) &&
                    System.currentTimeMillis() - e.indexedAt > 24 * 3600_000L -> true
                else -> false
            }
        }

    /** Set search media-type filter (null = all). */
    fun setMediaTypeFilter(type: String?) =
        _uiState.update { it.copy(mediaTypeFilter = type) }

    /**
     * True when the viewer can cross into the adjacent subfolder in [delta]
     * direction: the current view must be a subfolder (not All / root) and a
     * non-empty neighbour must exist. Used to show the double-arrow affordance.
     */
    fun canCrossViewer(delta: Int): Boolean {
        val st = _uiState.value
        val subId = st.viewer.subFolderId ?: return false
        val subs = orderedSubs(st)
        val pos = subs.indexOfFirst { it.id == subId }
        if (pos < 0) return false
        val targetPos = pos + delta
        if (targetPos !in subs.indices) return false
        return subs[targetPos].imageCount > 0
    }

    /** Set home grid sort mode (persisted). */
    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT_MODE, mode.name).apply()
        _uiState.update { it.copy(sortMode = mode) }
    }

    // ------------------------------------------------------------------ viewer

    /** Open the viewer at [index] within the currently selected subfolder. */
    fun openViewer(index: Int, favoritesOnly: Boolean = false) {
        _uiState.update {
            it.copy(
                viewer = ViewerState(
                    isOpen = true,
                    index = index,
                    subFolderId = it.currentSubFolderId,
                    favoritesOnly = favoritesOnly
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
        // Opened from Favorites: the browsing sequence is the favorites list
        // only — never cross into non-favorited items.
        if (state.viewer.favoritesOnly) {
            return state.images.filter { it.id in _favorites.value }
        }
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
        const val KEY_FAVORITES_SINGLE_COLUMN = "favorites_single_column"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_HD_THUMBNAILS = "hd_thumbnails"
        const val KEY_MONET_COLORS = "monet_colors"
        const val KEY_PILL_ALIGNMENT_LEFT = "pill_alignment_left"
    }
}
