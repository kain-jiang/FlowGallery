package com.flowgallery.app.data.source

import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem

/**
 * One entry returned by [FolderSource.listFiles] — a raw directory listing
 * item (no content IO). The repository turns these into [ImageItem]s.
 */
data class ScanEntry(
    val name: String,
    val isDirectory: Boolean,
    /** stable identifier of this entry within the source (document uri for
     *  LOCAL, smb:// URL for SMB, …) — the indexing/dedup key */
    val uriString: String,
    val size: Long = 0L,
    val modified: Long = 0L,
    val mime: String? = null,
    /** first-level subfolder this entry lives under (null = folder root);
     *  used to build PACK subfolder groups */
    val subFolderUri: String? = null,
    val subFolderName: String? = null
)

/**
 * Abstraction over where folders live. LOCAL is SAF; SMB/FTP/SFTP/WebDAV are
 * future network backends. The rest of the app (scanning, indexing, viewers)
 * talks to folders ONLY through this interface, so a new source is just a
 * new implementation + registry entry.
 */
interface FolderSource {

    val type: SourceType

    /** Whether this source needs a connection config beyond a plain uri. */
    val needsConfig: Boolean
        get() = type != SourceType.LOCAL

    /**
     * List the media entries under [folder] (recursively, including
     * subdirectories). Zero content IO — sizes/mimes come from the listing.
     * @param onProgress optional (done, total) reporting during the listing
     */
    suspend fun listFiles(
        folder: Folder,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): List<ScanEntry>

    /** Open a readable stream for [item] (dimension probing, hashing, …).
     *  Returns null when the file cannot be read. */
    fun openStream(item: ImageItem): java.io.InputStream?

    /** Validate a connection config for this source (test button in the
     *  add-folder UI). Default: always OK for LOCAL. */
    suspend fun testConnection(config: Any?): Result<Unit> = Result.success(Unit)
}
