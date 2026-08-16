package com.flowgallery.app.data

import android.media.MediaDataSource
import jcifs.smb.SmbFile

/**
 * Bridges jcifs-ng to Android's [MediaDataSource] so MediaMetadataRetriever
 * can extract video frames straight from an SMB share — reads are served on
 * demand (seek + read), so NO full download of the video is needed.
 */
class SmbMediaDataSource(private val fileUrl: String) : MediaDataSource() {

    private val config = SmbFetcher.configFromUrl(fileUrl)
    private val smbFile = SmbFile(config.url, SmbContexts.context(config))
    private var stream = smbFile.inputStream
    private var streamPos = 0L

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        synchronized(this) {
            try {
                if (position < streamPos) {
                    // Backward seek: reopen the stream.
                    stream.close()
                    stream = smbFile.inputStream
                    streamPos = 0L
                }
                if (position > streamPos) {
                    val skip = position - streamPos
                    var skipped = 0L
                    while (skipped < skip) {
                        val n = stream.skip(skip - skipped)
                        if (n <= 0L) {
                            if (stream.read() == -1) return -1
                            skipped += 1
                        } else {
                            skipped += n
                        }
                    }
                    streamPos = position
                }
                val n = stream.read(buffer, offset, size)
                if (n > 0) streamPos += n
                return n
            } catch (e: Exception) {
                android.util.Log.e("SmbMediaDS", "readAt($position) failed", e)
                return -1
            }
        }
    }

    override fun getSize(): Long = runCatching { smbFile.length() }
        .onFailure { android.util.Log.e("SmbMediaDS", "getSize failed", it) }
        .getOrDefault(-1L)

    override fun close() {
        runCatching { stream.close() }
    }
}
