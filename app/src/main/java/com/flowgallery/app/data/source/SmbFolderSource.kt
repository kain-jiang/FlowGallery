package com.flowgallery.app.data.source

import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.data.model.ImageItem
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB network share backend (jcifs-ng). The folder's uriString is a full
 * smb:// URL with embedded credentials; item uriStrings are smb:// file
 * URLs under it (also with credentials, inherited from the share).
 */
class SmbFolderSource : FolderSource {

    override val type: SourceType = SourceType.SMB
    override val needsConfig: Boolean = true

    override suspend fun listFiles(
        folder: Folder,
        onProgress: ((done: Int, total: Int) -> Unit)?
    ): List<ScanEntry> = withContext(Dispatchers.IO) {
        val config = SmbConfig.fromUrl(folder.uriString) ?: return@withContext emptyList()
        val out = mutableListOf<ScanEntry>()
        val counter = intArrayOf(0)

        fun collect(dirUrl: String, depth: Int, subUri: String?, subName: String?) {
            val dir = runCatching { SmbFile(dirUrl, SmbContexts.context(config)) }
                .getOrNull() ?: return
            val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in entries) {
                if (f.isDirectory) {
                    // First level under the share root becomes the subfolder
                    // tag (for PACK grouping).
                    val sUri = if (depth == 0) f.path else subUri
                    val sName = if (depth == 0) f.name else subName
                    collect(f.path, depth + 1, sUri, sName)
                } else {
                    out.add(
                        ScanEntry(
                            name = f.name,
                            isDirectory = false,
                            uriString = f.path,
                            size = f.length(),
                            modified = f.lastModified,
                            subFolderUri = subUri,
                            subFolderName = subName
                        )
                    )
                    counter[0]++
                    if (counter[0] % 20 == 0) onProgress?.invoke(counter[0], -1)
                }
            }
        }

        collect(config.url, 0, null, null)
        onProgress?.invoke(counter[0], counter[0])
        out
    }

    override fun openStream(item: ImageItem): java.io.InputStream? = runCatching {
        val config = SmbConfig.fromUrl(item.uriString) ?: return null
        SmbFile(item.uriString, SmbContexts.context(config)).inputStream
    }.getOrNull()

    override suspend fun testConnection(config: Any?): Result<Unit> = withContext(Dispatchers.IO) {
        val smb = config as? SmbConfig
            ?: return@withContext Result.failure(IllegalArgumentException("missing SMB config"))
        runCatching {
            SmbFile(smb.url, SmbContexts.context(smb)).listFiles()
        }.map { }
    }
}
