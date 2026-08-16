package com.flowgallery.app.data.source

import android.content.Context

/**
 * Registry of folder source implementations. Adding a new backend (SMB,
 * FTP, SFTP, WebDAV…) = implement [FolderSource], register it here, and
 * the whole app picks it up (scanning, indexing, viewers).
 */
class SourceRegistry(context: Context) {

    private val sources: Map<SourceType, FolderSource> = buildMap {
        put(SourceType.LOCAL, LocalFolderSource(context))
        put(SourceType.SMB, SmbFolderSource())
        // Future backends register here, e.g.:
        // put(SourceType.FTP, FtpFolderSource(context))
    }

    fun get(type: SourceType): FolderSource =
        sources[type] ?: sources.getValue(SourceType.LOCAL) // safe fallback
}
