package com.flowgallery.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.FolderType
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

    /** Folder backend registry (LOCAL now; SMB/FTP/SFTP/WebDAV later). */
    val sourceRegistry = com.flowgallery.app.data.source.SourceRegistry(context)

    // ------------------------------------------------------- scan cache

    private val scanCacheFile: java.io.File
        get() = java.io.File(context.filesDir, "scan_cache.json")

    /** Persisted dedup result (kept URIs) — survives restarts so the "All"
     *  view is correct IMMEDIATELY on cold start (P0: no empty flash while
     *  the background dedup pass re-runs). Keyed by URI because item ids
     *  drift between scans. */
    private val dedupUrisFile: java.io.File
        get() = java.io.File(context.filesDir, "dedup_uris.json")

    fun saveDedupUris(uris: Set<String>) {
        runCatching {
            val arr = org.json.JSONArray(uris.toList())
            dedupUrisFile.writeText(arr.toString())
            android.util.Log.d("DedupCache", "saved ${uris.size} kept uris")
        }.onFailure { e ->
            android.util.Log.e("DedupCache", "save failed", e)
        }
    }

    fun loadDedupUris(): Set<String> {
        return try {
            if (!dedupUrisFile.exists()) emptySet()
            else {
                val arr = org.json.JSONArray(dedupUrisFile.readText())
                buildSet {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DedupCache", "load failed", e)
            emptySet()
        }
    }

    /**
     * Persist the last scan result so the app can show content instantly on
     * startup, then refresh in the background (avoids the "reloads every
     * time you open the app" problem on large packs).
     */
    fun saveScanCache(items: List<ImageItem>) {
        runCatching {
            val arr = org.json.JSONArray()
            for (it in items) {
                arr.put(org.json.JSONObject().apply {
                    put("id", it.id)
                    put("folderId", it.folderId)
                    put("folderName", it.folderName)
                    put("subFolderId", it.subFolderId)
                    put("subFolderName", it.subFolderName)
                    put("subFolderUri", it.subFolderUri)
                    put("name", it.name)
                    put("uriString", it.uriString)
                    put("type", it.type.name)
                    put("width", it.width)
                    put("height", it.height)
                    put("sizeBytes", it.sizeBytes)
                    put("modifiedTime", it.modifiedTime)
                    put("durationMs", it.durationMs)
                    put("contentHash", it.contentHash)
                })
            }
            scanCacheFile.writeText(arr.toString())
            android.util.Log.d("ScanCache", "saved ${items.size} items, " +
                "withDim=${items.count { it.width > 0 }}, file=${scanCacheFile.absolutePath}")
        }.onFailure { e ->
            android.util.Log.e("ScanCache", "save failed", e)
        }
    }

    /** Load the cached scan (empty list if none / corrupt). */
    fun loadScanCache(): List<ImageItem> {
        return try {
            if (!scanCacheFile.exists()) {
                android.util.Log.d("ScanCache", "no cache file yet")
                emptyList()
            } else {
                val arr = org.json.JSONArray(scanCacheFile.readText())
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            ImageItem(
                                id = o.getLong("id"),
                                folderId = o.getLong("folderId"),
                                folderName = o.optString("folderName"),
                                subFolderId = if (o.isNull("subFolderId")) null else o.optLong("subFolderId"),
                                subFolderName = if (o.isNull("subFolderName")) null else o.optString("subFolderName"),
                                subFolderUri = if (o.isNull("subFolderUri")) null else o.optString("subFolderUri"),
                                name = o.optString("name"),
                                uriString = o.optString("uriString"),
                                type = runCatching { MediaType.valueOf(o.optString("type")) }
                                    .getOrDefault(MediaType.STATIC_IMAGE),
                                width = o.optInt("width"),
                                height = o.optInt("height"),
                                sizeBytes = o.optLong("sizeBytes"),
                                modifiedTime = o.optLong("modifiedTime"),
                                durationMs = if (o.isNull("durationMs")) null else o.optLong("durationMs"),
                                contentHash = if (o.isNull("contentHash")) null else o.optString("contentHash")
                            )
                        )
                    }
                }
                android.util.Log.d("ScanCache", "loaded ${list.size} items, withDim=${list.count { it.width > 0 }}")
                // SECURITY MIGRATION (P0): a legacy cache may still hold
                // smb:// URLs WITH embedded credentials — strip them so the
                // index keys line up with the new credential-free scans.
                val sanitized = list.map { it.copy(uriString = stripSmbCreds(it.uriString)) }
                    .map {
                        if (it.subFolderUri != null) it.copy(subFolderUri = stripSmbCreds(it.subFolderUri)) else it
                    }
                // Sanitize half-baked entries: a (w>0, h==0) or (w==0, h>0)
                // dimension is garbage from a broken extract and must not be
                // shown — zero both so the UI falls back to unknown.
                sanitized.map {
                    if (it.width > 0 && it.height > 0) it
                    else if (it.width > 0 || it.height > 0) it.copy(width = 0, height = 0)
                    else it
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanCache", "load failed", e)
            emptyList()
        }
    }

    // ------------------------------------------------------------------ folders

    /** Load the cached folder list from prefs. */
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
                    source = o.optString("source").takeIf { it.isNotBlank() }?.let { s ->
                        runCatching { com.flowgallery.app.data.source.SourceType.valueOf(s) }.getOrNull()
                    } ?: com.flowgallery.app.data.source.SourceType.LOCAL,
                    type = o.optString("type").takeIf { it.isNotBlank() }?.let { t ->
                        runCatching { FolderType.valueOf(t) }.getOrNull()
                    } ?: FolderType.NORMAL,
                    imageCount = o.optInt("count", 0),
                    isSelected = o.optBoolean("selected", true),
                    subFolders = subList
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        // SECURITY MIGRATION (P0): legacy SMB folders persisted their full
        // smb:// URL WITH credentials. Strip the credentials into the
        // encrypted store and persist the clean URL — one-time per folder.
        var migrated = false
        val sanitized = folders.map { f ->
            if (f.source == com.flowgallery.app.data.source.SourceType.SMB &&
                f.uriString.contains('@')
            ) {
                val cfg = com.flowgallery.app.data.source.SmbConfig.fromUrl(f.uriString)
                if (cfg != null) {
                    com.flowgallery.app.data.source.SmbCredentialStore.save(cfg)
                    migrated = true
                    f.copy(uriString = cfg.urlNoCreds)
                } else f
            } else f
        }
        if (migrated) saveFolders(sanitized)
        // Self-heal: drop entries whose tree is covered by another entry
        // (e.g. a subfolder also added as a separate library item), which
        // would otherwise duplicate media in the "All" view.
        return pruneOverlaps(sanitized)
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
            val fid = ids.firstOrNull { it.first == folder.id }?.second
            // Non-SAF sources (SMB shares, …) have no tree id and can't be
            // overlap-pruned — keep them as-is.
            if (fid == null) {
                result.add(folder)
                continue
            }
            val coveredByParent = ids.any { (otherId, otherFid) ->
                otherId != folder.id &&
                    otherFid != null &&
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
                    .put("source", f.source.name)
                    .put("type", f.type.name)
                    .put("count", f.imageCount)
                    .put("selected", f.isSelected)
                    .put("subs", subs)
            )
        }
        prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply()
    }

    /** Add a folder with an explicit type, dedup by URI. */
    fun addFolder(uri: Uri, displayName: String, type: FolderType): Boolean {
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
        folders.add(Folder(id = nextId, name = displayName, uriString = uri.toString(), type = type))
        saveFolders(folders)
        return true
    }

    /** Add an SMB share folder (source = SMB), dedup by smb url. The stored
     *  uriString is CREDENTIAL-FREE; the password goes to the encrypted
     *  [SmbCredentialStore] instead (never in URLs / index keys / caches). */
    fun addSmbFolder(config: com.flowgallery.app.data.source.SmbConfig, displayName: String?, type: FolderType): Boolean {
        val folders = loadFolders().toMutableList()
        val url = config.urlNoCreds
        if (folders.any { it.uriString == url }) return false
        val name = displayName?.takeIf { it.isNotBlank() } ?: "${config.host}/${config.share}"
        val nextId = (folders.maxOfOrNull { it.id } ?: 0L) + 1
        folders.add(
            Folder(
                id = nextId,
                name = name,
                uriString = url,
                source = com.flowgallery.app.data.source.SourceType.SMB,
                type = type
            )
        )
        saveFolders(folders)
        com.flowgallery.app.data.source.SmbCredentialStore.save(config)
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

    /** Strip embedded SMB credentials from a legacy URL (P0 migration). */
    private fun stripSmbCreds(uri: String): String {
        if (!uri.startsWith("smb://") || !uri.contains('@')) return uri
        return com.flowgallery.app.data.source.SmbConfig.fromUrl(uri)?.urlNoCreds ?: uri
    }

    /**
     * Lightweight check used to RECOMMEND a folder type: returns true when the
     * folder tree contains at least one subdirectory (→ suggest PACK).
     */
    fun hasSubDirectories(uri: Uri): Boolean {
        return try {
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(uri)
            } catch (e: IllegalArgumentException) {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            }
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                uri, docId
            )
            resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                val nameCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                var found = false
                while (c.moveToNext() && !found) {
                    val name = c.getString(nameCol) ?: ""
                    val mime = c.getString(mimeCol) ?: ""
                    found = !isImageName(name) && !isVideoName(name) &&
                        mime.startsWith("vnd.android.document/directory")
                }
                found
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Remove a folder from the library entirely (FR-2): releases its SAF
     * persistable permission AND drops its SMB credentials (if any).
     * Returns the removed folder.
     */
    fun removeFolder(id: Long): Folder? {
        val folders = loadFolders().toMutableList()
        val removed = folders.find { it.id == id } ?: return null
        folders.removeIf { it.id == id }
        saveFolders(folders)
        runCatching {
            if (removed.source == com.flowgallery.app.data.source.SourceType.SMB) {
                val cfg = com.flowgallery.app.data.source.SmbCredentialStore.configFor(removed.uriString)
                if (cfg != null) {
                    com.flowgallery.app.data.source.SmbCredentialStore.delete(cfg.host, cfg.share)
                }
            } else {
                val uri = Uri.parse(removed.uriString)
                resolver.releasePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
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
     * Scan a folder through its [FolderSource]: zero-IO listing, then build
     * ImageItems. PACK folders group first-level subfolders as browsable
     * sub-entries; NORMAL folders collect recursively without tagging.
     */
    suspend fun scanFolder(folder: Folder): FolderScanResult = withContext(Dispatchers.IO) {
        val source = sourceRegistry.get(folder.source)
        val entries = runCatching { source.listFiles(folder) }.getOrDefault(emptyList())

        val allItems = mutableListOf<ImageItem>()
        var nextId = folder.id * 1_000_000L
        val isPack = folder.type == FolderType.PACK
        val subGroups = LinkedHashMap<String, Pair<SubFolder, MutableList<ImageItem>>>()

        for (e in entries) {
            if (!isImageName(e.name) && !isVideoName(e.name)) continue
            val subId = e.subFolderUri?.hashCode()?.toLong()
            val item = ImageItem(
                id = nextId++,
                folderId = folder.id,
                folderName = folder.name,
                source = folder.source,
                subFolderId = subId,
                subFolderUri = e.subFolderUri,
                subFolderName = e.subFolderName,
                name = e.name,
                uriString = e.uriString,
                type = classify(e.name),
                width = 0,
                height = 0,
                sizeBytes = e.size,
                modifiedTime = e.modified
            )
            if (isPack && e.subFolderUri != null && subId != null) {
                subGroups.getOrPut(e.subFolderUri) {
                    SubFolder(
                        id = subId,
                        name = e.subFolderName ?: e.subFolderUri,
                        uriString = e.subFolderUri,
                        imageCount = 0
                    ) to mutableListOf()
                }.second.add(item)
            }
            allItems.add(item)
        }

        // Build first-level subfolder summaries — skip empty subfolders (FR-2.1)
        val subs = subGroups.values
            .map { it.first.copy(imageCount = it.second.size) }
            .filter { it.imageCount > 0 }
        FolderScanResult(folder.id, allItems, subs)
    }

    /** Scan every selected folder and merge results (used by the "All" view). */
    suspend fun scanAll(folders: List<Folder>): List<FolderScanResult> = withContext(Dispatchers.IO) {
        folders.map { scanFolder(it) }
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
                // Prefer the indexed hash (no IO); fall back to reading via
                // the item's source.
                val hash = item.contentHash ?: hashViaSource(item)
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

    /** MD5 of the file contents via the item's source, or null on failure. */
    private fun hashViaSource(item: ImageItem): String? = try {
        val md = java.security.MessageDigest.getInstance("MD5")
        sourceRegistry.get(item.source).openStream(item)?.use { input ->
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
