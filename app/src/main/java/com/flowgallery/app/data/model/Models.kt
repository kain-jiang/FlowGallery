package com.flowgallery.app.data.model

/**
 * A browsable image folder selected by the user via Storage Access Framework.
 */
data class Folder(
    val id: Long,
    val name: String,
    val uriString: String,
    val imageCount: Int = 0,
    val isSelected: Boolean = true
)

/**
 * A single image discovered inside a [Folder].
 */
data class ImageItem(
    val id: Long,
    val folderId: Long,
    val folderName: String,
    val name: String,
    val uriString: String,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false
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
