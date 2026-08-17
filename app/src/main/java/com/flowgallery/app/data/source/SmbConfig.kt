package com.flowgallery.app.data.source

/**
 * Connection config for an SMB share. The full smb:// URL (with embedded
 * credentials) is the folder's uriString and the item key used by the
 * index/dedup.
 */
data class SmbConfig(
    val host: String,
    val share: String,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = ""
) {
    /** Full URL WITH embedded credentials (in-memory use only — test
     *  connection, direct jcifs calls). NEVER persisted. */
    val url: String
        get() {
            val auth = if (username.isNotEmpty()) {
                "${java.net.URLEncoder.encode(domain, "UTF-8")};" +
                    "${java.net.URLEncoder.encode(username, "UTF-8")}:" +
                    java.net.URLEncoder.encode(password, "UTF-8") + "@"
            } else ""
            // NOTE: share/path stay RAW (Chinese names) — jcifs-ng expects
            // them unencoded; URL-encoding breaks share resolution.
            val sharePath = share.trim('/')
            val sub = path.trim('/')
            val base = "smb://$auth$host/$sharePath"
            return if (sub.isNotEmpty()) "$base/$sub/" else "$base/"
        }

    /** URL WITHOUT credentials — the ONLY form persisted (folder uriString,
     *  item uriString, index keys, scan cache, Coil cache keys). */
    val urlNoCreds: String
        get() {
            val sharePath = share.trim('/')
            val sub = path.trim('/')
            val base = "smb://$host/$sharePath"
            return if (sub.isNotEmpty()) "$base/$sub/" else "$base/"
        }

    /** Stable credential key (host/share) used by the encrypted store. */
    val credKey: String get() = "$host/${share.trim('/')}"

    /** Parse a full smb:// URL (with credentials) back into a config. */
    companion object {
        fun fromUrl(fullUrl: String): SmbConfig? = runCatching {
            val noScheme = fullUrl.removePrefix("smb://")
            val at = noScheme.lastIndexOf('@')
            var user = ""
            var pass = ""
            var domain = ""
            var rest = noScheme
            if (at >= 0) {
                val cred = noScheme.substring(0, at)
                rest = noScheme.substring(at + 1)
                val semi = cred.indexOf(';')
                if (semi >= 0) {
                    domain = java.net.URLDecoder.decode(cred.substring(0, semi), "UTF-8")
                    val u = cred.substring(semi + 1)
                    val colon = u.indexOf(':')
                    user = if (colon >= 0) java.net.URLDecoder.decode(u.substring(0, colon), "UTF-8")
                    else java.net.URLDecoder.decode(u, "UTF-8")
                    pass = if (colon >= 0) java.net.URLDecoder.decode(u.substring(colon + 1), "UTF-8") else ""
                } else {
                    val colon = cred.indexOf(':')
                    user = if (colon >= 0) java.net.URLDecoder.decode(cred.substring(0, colon), "UTF-8")
                    else java.net.URLDecoder.decode(cred, "UTF-8")
                    pass = if (colon >= 0) java.net.URLDecoder.decode(cred.substring(colon + 1), "UTF-8") else ""
                }
            }
            // rest = host/share/path (raw names)
            val slash = rest.indexOf('/')
            val host = if (slash >= 0) rest.substring(0, slash) else rest
            val restPath = if (slash >= 0) rest.substring(slash + 1) else ""
            val parts = restPath.split('/').filter { it.isNotEmpty() }
            val share = parts.firstOrNull() ?: ""
            val path = parts.drop(1).joinToString("/")
            SmbConfig(host, share, path, user, pass, domain)
        }.getOrNull()
    }
}
