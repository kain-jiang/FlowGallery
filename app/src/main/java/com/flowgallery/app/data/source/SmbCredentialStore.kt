package com.flowgallery.app.data.source

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted SMB credential store.
 *
 * Passwords NEVER live inside URLs anymore: folder/item uriStrings are
 * credential-free (`smb://host/share/path`) and credentials are stored here,
 * AES-GCM encrypted with a key held in the Android Keystore (never leaves
 * the TEE). Changing a password = re-saving the config; the index keys and
 * scan cache stay valid because they never contained the secret.
 */
object SmbCredentialStore {

    private const val KEY_ALIAS = "flowgallery_smb_creds"
    private const val PREFS = "flowgallery_smb_creds"
    private const val PREFIX = "cred_"

    private lateinit var prefs: SharedPreferences
    private var cachedKey: SecretKey? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Persist credentials for a share (overwrites the previous password). */
    fun save(config: SmbConfig) {
        if (config.username.isEmpty()) return
        val payload = "${config.domain}\n${config.username}\n${config.password}"
        prefs.edit().putString(PREFIX + config.credKey, encrypt(payload)).apply()
    }

    /** Credentials for [host]/[share], or null when none stored. */
    fun load(host: String, share: String): Triple<String, String, String>? {
        val raw = prefs.getString(PREFIX + "$host/${share.trim('/')}", null) ?: return null
        val decrypted = runCatching { decrypt(raw) }.getOrNull() ?: return null
        val parts = decrypted.split('\n', limit = 3)
        if (parts.size != 3) return null
        return Triple(parts[1], parts[2], parts[0]) // user, pass, domain
    }

    /** Drop stored credentials (folder removal / user edited them away). */
    fun delete(host: String, share: String) {
        prefs.edit().remove(PREFIX + "$host/${share.trim('/')}").apply()
    }

    /**
     * Resolve a possibly-credential-free URL to a full config:
     * - URL already carries credentials (legacy persisted data) → parse as-is
     * - URL is clean → attach stored credentials (if any)
     */
    fun configFor(url: String): SmbConfig? {
        val parsed = SmbConfig.fromUrl(url) ?: return null
        if (parsed.username.isNotEmpty()) return parsed // legacy inline creds
        val creds = load(parsed.host, parsed.share) ?: return parsed
        return parsed.copy(
            username = creds.first,
            password = creds.second,
            domain = creds.third
        )
    }

    // ------------------------------------------------------------------ crypto

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            cachedKey = it
            return it
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        val k = gen.generateKey()
        cachedKey = k
        return k
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val (ivB64, ctB64) = blob.split(':', limit = 2)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ct = Base64.decode(ctB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
