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
class MediaIndexer(private val context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /**
     * Merge [items] with [existing]; returns the updated index map and the
     * number of entries that were freshly extracted (for progress reporting).
     *
     * @param force re-extract every file even if size/mtime are unchanged
     * @param onProgress invoked as files are processed: (done, total)
     */
    suspend fun merge(
        items: List<ImageItem>,
        existing: Map<String, IndexEntry>,
        force: Boolean = false,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Pair<Map<String, IndexEntry>, Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val result = existing.toMutableMap()
        var extracted = 0
        val total = items.size
        items.forEachIndexed { index, item ->
            val cached = existing[item.uriString]
            if (!force &&
                cached != null &&
                cached.sizeBytes == item.sizeBytes &&
                cached.modifiedTime == item.modifiedTime
            ) {
                // unchanged — reuse without touching the file
            } else {
                val entry = extract(item)
                result[item.uriString] = IndexEntry(
                    uriString = item.uriString,
                    width = entry.width,
                    height = entry.height,
                    durationMs = entry.durationMs,
                    sizeBytes = item.sizeBytes,
                    modifiedTime = item.modifiedTime,
                    contentHash = entry.contentHash,
                    indexedAt = now
                )
                extracted++
            }
            if (index % 10 == 0 || index == total - 1) {
                onProgress?.invoke(index + 1, total)
            }
        }
        result to extracted
    }

    /** Extract metadata for one item (dimensions, duration, hash). */
    private suspend fun extract(item: ImageItem): IndexEntry = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uriString)
        if (item.type.isVideo) {
            // Videos: resolution + duration via MediaMetadataRetriever (one
            // pass); BitmapFactory can't decode video dimensions.
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    IndexEntry(
                        uriString = item.uriString,
                        width = w,
                        height = h,
                        durationMs = d,
                        contentHash = contentHash(uri)
                    )
                } finally {
                    runCatching { mmr.release() }
                }
            } catch (e: Exception) {
                IndexEntry(
                    uriString = item.uriString,
                    contentHash = contentHash(uri)
                )
            }
        } else {
            val (w, h) = dimensionOf(uri)
            val hash = contentHash(uri)
            IndexEntry(
                uriString = item.uriString,
                width = w,
                height = h,
                durationMs = null,
                contentHash = hash
            )
        }
    }

    /** Bitmap bounds only (no full decode). */
    private fun dimensionOf(uri: Uri): Pair<Int, Int> = try {
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

    /** MD5 of the file contents, or null on failure. Used for content dedup. */
    private fun contentHash(uri: Uri): String? = try {
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
