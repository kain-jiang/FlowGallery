package com.flowgallery.app.data.index

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the media metadata index ([IndexEntry]) as JSON under
 * `filesDir/index.json`. Read/write are cheap (bounded by image count);
 * loading happens once at startup, saving after each index pass.
 */
class IndexStore(private val context: Context) {

    private val indexFile: File
        get() = File(context.filesDir, "index.json")

    /** Load the persisted index (empty map if none / corrupt). */
    fun load(): Map<String, IndexEntry> {
        return try {
            if (!indexFile.exists()) {
                android.util.Log.d("IndexStore", "no index file yet")
                emptyMap()
            } else {
                val arr = JSONArray(indexFile.readText())
                val map = buildMap {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val uri = o.optString("uri")
                        if (uri.isBlank()) continue
                        put(
                            uri,
                            IndexEntry(
                                uriString = uri,
                                folderId = if (o.isNull("f")) -1L else o.optLong("f", -1L),
                                width = o.optInt("w"),
                                height = o.optInt("h"),
                                durationMs = if (o.isNull("d")) null else o.optLong("d"),
                                sizeBytes = o.optLong("s"),
                                modifiedTime = o.optLong("m"),
                                contentHash = if (o.isNull("h")) null else o.optString("h"),
                                indexedAt = o.optLong("t")
                            )
                        )
                    }
                }
                android.util.Log.d("IndexStore", "loaded ${map.size} entries")
                map
            }
        } catch (e: Exception) {
            android.util.Log.e("IndexStore", "load failed", e)
            emptyMap()
        }
    }

    /** Persist the whole index (replaces the file). */
    fun save(entries: Collection<IndexEntry>) {
        runCatching {
            val arr = JSONArray()
            for (e in entries) {
                arr.put(
                    JSONObject().apply {
                        put("uri", e.uriString)
                        put("f", e.folderId)
                        put("w", e.width)
                        put("h", e.height)
                        put("d", e.durationMs)
                        put("s", e.sizeBytes)
                        put("m", e.modifiedTime)
                        put("h", e.contentHash)
                        put("t", e.indexedAt)
                    }
                )
            }
            indexFile.writeText(arr.toString())
            android.util.Log.d("IndexStore", "saved ${entries.size} entries, file=${indexFile.absolutePath}")
        }.onFailure { e ->
            android.util.Log.e("IndexStore", "save failed", e)
        }
    }
}
