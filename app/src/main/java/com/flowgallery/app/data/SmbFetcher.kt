package com.flowgallery.app.data

import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.flowgallery.app.data.model.SmbConfig
import java.net.URLDecoder

/**
 * Coil Fetcher for `smb://` URLs. The request data is the SMB URL with
 * credentials embedded (built from [SmbConfig]); the URL alone can resolve
 * the connection config for reading.
 */
class SmbFetcher(
    private val url: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val stream = openSmbStream(url)
        // SourceImageSource is internal and the Kotlin top-level ImageSource
        // factory clashes with the class name — the Java helper wraps the
        // InputStream into a Coil ImageSource (okio handled in Java).
        val imageSource = SmbImageSourceFactory.create(stream, options.context)
        // Determine MIME from the file extension.
        val mime = mimeFromUrl(url)
        return SourceResult(imageSource, mime, DataSource.DISK)
    }

    private suspend fun openSmbStream(fullUrl: String): java.io.InputStream {
        // Parse the credentials out of the URL to build a config, then read.
        val config = configFromUrl(fullUrl)
        val cleanUrl = config.url
        val file = jcifs.smb.SmbFile(cleanUrl, smbContext(config))
        return file.inputStream
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: coil.ImageLoader
        ): Fetcher? {
            if (!data.startsWith("smb://")) return null
            return SmbFetcher(data, options)
        }
    }

    companion object {
        private fun smbContext(config: SmbConfig): jcifs.CIFSContext {
            val base = jcifs.context.BaseContext(
                jcifs.config.PropertyConfiguration(java.util.Properties())
            )
            return if (config.username.isNotEmpty()) {
                base.withCredentials(
                    jcifs.smb.NtlmPasswordAuthenticator(
                        config.domain, config.username, config.password
                    )
                )
            } else base
        }

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
            // rest = host/share/path
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
                else -> null
            }
        }
    }
}
