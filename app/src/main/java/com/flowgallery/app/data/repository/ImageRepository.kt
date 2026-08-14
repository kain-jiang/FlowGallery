package com.flowgallery.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's selected folders (via SAF URIs) and scans them for images.
 */
class ImageRepository(context: Context) {

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
                Folder(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    uriString = o.getString("uri"),
                    imageCount = o.optInt("count", 0),
                    isSelected = o.optBoolean("selected", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<Folder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("uri", f.uriString)
                    .put("count", f.imageCount)
                    .put("selected", f.isSelected)
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

    fun updateFolderCounts(counts: Map<Long, Int>) {
        val folders = loadFolders().map { f ->
            counts[f.id]?.let { f.copy(imageCount = it) } ?: f
        }
        saveFolders(folders)
    }

    // ------------------------------------------------------------------ images

    /**
     * Scan the documents tree referenced by a folder's SAF uri.
     * Only reads image MIME documents, one level deep (typical "图包" layout).
     */
    suspend fun scanFolder(folder: Folder): List<ImageItem> = withContext(Dispatchers.IO) {
        val rootUri = Uri.parse(folder.uriString) ?: return@withContext emptyList()
        val docs = mutableListOf<Pair<Uri, String>>() // uri to display name

        val treeChildren = resolver.getChildDocuments(rootUri) ?: emptyList()
        for ((childUri, childName) in treeChildren) {
            if (isImageName(childName)) {
                docs.add(childUri to childName)
            }
        }
        // recurse one level for nested image packs
        for ((childUri, childName) in treeChildren) {
            if (isImageName(childName)) continue
            val nested = resolver.getChildDocuments(childUri) ?: continue
            for ((grandUri, grandName) in nested) {
                if (isImageName(grandName)) docs.add(grandUri to grandName)
            }
        }

        var nextId = folder.id * 1_000_000L
        docs.map { (uri, name) ->
            val (w, h) = resolver.queryDimensions(uri)
            ImageItem(
                id = nextId++,
                folderId = folder.id,
                folderName = folder.name,
                name = name,
                uriString = uri.toString(),
                width = w,
                height = h
            )
        }
    }

    /** Scan every selected folder and merge results (used by the "All" view). */
    suspend fun scanAll(folders: List<Folder>): List<ImageItem> = withContext(Dispatchers.IO) {
        val merged = mutableListOf<ImageItem>()
        for (folder in folders) {
            merged += scanFolder(folder)
        }
        merged
    }

    // ------------------------------------------------------------------ helpers

    private fun android.content.ContentResolver.getChildDocuments(
        parentUri: Uri
    ): List<Pair<Uri, String>>? {
        return try {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri, android.provider.DocumentsContract.getTreeDocumentId(parentUri)
            )
            val list = mutableListOf<Pair<Uri, String>>()
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
                    if (mime.startsWith("image/") || isImageName(name)) {
                        list.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(
                            parentUri, docId) to name)
                    }
                }
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    private fun android.content.ContentResolver.queryDimensions(uri: Uri): Pair<Int, Int> {
        // MediaStore WIDTH/HEIGHT columns don't work for SAF document URIs,
        // so decode the image header instead (fast, reads only the bounds).
        return try {
            val opts = android.graphics.BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            resolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    opts.outWidth to opts.outHeight
                } else 0 to 0
            } ?: (0 to 0)
        } catch (e: Exception) {
            (0 to 0)
        }
    }

    private fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
            n.endsWith(".heic") || n.endsWith(".avif")
    }

    private companion object {
        const val KEY_FOLDERS = "folders"
    }
}
