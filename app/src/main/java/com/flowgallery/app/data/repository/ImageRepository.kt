package com.flowgallery.app.data.repository

import android.content.Context
import android.content.SharedPreferences
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
        val folders = try {
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
        // Self-heal: drop entries whose tree is covered by another entry
        // (e.g. a subfolder also added as a separate library item), which
        // would otherwise duplicate media in the "All" view.
        return pruneOverlaps(folders)
    }

    /**
     * Remove folders that are already covered by a parent folder entry.
     * Keeps the parent (higher in the tree), drops the redundant child.
     */
    private fun pruneOverlaps(folders: List<Folder>): List<Folder> {
        if (folders.size < 2) return folders
        val ids = folders.map { it.id to treeDocumentId(Uri.parse(it.uriString)) }
        val result = mutableListOf<Folder>()
        for (folder in folders) {
            val fid = ids.firstOrNull { it.first == folder.id }?.second ?: continue
            val coveredByParent = ids.any { (otherId, otherFid) ->
                otherId != folder.id &&
                    otherFid != null &&
                    fid != null &&
                    (fid.startsWith("$otherFid/") || fid == otherFid)
            }
            if (!coveredByParent) {
                result.add(folder)
            }
        }
        // Persist cleanup so the store stays tidy.
        if (result.size != folders.size) {
            saveFolders(result)
        }
        return result
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

        // Reject folders whose tree is already covered by an existing entry —
        // e.g. adding a subfolder of an already-added folder (or vice versa)
        // would duplicate its media in the "All" view (FR-2).
        val newId = treeDocumentId(uri)
        if (newId != null) {
            val covered = folders.any { existing ->
                val existingId = treeDocumentId(Uri.parse(existing.uriString))
                existingId != null &&
                    (newId == existingId ||
                        newId.startsWith("$existingId/") ||
                        existingId.startsWith("$newId/"))
            }
            if (covered) return false
        }

        val nextId = (folders.maxOfOrNull { it.id } ?: 0L) + 1
        folders.add(Folder(id = nextId, name = displayName, uriString = uri.toString()))
        saveFolders(folders)
        return true
    }

    /** Extract the SAF tree document id (e.g. "primary:DCIM/Test") or null. */
    private fun treeDocumentId(uri: Uri): String? {
        return try {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        }
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
            for ((childUri, childName, mime, size) in children) {
                if (isImageName(childName) || isVideoName(childName)) {
                    // No content IO during scan — classify by extension only
                    // (dimensions are filled in lazily by the UI via Coil).
                    out.add(
                        ImageItem(
                            id = nextId++,
                            folderId = folder.id,
                            folderName = folder.name,
                            subFolderId = subFolderId,
                            subFolderName = subFolderName,
                            name = childName,
                            uriString = childUri.toString(),
                            type = classify(childName),
                            width = 0,
                            height = 0,
                            sizeBytes = size
                        )
                    )
                } else {
                    // Not a media file — attempt recursion; if it's a plain
                    // file (not a directory) getChildDocuments returns null
                    // and we simply skip it. MIME alone is unreliable here.
                    collectRecursive(childUri, subFolderId, subFolderName, out)
                }
            }
        }

        // Root pass: direct files + first-level subfolders
        val rootChildren = resolver.getChildDocuments(rootUri) ?: emptyList()
        val subGroups = LinkedHashMap<String, Pair<SubFolder, List<ImageItem>>>()
        for ((childUri, childName, mime, size) in rootChildren) {
            if (isImageName(childName) || isVideoName(childName)) {
                allItems.add(
                    ImageItem(
                        id = nextId++,
                        folderId = folder.id,
                        folderName = folder.name,
                        name = childName,
                        uriString = childUri.toString(),
                        type = classify(childName),
                        width = 0,
                        height = 0,
                        sizeBytes = size
                    )
                )
            } else {
                // first-level subfolder: recursive collect with tag.
                // IMPORTANT: the SubFolder must reuse the SAME id that the
                // items were tagged with, otherwise tab filtering never matches.
                val subId = nextId++
                val subItems = mutableListOf<ImageItem>()
                collectRecursive(childUri, subId, childName, subItems)
                if (subItems.isNotEmpty() || resolver.isDirectory(childUri)) {
                    val subName = resolver.displayNameOf(childUri) ?: childName
                    subGroups[childUri.toString()] =
                        SubFolder(id = subId, name = subName, uriString = childUri.toString(), imageCount = subItems.size) to subItems
                    allItems.addAll(subItems)
                }
            }
        }

        // Build first-level subfolder summaries — skip empty subfolders (FR-2.1)
        val subs = subGroups.values
            .map { it.first }
            .filter { it.imageCount > 0 }
        FolderScanResult(folder.id, allItems, subs)
    }

    /** Scan every selected folder and merge results (used by the "All" view). */
    suspend fun scanAll(folders: List<Folder>): List<FolderScanResult> = withContext(Dispatchers.IO) {
        folders.map { scanFolder(it) }
    }

    /**
     * Lazily resolve real dimensions for items that were scanned with zero IO.
     * Images: BitmapFactory bounds decode (fast, header only). Videos are
     * skipped — their resolution is not needed for HD/SD badges.
     */
    suspend fun resolveDimensions(items: List<ImageItem>): List<ImageItem> =
        withContext(Dispatchers.IO) {
            items.map { item ->
                if (item.width > 0 || item.type == MediaType.VIDEO) {
                    item
                } else {
                    val (w, h) = resolver.dimensionOf(Uri.parse(item.uriString))
                    if (w > 0 && h > 0) item.copy(width = w, height = h) else item
                }
            }
        }

    /**
     * Content-level dedup: same file size + same content hash ⇒ duplicate.
     * Only items sharing a size with another item get hashed, so unique
     * files are never read. The kept item carries its duplicate copies in
     * `duplicates` (for the viewer's "all file paths" display).
     */
    suspend fun dedupByContent(items: List<ImageItem>): List<ImageItem> =
        withContext(Dispatchers.IO) {
            // Group by size; only sizes appearing more than once need hashing.
            val sizeCounts = items.groupingBy { it.sizeBytes }.eachCount()
            val candidates = items.filter { (sizeCounts[it.sizeBytes] ?: 0) > 1 }

            val hashById = mutableMapOf<Long, String>()
            for (item in candidates) {
                val hash = resolver.contentHash(Uri.parse(item.uriString))
                if (hash != null) hashById[item.id] = hash
            }

            // Group items by (size:hash); unique sizes form their own group.
            val groups = LinkedHashMap<String, MutableList<ImageItem>>()
            for (item in items) {
                val hash = hashById[item.id]
                val key = if (hash != null) "${item.sizeBytes}:$hash" else "uniq:${item.id}"
                groups.getOrPut(key) { mutableListOf() }.add(item)
            }

            val kept = mutableListOf<ImageItem>()
            for (group in groups.values) {
                val first = group.first()
                val hash = hashById[first.id]
                kept.add(
                    if (group.size > 1) {
                        first.copy(
                            contentHash = hash,
                            duplicates = group.drop(1).map { it.copy(contentHash = hash) }
                        )
                    } else first
                )
            }
            kept
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
    ): List<Quad>? {
        return try {
            // Subfolders are document URIs, the root is a tree URI —
            // getDocumentId works for both (getTreeDocumentId only for trees).
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(parentUri)
            } catch (e: IllegalArgumentException) {
                android.provider.DocumentsContract.getTreeDocumentId(parentUri)
            }
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri, docId
            )
            val list = mutableListOf<Quad>()
            resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(
                    android.provider.DocumentsContract.Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val docIdChild = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "unknown"
                    val mime = c.getString(mimeCol) ?: ""
                    val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                    list.add(
                        Quad(
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, docIdChild),
                            name,
                            mime,
                            size
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    /** (uri, displayName, mimeType, sizeBytes) */
    private data class Quad(
        val uri: Uri,
        val name: String,
        val mime: String,
        val size: Long
    )

    private fun android.content.ContentResolver.isDirectory(uri: Uri): Boolean {
        return try {
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(uri)
            } catch (e: IllegalArgumentException) {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            }
            resolver.query(
                android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId),
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    (c.getString(0) ?: "").startsWith("vnd.android.document/directory")
                } else false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Decode image bounds without loading pixels (fast, header only). */
    private fun android.content.ContentResolver.dimensionOf(uri: Uri): Pair<Int, Int> {
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

    /** MD5 of the file contents, or null on failure. Used for content dedup. */
    private fun android.content.ContentResolver.contentHash(uri: Uri): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            resolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
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
     * Classify media type by file extension only — zero content IO.
     * Dimensions are resolved lazily by the UI (Coil) when thumbnails load.
     */
    private fun classify(name: String): MediaType {
        val lower = name.lowercase()
        return when {
            isVideoName(name) -> MediaType.VIDEO
            lower.endsWith(".gif") -> MediaType.ANIMATED_GIF
            lower.endsWith(".webp") -> MediaType.ANIMATED_WEBP
            else -> MediaType.STATIC_IMAGE
        }
    }

    private fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
            n.endsWith(".heic") || n.endsWith(".avif")
    }

    private fun isVideoName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
            n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".3gp") ||
            n.endsWith(".flv") || n.endsWith(".ts") || n.endsWith(".m4v") ||
            n.endsWith(".wmv") || n.endsWith(".rmvb") || n.endsWith(".mpg") ||
            n.endsWith(".mpeg") || n.endsWith(".rm")
    }

    private companion object {
        const val KEY_FOLDERS = "folders"
    }
}
