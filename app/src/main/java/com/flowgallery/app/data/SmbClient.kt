package com.flowgallery.app.data

import com.flowgallery.app.data.model.SmbConfig
import jcifs.CIFSContext
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper over jcifs-ng for SMB share access. Each call builds a fresh
 * context (jcifs-ng contexts are stateful per-connection; cheap enough for
 * scan/read operations).
 */
object SmbClient {

    /** Create a CIFS context with the share's credentials. */
    private fun context(config: SmbConfig): CIFSContext {
        val base = BaseContext(jcifs.config.PropertyConfiguration(java.util.Properties()))
        if (config.username.isNotEmpty()) {
            val auth = NtlmPasswordAuthenticator(
                config.domain, config.username, config.password
            )
            return base.withCredentials(auth)
        }
        return base
    }

    /** List a directory; returns file entries (name, dir, size, lastModified). */
    data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    suspend fun list(config: SmbConfig, subPath: String = ""): List<Entry> =
        withContext(Dispatchers.IO) {
            try {
                val dirUrl = joinUrl(config.url, subPath)
                val file = SmbFile(dirUrl, context(config))
                file.listFiles().map { f ->
                    Entry(
                        name = f.name,
                        isDirectory = f.isDirectory,
                        size = f.length(),
                        lastModified = f.lastModified
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Open a stream to read a file's bytes (caller closes). */
    suspend fun openStream(config: SmbConfig, subPath: String) =
        withContext(Dispatchers.IO) {
            val fileUrl = joinUrl(config.url, subPath)
            SmbFile(fileUrl, context(config)).inputStream
        }

    /** Read a file's first [maxBytes] (used for dimension sniffing). */
    suspend fun readHead(config: SmbConfig, subPath: String, maxBytes: Int = 64 * 1024): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                val stream = openStream(config, subPath)
                stream.use { input ->
                    val buf = ByteArray(maxBytes)
                    val n = input.read(buf)
                    if (n <= 0) ByteArray(0) else buf.copyOf(n)
                }
            } catch (e: Exception) {
                ByteArray(0)
            }
        }

    /** Test connectivity: list the share root. Returns error message or null. */
    suspend fun test(config: SmbConfig): String? = withContext(Dispatchers.IO) {
        try {
            SmbFile(config.url, context(config)).listFiles()
            null
        } catch (e: Exception) {
            e.message ?: "SMB connection failed"
        }
    }

    private fun joinUrl(base: String, subPath: String): String {
        val clean = base.trimEnd('/')
        val sub = subPath.trim('/')
        return if (sub.isEmpty()) "$clean/" else "$clean/$sub/"
    }
}
