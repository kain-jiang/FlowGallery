package com.flowgallery.app.data

import android.media.MediaDataSource
import com.flowgallery.app.data.source.SmbConfig
import com.flowgallery.app.data.source.SmbContexts
import jcifs.smb.SmbFile

/**
 * Bridges jcifs-ng to Android's [MediaDataSource] so MediaMetadataRetriever
 * can extract video frames straight from an SMB share — reads are served on
 * demand (seek + read), no full download of the video.
 */
class SmbMediaDataSource(private val fileUrl: String) : MediaDataSource() {

    private val config = SmbConfig.fromUrl(fileUrl)
    private val smbFile = config?.let { SmbFile(fileUrl, SmbContexts.context(it)) }
    private var stream = smbFile?.inputStream
    private var streamPos = 0L

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val s = stream ?: return -1
        synchronized(this) {
            try {
                if (position < streamPos) {
                    // Backward seek: reopen the stream.
                    s.close()
                    stream = smbFile?.inputStream
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
                return -1
            }
        }
    }

    override fun getSize(): Long = runCatching { smbFile?.length() ?: -1L }.getOrDefault(-1L)

    override fun close() {
        runCatching { stream?.close() }
    }
}
