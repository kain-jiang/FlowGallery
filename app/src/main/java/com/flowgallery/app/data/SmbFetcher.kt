package com.flowgallery.app.data

import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.flowgallery.app.data.source.SmbConfig
import com.flowgallery.app.data.source.SmbContexts
import jcifs.smb.SmbFile

/**
 * Wrapper type for an SMB URL. Coil maps plain `String` to `Uri` before
 * fetching (UriFetcher then fails on smb://), so SMB models pass this type
 * instead to guarantee [SmbFetcher] is used.
 */
data class SmbUri(val value: String)

/** Coil request model for a media item: SMB URLs get wrapped, others stay. */
fun smbModel(uriString: String): Any =
    if (uriString.startsWith("smb://")) SmbUri(uriString) else uriString

/**
 * Stable disk-cache key for [SmbUri] — without it Coil can't cache SMB
 * results and every scroll/re-open would re-download.
 */
class SmbUriKeyer : coil.key.Keyer<SmbUri> {
    override fun key(data: SmbUri, options: Options): String? = data.value
}

/**
 * Coil Fetcher for [SmbUri]: downloads the whole file to a temp file first,
 * then lets Coil decode from the local copy (stream decoding can stall on
 * slow SMB reads). Concurrency is capped to respect the server's connection
 * limit.
 */
class SmbFetcher(
    private val uri: SmbUri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = uri.value
        val config = SmbConfig.fromUrl(url) ?: throw IllegalStateException("bad smb url")
        val mime = mimeFromUrl(url)

        // VIDEOS: download to a temp file then let SmartVideoFrameDecoder
        // read frames LOCALLY — streaming seek over SMB is far too slow for
        // MediaMetadataRetriever's random access (frames never arrive).
        // Local decoding is fast; the disk cache reuses it afterwards.
        downloadSemaphore.acquire()
        try {
            val tmp = java.io.File.createTempFile("smb_", ".bin", options.context.cacheDir)
            try {
                val file = SmbFile(url, SmbContexts.context(config))
                file.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val imageSource = SmbImageSourceFactory.create(tmp)
                return SourceResult(imageSource, mime, DataSource.DISK)
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        } finally {
            downloadSemaphore.release()
        }

        // IMAGES: download fully to a temp file, then decode locally.
        downloadSemaphore.acquire()
        try {
            val tmp = java.io.File.createTempFile("smb_", ".bin", options.context.cacheDir)
            try {
                val file = SmbFile(url, SmbContexts.context(config))
                file.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val imageSource = SmbImageSourceFactory.create(tmp)
                return SourceResult(imageSource, mime ?: "image/*", DataSource.DISK)
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        } finally {
            downloadSemaphore.release()
        }
    }

    companion object {
        /** Cap concurrent SMB downloads (server connection limit). */
        private val downloadSemaphore = java.util.concurrent.Semaphore(3)

        private fun mimeFromUrl(url: String): String? {
            val name = url.substringBefore('?').substringAfterLast('/').lowercase()
            return when {
                name.endsWith(".gif") -> "image/gif"
                name.endsWith(".webp") -> "image/webp"
                name.endsWith(".png") -> "image/png"
                name.endsWith(".bmp") -> "image/bmp"
                name.endsWith(".heic") || name.endsWith(".heif") -> "image/heic"
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                name.endsWith(".avif") -> "image/avif"
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                    name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".3gp") ||
                    name.endsWith(".flv") || name.endsWith(".ts") || name.endsWith(".m4v") ||
                    name.endsWith(".wmv") || name.endsWith(".rmvb") || name.endsWith(".mpg") ||
                    name.endsWith(".mpeg") || name.endsWith(".rm") -> "video/mp4"
                else -> null
            }
        }
    }

    class Factory : Fetcher.Factory<SmbUri> {
        override fun create(
            data: SmbUri,
            options: Options,
            imageLoader: coil.ImageLoader
        ): Fetcher? {
            if (!data.value.startsWith("smb://")) return null
            return SmbFetcher(data, options)
        }
    }
}
