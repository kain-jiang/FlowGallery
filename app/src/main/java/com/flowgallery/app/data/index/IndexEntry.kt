package com.flowgallery.app.data.index

/**
 * Metadata of one indexed media file. Keyed by [uriString]; the store is
 * incremental — entries whose size/mtime match the last scan are reused,
 * so re-indexing only touches new or changed files.
 */
/** Outcome of a metadata-extraction attempt for one item. */
enum class IndexStatus { SUCCESS, FAILED }

/**
 * One indexed item: extracted metadata plus its status. FAILED entries are
 * kept (with a retry throttle) so the UI can distinguish "indexed" from
 * "failed" instead of dropping failures entirely.
 */
data class IndexEntry(
    val uriString: String,
    /** owning folder id — enables exact per-folder counts without fragile
     *  uri-prefix matching (SMB credential encoding, tree/document shapes) */
    val folderId: Long = -1L,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long? = null,
    val sizeBytes: Long = 0L,
    val modifiedTime: Long = 0L,
    val contentHash: String? = null,
    val indexedAt: Long = 0L,
    /** extraction outcome; legacy entries default to SUCCESS */
    val status: IndexStatus = IndexStatus.SUCCESS
)
