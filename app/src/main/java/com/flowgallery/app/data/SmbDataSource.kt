package com.flowgallery.app.data

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.flowgallery.app.data.model.SmbConfig
import java.io.EOFException
import java.io.InputStream

/**
 * Media3 DataSource that streams `smb://` URLs through jcifs-ng, enabling
 * in-app video playback straight from SMB shares (no local download).
 */
class SmbDataSource : BaseDataSource(false) {

    private var input: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        if (!url.startsWith("smb://")) {
            throw IllegalArgumentException("Not an smb:// URL: $url")
        }
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val config = SmbFetcher.configFromUrl(url)
        val file = jcifs.smb.SmbFile(config.url, smbContext(config))
        val stream = file.inputStream

        // Seek support: drain the stream up to the requested position.
        if (dataSpec.position > 0L) {
            val skip = dataSpec.position
            var skipped = 0L
            while (skipped < skip) {
                val n = stream.skip(skip - skipped)
                if (n <= 0L) {
                    if (stream.read() == -1) throw EOFException()
                    skipped += 1
                } else {
                    skipped += n
                }
            }
        }

        input = stream
        bytesRemaining = if (dataSpec.length == C_LENGTH_UNSET) {
            file.length() - dataSpec.position
        } else {
            dataSpec.length
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val inStream = input ?: return C_RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) return C_RESULT_END_OF_INPUT
        val readLen = if (bytesRemaining == C_LENGTH_UNSET) length
        else length.toLong().coerceAtMost(bytesRemaining).toInt()
        val n = inStream.read(buffer, offset, readLen)
        if (n == -1) return C_RESULT_END_OF_INPUT
        if (bytesRemaining != C_LENGTH_UNSET) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { input?.close() }
        input = null
    }

    private fun smbContext(config: SmbConfig): jcifs.CIFSContext {
        val base = jcifs.context.BaseContext(jcifs.config.PropertyConfiguration(java.util.Properties()))
        return if (config.username.isNotEmpty()) {
            base.withCredentials(
                jcifs.smb.NtlmPasswordAuthenticator(
                    config.domain, config.username, config.password
                )
            )
        } else base
    }

    companion object {
        /** Length unknown (-1). */
        private const val C_LENGTH_UNSET: Long = -1L
        /** Read returned end of input. */
        private const val C_RESULT_END_OF_INPUT = -1
    }
}

/** Media3 factory wiring smb:// URLs to [SmbDataSource]. */
class SmbDataSourceFactory : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource = SmbDataSource()
}
