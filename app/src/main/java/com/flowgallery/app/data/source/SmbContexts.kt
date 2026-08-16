package com.flowgallery.app.data.source

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared jcifs-ng configuration for SMB access.
 *
 * Contexts are CACHED per share (host/user/share/path): jcifs-ng pools
 * transports, so reusing a context reuses the connection — creating a fresh
 * BaseContext per operation opens new connections and quickly exhausts the
 * server's cap ("No more connections can be made…").
 */
object SmbContexts {

    private val cache = ConcurrentHashMap<String, CIFSContext>()

    private fun baseContext(): CIFSContext {
        val props = Properties().apply {
            // Generous timeouts: large files take a while over slow networks.
            setProperty("jcifs.smb.client.responseTimeout", "60000")
            setProperty("jcifs.smb.client.connTimeout", "20000")
            setProperty("jcifs.smb.client.soTimeout", "120000")
            setProperty("jcifs.smb.client.sessionTimeout", "60000")
            setProperty("jcifs.resolveOrder", "DNS")
            setProperty("jcifs.smb.client.disableSMB1", "true")
        }
        return BaseContext(PropertyConfiguration(props))
    }

    /** Context with the share's credentials, cached per share. */
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
