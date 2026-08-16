package com.flowgallery.app.data

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import com.flowgallery.app.data.model.SmbConfig
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared jcifs-ng configuration for all SMB access (scan, image fetcher,
 * video data source, connection test).
 *
 * Android-friendly settings:
 * - short timeouts so a bad host fails in seconds instead of hanging
 * - DNS-only resolution (NetBIOS/WINS lookups can block for minutes)
 * - SMB2/3 only (SMB1 negotiation is slow/insecure and rarely needed)
 *
 * IMPORTANT: contexts are CACHED per share (host/share/user). Creating a
 * fresh BaseContext per operation opens a NEW connection each time, which
 * quickly exhausts the server's connection limit ("No more connections
 * can be made to this remote computer…"). jcifs-ng contexts pool their
 * transports, so reusing the same context reuses the connection.
 */
object SmbContexts {

    private val cache = ConcurrentHashMap<String, CIFSContext>()

    private fun baseContext(): CIFSContext {
        val props = Properties().apply {
            // Generous timeouts: thumbnails are fully downloaded before
            // decode, and large images can take a while over slow networks.
            setProperty("jcifs.smb.client.responseTimeout", "60000")
            setProperty("jcifs.smb.client.connTimeout", "20000")
            setProperty("jcifs.smb.client.soTimeout", "120000")
            setProperty("jcifs.smb.client.sessionTimeout", "60000")
            setProperty("jcifs.resolveOrder", "DNS")
            setProperty("jcifs.smb.client.disableSMB1", "true")
            // Large read/write buffers: fewer round-trips per byte = much
            // faster large-file transfers (the default is small and slow).
            setProperty("jcifs.smb.client.readBufferSize", "131072")
            setProperty("jcifs.smb.client.writeBufferSize", "131072")
        }
        return BaseContext(PropertyConfiguration(props))
    }

    /** Context with the share's credentials applied (anonymous when empty).
     *  Cached per share so connections are reused across operations. */
    fun context(config: SmbConfig): CIFSContext {
        val key = "${config.username}@${config.host}/${config.share}/${config.path}"
        return cache.getOrPut(key) {
            val base = baseContext()
            if (config.username.isNotEmpty()) {
                base.withCredentials(
                    NtlmPasswordAuthenticator(
                        config.domain, config.username, config.password
                    )
                )
            } else {
                base
            }
        }
    }
}
