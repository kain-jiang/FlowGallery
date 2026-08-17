package com.flowgallery.app.data

import android.media.MediaDataSource
import com.flowgallery.app.data.source.SmbContexts
import com.flowgallery.app.data.source.SmbCredentialStore
import jcifs.smb.SmbFile

/**
 * Bridges jcifs-ng to Android's [MediaDataSource] so MediaMetadataRetriever
 * can extract video frames straight from an SMB share — reads are served on
 * demand (seek + read), no full download of the video.
 */
class SmbMediaDataSource(private val fileUrl: String) : MediaDataSource() {

    private val config = SmbCredentialStore.configFor(fileUrl)
    private val smbFile = config?.let { SmbFile(fileUrl, SmbContexts.context(it)) }
    private var stream = smbFile?.inputStream
    private var streamPos = 0L

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        synchronized(this) {
            try {
                var s = stream ?: return -1
                if (position < streamPos) {
                    // Backward seek: reopen the stream — and use the NEW
                    // stream from here on (reading the closed one throws
                    // "Bad file descriptor").
                    s.close()
                    s = smbFile?.inputStream ?: return -1
                    stream = s
                    streamPos = 0L
                }
                if (position > streamPos) {
                    var skipped = 0L
                    while (skipped < position - streamPos) {
                        val n = s.skip(position - streamPos - skipped)
                        if (n <= 0L) {
                            if (s.read() == -1) return -1
                            skipped += 1
                        } else skipped += n
                    }
                    streamPos = position
                }
                val n = s.read(buffer, offset, size)
                if (n > 0) streamPos += n
                return n
            } catch (e: Exception) {
                android.util.Log.e("SmbMediaDS", "readAt($position) failed", e)
                return -1
            }
        }
    }

    override fun getSize(): Long = runCatching {
        val sz = smbFile?.length() ?: -1L
        android.util.Log.d("SmbMediaDS", "getSize=$sz")
        sz
    }.getOrDefault(-1L)

    override fun close() {
        runCatching { stream?.close() }
    }
}
