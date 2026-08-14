package com.flowgallery.app.data.model

/**
 * A browsable image folder selected by the user via Storage Access Framework.
 */
data class Folder(
    val id: Long,
    val name: String,
    val uriString: String,
    val imageCount: Int = 0,
    val isSelected: Boolean = true,
    val subFolders: List<SubFolder> = emptyList()
)

/**
 * A first-level subfolder inside a root [Folder]; its count aggregates
 * all nested images recursively (FR-2.1).
 */
data class SubFolder(
    val id: Long,
    val name: String,
    val uriString: String,
    val imageCount: Int = 0
)

/** Media type classification (FR-10). */
enum class MediaType {
    STATIC_IMAGE,
    ANIMATED_GIF,
    ANIMATED_WEBP,
    VIDEO;

    val isAnimated: Boolean get() = this == ANIMATED_GIF || this == ANIMATED_WEBP
    val isVideo: Boolean get() = this == VIDEO
}

/**
 * A single media item discovered inside a [Folder].
 *
 * When content-dedup runs, the kept item carries its duplicate copies in
 * [duplicates] so the viewer can show every file path/name of the same media.
 */
data class ImageItem(
    val id: Long,
    val folderId: Long,
    val folderName: String,
    val subFolderId: Long? = null,
    val subFolderName: String? = null,
    val name: String,
    val uriString: String,
    val type: MediaType = MediaType.STATIC_IMAGE,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val durationMs: Long? = null,
    val isFavorite: Boolean = false,
    val contentHash: String? = null,
    val duplicates: List<ImageItem> = emptyList()
) {
    val isHd: Boolean get() = width >= 1920 || height >= 1080

    /** Aspect ratio used by the waterfall layout; guard against corrupt metadata. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/** UI state for the full-screen viewer. */
data class ViewerState(
    val isOpen: Boolean = false,
    val index: Int = 0
)

/** Bottom navigation destinations. */
enum class GalleryTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Settings("Settings")
}

/** Special home-tab filters that are not real folders. */
object HomeFilter {
    const val ALL: Long = -1L
    const val FAVORITES: Long = -2L
}
