package com.flowgallery.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem
import com.flowgallery.app.data.model.MediaType
import com.flowgallery.app.data.model.SubFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's selected folders (via SAF URIs), scans them recursively
 * for images/videos, and releases permissions on removal (FR-2 / FR-2.1 / FR-10).
 */
class ImageRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("flowgallery", Context.MODE_PRIVATE)

    private val resolver = context.applicationContext.contentResolver

    // ------------------------------------------------------------------ folders

    fun loadFolders(): List<Folder> {
        val raw = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val subs = o.optJSONArray("subs")
                val subList = if (subs == null) emptyList() else {
                    (0 until subs.length()).map { j ->
                        val s = subs.getJSONObject(j)
                        SubFolder(
                            id = s.getLong("id"),
                            name = s.getString("name"),
                            uriString = s.getString("uri"),
                            imageCount = s.optInt("count", 0)
                        )
                    }
                }
                Folder(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    uriString = o.getString("uri"),
                    imageCount = o.optInt("count", 0),
                    isSelected = o.optBoolean("selected", true),
                    subFolders = subList
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<Folder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            val subs = JSONArray()
            f.subFolders.forEach { s ->
                subs.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("uri", s.uriString)
                        .put("count", s.imageCount)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("uri", f.uriString)
                    .put("count", f.imageCount)
                    .put("selected", f.isSelected)
                    .put("subs", subs)
            )
        }
        prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply()
    }

    /** Add a folder, dedup by URI. Returns true if it was actually added. */
    fun addFolder(uri: Uri, displayName: String): Boolean {
        val folders = loadFolders().toMutableList()
        if (folders.any { it.uriString == uri.toString() }) return false
        val nextId = (folders.maxOfOrNull { it.id } ?: 0L) + 1
        folders.add(Folder(id = nextId, name = displayName, uriString = uri.toString()))
        saveFolders(folders)
        return true
    }

    /**
     * Remove a folder from the library AND release its SAF persistable
     * permission (FR-2). Returns the removed folder.
     */
    fun removeFolder(id: Long): Folder? {
        val folders = loadFolders().toMutableList()
        val removed = folders.find { it.id == id } ?: return null
        folders.removeIf { it.id == id }
        saveFolders(folders)
        runCatching {
            val uri = Uri.parse(removed.uriString)
            resolver.releasePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return removed
    }

    fun updateFolderCounts(counts: Map<Long, Int>) {
        val folders = loadFolders().map { f ->
            counts[f.id]?.let { f.copy(imageCount = it) } ?: f
        }
        saveFolders(folders)
    }

    fun updateFolderSubFolders(id: Long, subs: List<SubFolder>, totalCount: Int) {
        val folders = loadFolders().map { f ->
            if (f.id == id) f.copy(subFolders = subs, imageCount = totalCount) else f
        }
        saveFolders(folders)
    }

    // ------------------------------------------------------------------ scanning

    /**
     * Recursively scan a folder tree (FR-2.1): collects all images/videos
     * from the root and every nested level, and returns the first-level
     * subfolder breakdown.
     */
    suspend fun scanFolder(folder: Folder): FolderScanResult = withContext(Dispatchers.IO) {
        val rootUri = Uri.parse(folder.uriString) ?: return@withContext FolderScanResult(folder.id, emptyList(), emptyList())
        val allItems = mutableListOf<ImageItem>()
        var nextId = folder.id * 1_000_000L

        /**
         * Recursively collect media under [dirUri]. Items get tagged with the
         * first-level subfolder they live under (null when directly in root).
         */
        fun collectRecursive(
            dirUri: Uri,
            subFolderId: Long?,
            subFolderName: String?,
            out: MutableList<ImageItem>
        ) {
            val children = resolver.getChildDocuments(dirUri) ?: return
            for ((childUri, childName, mime) in children) {
                when {
                    isImageName(childName) || isVideoName(childName) -> {
                        val (w, h, dur, type) = resolver.analyzeMedia(childUri, childName)
                        out.add(
                            ImageItem(
                                id = nextId++,
                                folderId = folder.id,
                                folderName = folder.name,
                                subFolderId = subFolderId,
                                subFolderName = subFolderName,
                                name = childName,
                                uriString = childUri.toString(),
                                type = type,
                                width = w,
                                height = h,
                                durationMs = dur
                            )
                        )
                    }
                    mime.startsWith("vnd.android.document/directory") -> {
                        // nested directory: keep the same first-level tag
                        collectRecursive(childUri, subFolderId, subFolderName, out)
                    }
                }
            }
        }

        // Root pass: direct files + first-level subfolders
        val rootChildren = resolver.getChildDocuments(rootUri) ?: emptyList()
        val subGroups = LinkedHashMap<String, MutableList<ImageItem>>()
        for ((childUri, childName, mime) in rootChildren) {
            when {
                isImageName(childName) || isVideoName(childName) -> {
                    val (w, h, dur, type) = resolver.analyzeMedia(childUri, childName)
                    allItems.add(
                        ImageItem(
                            id = nextId++,
                            folderId = folder.id,
                            folderName = folder.name,
                            name = childName,
                            uriString = childUri.toString(),
                            type = type,
                            width = w,
                            height = h,
                            durationMs = dur
                        )
                    )
                }
                mime.startsWith("vnd.android.document/directory") -> {
                    // first-level subfolder: recursive collect with tag
                    val subId = nextId++
                    val subItems = mutableListOf<ImageItem>()
                    collectRecursive(childUri, subId, childName, subItems)
                    subGroups[childUri.toString()] = subItems
                    allItems.addAll(subItems)
                }
            }
        }

        // Build first-level subfolder summaries
        val subs = subGroups.map { (uriStr, items) ->
            SubFolder(
                id = nextId++,
                name = resolver.displayNameOf(Uri.parse(uriStr)) ?: "sub",
                uriString = uriStr,
                imageCount = items.size
            )
        }
        FolderScanResult(folder.id, allItems, subs)
    }

    /** Scan every selected folder and merge results (used by the "All" view). */
    suspend fun scanAll(folders: List<Folder>): List<FolderScanResult> = withContext(Dispatchers.IO) {
        folders.map { scanFolder(it) }
    }

    // ------------------------------------------------------------------ helpers

    /** Result of scanning one root folder: flat items + subfolder summaries. */
    data class FolderScanResult(
        val folderId: Long,
        val items: List<ImageItem>,
        val subFolders: List<SubFolder>
    )

    private fun android.content.ContentResolver.getChildDocuments(
        parentUri: Uri
    ): List<Triple<Uri, String, String>>? {
        return try {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri, android.provider.DocumentsContract.getTreeDocumentId(parentUri)
            )
            val list = mutableListOf<Triple<Uri, String, String>>()
            resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    val docId = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "unknown"
                    val mime = c.getString(mimeCol) ?: ""
                    list.add(
                        Triple(
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, docId),
                            name,
                            mime
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    private fun android.content.ContentResolver.displayNameOf(uri: Uri): String? {
        return try {
            resolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Analyze a media document: dimensions, video duration, media type.
     * (FR-10: static / animated GIF / animated WebP / video)
     */
    private fun android.content.ContentResolver.analyzeMedia(
        uri: Uri,
        name: String
    ): MediaInfo {
        val lower = name.lowercase()
        return try {
            if (isVideoName(name)) {
                // video: use MediaMetadataRetriever for resolution + duration
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    MediaInfo(w, h, dur, MediaType.VIDEO)
                } finally {
                    runCatching { mmr.release() }
                }
            } else {
                // image: BitmapFactory bounds + detect animation
                val opts = android.graphics.BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                resolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input, null, opts)
                }
                val w = opts.outWidth
                val h = opts.outHeight
                val type = when {
                    lower.endsWith(".gif") -> MediaType.ANIMATED_GIF
                    lower.endsWith(".webp") -> MediaType.ANIMATED_WEBP
                    else -> MediaType.STATIC_IMAGE
                }
                MediaInfo(w, h, null, type)
            }
        } catch (e: Exception) {
            // fallback: classify by extension only
            val type = when {
                isVideoName(name) -> MediaType.VIDEO
                lower.endsWith(".gif") -> MediaType.ANIMATED_GIF
                lower.endsWith(".webp") -> MediaType.ANIMATED_WEBP
                else -> MediaType.STATIC_IMAGE
            }
            MediaInfo(0, 0, null, type)
        }
    }

    private data class MediaInfo(
        val width: Int,
        val height: Int,
        val durationMs: Long?,
        val type: MediaType
    )

    private fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
            n.endsWith(".heic") || n.endsWith(".avif")
    }

    private fun isVideoName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
            n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".3gp")
    }

    private companion object {
        const val KEY_FOLDERS = "folders"
    }
}
