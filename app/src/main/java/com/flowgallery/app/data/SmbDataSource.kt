package com.flowgallery.app.data

import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.flowgallery.app.data.source.SmbContexts
import com.flowgallery.app.data.source.SmbCredentialStore
import jcifs.smb.SmbFile

/**
 * Media3 DataSource that streams an smb:// URL through jcifs-ng — used by
 * ExoPlayer so videos play directly from the share (no download).
 */
class SmbDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var file: SmbFile? = null
    private var input: java.io.InputStream? = null
    private var currentUri: android.net.Uri? = null

    override fun getUri(): android.net.Uri? = currentUri

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        currentUri = dataSpec.uri
        val config = SmbCredentialStore.configFor(url) ?: throw IllegalStateException("bad smb url")
        val f = SmbFile(url, SmbContexts.context(config))
        val stream = f.inputStream
        if (dataSpec.position > 0) {
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val n = stream.skip(dataSpec.position - skipped)
                if (n <= 0L) break
                skipped += n
            }
        }
        file = f
        input = stream
        return f.length() - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        input?.read(buffer, offset, length) ?: -1

    override fun close() {
        runCatching { input?.close() }
        input = null
        file = null
    }
}
