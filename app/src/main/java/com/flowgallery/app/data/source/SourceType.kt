package com.flowgallery.app.data.source

/** Folder backends. LOCAL = SAF documents; the others are external network
 *  sources (SMB shares, FTP, SFTP, WebDAV…) — each gets its own
 *  [FolderSource] implementation. */
enum class SourceType(val label: String) {
    LOCAL("Local"),
    SMB("SMB"),
    FTP("FTP"),
    SFTP("SFTP"),
    WEBDAV("WebDAV")
}
