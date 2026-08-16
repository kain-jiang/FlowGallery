package com.flowgallery.app.data.index

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.flowgallery.app.data.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Incremental metadata indexer.
 *
 * Takes the scan result (list of [ImageItem] — file listing only, zero IO)
 * and merges it with the persisted [IndexStore] index:
 * - entries whose size+mtime are unchanged are REUSED (no IO),
 * - new or changed files get metadata extracted (dimensions, video
 *   duration, content hash).
 *
 * The merged index feeds the home screen directly: correct aspect ratios,
 * HD/SD badges, sort-by-time/size and content-dedup are available
 * immediately after a scan — no separate background dimension pass.
 */
class MediaIndexer(
    private val context: Context,
    private val sourceRegistry: com.flowgallery.app.data.source.SourceRegistry
) {
    /** Failed extracts are retried at most once per 24h (kept as FAILED
     *  markers in between, so incremental runs stay fast). */
    private companion object {
        const val RETRY_THROTTLE_MS = 24L * 60 * 60 * 1000
    }

    private val resolver = context.applicationContext.contentResolver

    /**
     * Merge [items] with [existing]; returns the updated index map, the
     * number of freshly extracted entries and the number of FAILED extracts.
     *
     * @param force re-extract every file even if size/mtime are unchanged
     * @param onProgress invoked as files are processed: (done, total)
     * @param onCancelCheck when it returns true the merge STOPS and returns
     *   the partial result (so cancelled runs still persist what they did)
     */
    suspend fun merge(
        items: List<ImageItem>,
        existing: Map<String, IndexEntry>,
        force: Boolean = false,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
        onCancelCheck: (() -> Boolean)? = null
    ): Triple<Map<String, IndexEntry>, Int, Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val result = existing.toMutableMap()
        var extracted = 0
        var failed = 0
        val total = items.size
        items.forEachIndexed { index, item ->
            if (onCancelCheck?.invoke() == true) {
                return@withContext Triple(result, extracted, failed)
            }
            val cached = existing[item.uriString]
            // Reuse only COMPLETE entries. A zero-dimension entry (failed
            // extract) would otherwise be reused forever because its
            // size/mtime match — the broken metadata never heals.
            if (!force &&
                cached?.status == IndexStatus.SUCCESS &&
                cached.width > 0 && cached.height > 0 &&
                cached.sizeBytes == item.sizeBytes &&
                cached.modifiedTime == item.modifiedTime
            ) {
                // unchanged — reuse without touching the file, but backfill
                // the folderId for entries created before it existed
                // (clear → re-index must yield correct per-folder counts).
                if (cached.folderId < 0) {
                    result[item.uriString] = cached.copy(folderId = item.folderId)
                }
            } else if (!force && cached?.status == IndexStatus.FAILED &&
                System.currentTimeMillis() - cached.indexedAt < RETRY_THROTTLE_MS
            ) {
                // Failed recently — keep the FAILED marker, skip the retry.
            } else {
                val entry = extract(item)
                val bad = entry.width <= 0 && entry.height <= 0 && entry.durationMs == null
                if (bad) {
                    failed++
                    // Keep a FAILED marker so the index record reflects the
                    // outcome (and is counted as NOT indexed).
                    result[item.uriString] = IndexEntry(
                        uriString = item.uriString,
                        folderId = item.folderId,
                        sizeBytes = item.sizeBytes,
                        modifiedTime = item.modifiedTime,
                        indexedAt = now,
                        status = IndexStatus.FAILED
                    )
                } else {
                    result[item.uriString] = IndexEntry(
                        uriString = item.uriString,
                        folderId = item.folderId,
                        width = entry.width,
                        height = entry.height,
                        durationMs = entry.durationMs,
                        sizeBytes = item.sizeBytes,
                        modifiedTime = item.modifiedTime,
                        contentHash = entry.contentHash,
                        indexedAt = now,
                        status = IndexStatus.SUCCESS
                    )
                    extracted++
                }
            }
            if (index % 10 == 0 || index == total - 1) {
                onProgress?.invoke(index + 1, total)
            }
        }
        Triple(result, extracted, failed)
    }

    /** Extract metadata for one item (dimensions, duration). NOTE: content
     *  hash is NOT computed here — hashing reads the WHOLE file and makes
     *  indexing crawl on network sources. Dedup computes hashes on demand
     *  (only for size-duplicate candidates) instead. */
    private suspend fun extract(item: ImageItem): IndexEntry = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uriString)
        if (item.type.isVideo) {
            // Videos: resolution + duration via MediaMetadataRetriever (one
            // pass); BitmapFactory can't decode video dimensions.
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    if (uri.scheme == "smb") {
                        // SMB videos: stream via SmbMediaDataSource (plain
                        // MediaMetadataRetriever can't open smb:// uris).
                        mmr.setDataSource(com.flowgallery.app.data.SmbMediaDataSource(item.uriString))
                    } else {
                        mmr.setDataSource(context, uri)
                    }
                    var w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    var h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    // Some files only expose one dimension via metadata —
                    // fall back to the decoded first frame's real size.
                    if (w <= 0 || h <= 0) {
                        val frame = runCatching {
                            mmr.getFrameAtTime(0)
                        }.getOrNull()
                        if (frame != null) {
                            if (w <= 0) w = frame.width
                            if (h <= 0) h = frame.height
                            frame.recycle()
                        }
                    }
                    IndexEntry(
                        uriString = item.uriString,
                        width = w,
                        height = h,
                        durationMs = d
                    )
                } finally {
                    runCatching { mmr.release() }
                }
            } catch (e: Exception) {
                IndexEntry(uriString = item.uriString)
            }
        } else {
            val (w, h) = dimensionOf(item)
            IndexEntry(
                uriString = item.uriString,
                width = w,
                height = h,
                durationMs = null
            )
        }
    }

    /** Bitmap bounds only (no full decode). Reads via the item's source. */
    private fun dimensionOf(item: ImageItem): Pair<Int, Int> = try {
        val opts = android.graphics.BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        sourceRegistry.get(item.source).openStream(item)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                opts.outWidth to opts.outHeight
            } else 0 to 0
        } ?: (0 to 0)
    } catch (e: Exception) {
        (0 to 0)
    }
}
