package com.flowgallery.app.data.index

/**
 * Metadata of one indexed media file. Keyed by [uriString]; the store is
 * incremental — entries whose size/mtime match the last scan are reused,
 * so re-indexing only touches new or changed files.
 */
data class IndexEntry(
    val uriString: String,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long? = null,
    val sizeBytes: Long = 0L,
    val modifiedTime: Long = 0L,
    val contentHash: String? = null,
    val indexedAt: Long = 0L
)
