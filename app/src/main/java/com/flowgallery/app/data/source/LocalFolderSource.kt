package com.flowgallery.app.data.source

import android.content.Context
import android.net.Uri
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF (Storage Access Framework) implementation: folders are document tree
 * URIs, files are listed via DocumentsContract and read through the
 * content resolver.
 */
class LocalFolderSource(private val context: Context) : FolderSource {

    override val type: SourceType = SourceType.LOCAL

    private val resolver = context.applicationContext.contentResolver

    override suspend fun listFiles(
        folder: Folder,
        onProgress: ((done: Int, total: Int) -> Unit)?
    ): List<ScanEntry> = withContext(Dispatchers.IO) {
        val rootUri = Uri.parse(folder.uriString) ?: return@withContext emptyList()
        val out = mutableListOf<ScanEntry>()
        val counter = intArrayOf(0)

        fun collect(dirUri: Uri, depth: Int, subUri: String?, subName: String?) {
            val children = getChildDocuments(dirUri) ?: return
            for ((childUri, name, mime, size, modified) in children) {
                if (isDirectory(childUri)) {
                    // First level under the root becomes the subfolder tag.
                    val sUri = if (depth == 0) childUri.toString() else subUri
                    val sName = if (depth == 0) name else subName
                    collect(childUri, depth + 1, sUri, sName)
                } else {
                    out.add(
                        ScanEntry(
                            name = name,
                            isDirectory = false,
                            uriString = childUri.toString(),
                            size = size,
                            modified = modified,
                            mime = mime,
                            subFolderUri = subUri,
                            subFolderName = subName
                        )
                    )
                    counter[0]++
                    if (counter[0] % 20 == 0) onProgress?.invoke(counter[0], -1)
                }
            }
        }

        collect(rootUri, 0, null, null)
        onProgress?.invoke(counter[0], counter[0])
        out
    }

    override fun openStream(item: ImageItem): java.io.InputStream? = try {
        resolver.openInputStream(Uri.parse(item.uriString))
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ helpers

    private fun isDirectory(uri: Uri): Boolean = try {
        val docId = try {
            android.provider.DocumentsContract.getDocumentId(uri)
        } catch (e: IllegalArgumentException) {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        }
        resolver.query(
            uri,
            arrayOf(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val mime = c.getString(0)
                mime == null || mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
            } else false
        } ?: false
    } catch (e: Exception) {
        false
    }

    private fun getChildDocuments(parentUri: Uri): List<ChildEntry>? {
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
            val list = mutableListOf<ChildEntry>()
            resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
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
                val modCol = c.getColumnIndex(
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val docIdChild = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "unknown"
                    val mime = c.getString(mimeCol) ?: ""
                    val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L
                    list.add(
                        ChildEntry(
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, docIdChild),
                            name, mime, size, modified
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    private data class ChildEntry(
        val uri: Uri,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long
    )
}
