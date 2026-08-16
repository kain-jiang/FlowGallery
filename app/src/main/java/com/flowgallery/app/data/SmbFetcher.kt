package com.flowgallery.app.data

import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.flowgallery.app.data.model.SmbConfig
import java.net.URLDecoder

/**
 * Wrapper type for an SMB URL. Coil maps plain `String` to `Uri` before
 * fetching (UriFetcher then fails on smb://), so we pass this type instead
 * to guarantee [SmbFetcher] is used.
 */
data class SmbUri(val value: String)

/**
 * Coil request model for a media item: SMB URLs are wrapped in [SmbUri]
 * (bypasses Coil's String→Uri mapper), everything else stays a String so
 * the default fetchers handle it.
 */
fun smbModel(uriString: String): Any =
    if (uriString.startsWith("smb://")) SmbUri(uriString) else uriString

/**
 * Coil cache keyer for [SmbUri]: the URL is the stable identity, so Coil's
 * disk cache can reuse downloaded thumbnails instead of re-fetching the
 * share on every scroll.
 */
class SmbUriKeyer : coil.key.Keyer<SmbUri> {
    override fun key(data: SmbUri, options: Options): String? = data.value
}

/**
 * Coil Fetcher for [SmbUri] (smb:// shares). The URL has credentials
 * embedded; it is resolved to a config for reading.
 */
class SmbFetcher(
    private val uri: SmbUri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = uri.value
        android.util.Log.d("SmbFetcher", "fetch: ${url.take(80)}")
        val mime = mimeFromUrl(url)
        // VIDEOS: return the stream directly — SmartVideoFrameDecoder reads
        // frames via SmbMediaDataSource on demand, so no full download (videos
        // are large; downloading them for a thumbnail is wasteful and hogs
        // connections).
        if (mime?.startsWith("video/") == true) {
            val stream = openSmbStream(url)
            val imageSource = SmbImageSourceFactory.create(stream, options.context)
            return SourceResult(imageSource, mime, DataSource.DISK)
        }
        // IMAGES: download once to a PERSISTENT cache file (filesDir, survives
        // app restarts — Coil's own disk cache didn't kick in for SMB). The
        // cache key is the URL hash; existing files are served directly.
        val cacheDir = java.io.File(options.context.filesDir, "smb_cache").apply { mkdirs() }
        val cacheFile = java.io.File(cacheDir, url.hashCode().toUInt().toString(16))
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            android.util.Log.d("SmbFetcher", "cache hit: ${url.take(50)}")
            return SourceResult(
                SmbImageSourceFactory.create(cacheFile),
                mime ?: "image/*",
                DataSource.DISK
            )
        }
        // Limit concurrent downloads (server connection cap + jcifs-ng
        // transport contention); 1 = fully serial, safest.
        downloadSemaphore.acquire()
        try {
            val stream = openSmbStream(url)
            try {
                stream.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                val imageSource = SmbImageSourceFactory.create(cacheFile)
                return SourceResult(imageSource, mime ?: "image/*", DataSource.DISK)
            } catch (e: Exception) {
                android.util.Log.e("SmbFetcher", "fetch failed: $url", e)
                cacheFile.delete()
                throw e
            }
        } finally {
            downloadSemaphore.release()
        }
    }

    private suspend fun openSmbStream(fullUrl: String): java.io.InputStream {
        // Parse the credentials out of the URL to build a config, then read.
        val config = configFromUrl(fullUrl)
        val cleanUrl = config.url
        val file = jcifs.smb.SmbFile(cleanUrl, smbContext(config))
        return file.inputStream
    }

    class Factory : Fetcher.Factory<SmbUri> {
        override fun create(
            data: SmbUri,
            options: Options,
            imageLoader: coil.ImageLoader
        ): Fetcher? {
            android.util.Log.d("SmbFetcher", "Factory.create: data=${data.value.take(60)}")
            if (!data.value.startsWith("smb://")) return null
            return SmbFetcher(data, options)
        }
    }

    companion object {
        /** Cap concurrent SMB downloads (server connection limit + jcifs-ng
         *  transport contention). 1 = fully serial, safest for Windows SMB
         *  servers with a 20-connection cap. */
        private val downloadSemaphore = java.util.concurrent.Semaphore(1)

        private fun smbContext(config: SmbConfig): jcifs.CIFSContext =
            SmbContexts.context(config)

        /** Rebuild a SmbConfig from an smb:// URL with embedded credentials. */
        fun configFromUrl(fullUrl: String): SmbConfig {
            var rest = fullUrl.removePrefix("smb://")
            var user = ""
            var pass = ""
            var domain = ""
            val atIdx = rest.lastIndexOf('@')
            if (atIdx > 0) {
                val cred = rest.substring(0, atIdx)
                rest = rest.substring(atIdx + 1)
                val colon = cred.lastIndexOf(':')
                val semicolon = cred.indexOf(';')
                if (semicolon >= 0) {
                    domain = URLDecoder.decode(cred.substring(0, semicolon), "UTF-8")
                    user = URLDecoder.decode(cred.substring(semicolon + 1, colon), "UTF-8")
                } else if (colon >= 0) {
                    user = URLDecoder.decode(cred.substring(0, colon), "UTF-8")
                }
                if (colon >= 0) {
                    pass = URLDecoder.decode(cred.substring(colon + 1), "UTF-8")
                }
            }
            // rest = host/share/path (raw names, not URL-encoded)
            val slash = rest.indexOf('/')
            val host = if (slash >= 0) rest.substring(0, slash) else rest
            val restPath = if (slash >= 0) rest.substring(slash + 1) else ""
            val parts = restPath.split('/').filter { it.isNotEmpty() }
            val share = parts.firstOrNull() ?: ""
            val path = parts.drop(1).joinToString("/")
            return SmbConfig(host, share, path, user, pass, domain)
        }

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
                name.endsWith(".mp4") || name.endsWith(".mkv") ||
                    name.endsWith(".webm") || name.endsWith(".mov") ||
                    name.endsWith(".avi") || name.endsWith(".3gp") ||
                    name.endsWith(".flv") || name.endsWith(".ts") ||
                    name.endsWith(".m4v") || name.endsWith(".wmv") ||
                    name.endsWith(".rmvb") || name.endsWith(".mpg") ||
                    name.endsWith(".mpeg") || name.endsWith(".rm") -> "video/mp4"
                else -> null
            }
        }
    }
}
