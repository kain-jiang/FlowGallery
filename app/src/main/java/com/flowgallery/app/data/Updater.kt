package com.flowgallery.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update via GitHub Releases (matches the release workflow):
 * tags are `vX.Y.Z` → versionName `X.Y.Z`, versionCode `10000X + 100Y + Z`.
 */
object Updater {

    private const val REPO_API =
        "https://api.github.com/repos/kain-jiang/FlowGallery/releases/latest"

    /** Latest release info; null when the check fails (network / no release). */
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String
    )

    /** Semver "x.y.z" → int, mirroring the CI (10000x + 100y + z). */
    fun versionCodeOf(versionName: String): Int {
        val parts = versionName.trimStart('v').split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }

    /**
     * Fetch the latest release from GitHub. Returns null when there is no
     * newer version than [currentVersionCode], or on any failure.
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(REPO_API).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "FlowGallery")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").ifEmpty { return@withContext null }
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            val url = apkUrl ?: return@withContext null

            val versionName = tag.trimStart('v')
            val newCode = versionCodeOf(versionName)
            if (newCode <= currentVersionCode) return@withContext null

            UpdateInfo(versionName, newCode, url)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download the APK into [targetFile]. Returns true on success.
     * On failure the partial file is deleted.
     */
    suspend fun downloadApk(url: String, targetFile: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    return@withContext false
                }
                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                targetFile.length() > 0
            } catch (e: Exception) {
                targetFile.delete()
                false
            }
        }
}
