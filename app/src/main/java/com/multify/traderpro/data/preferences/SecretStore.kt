package com.multify.traderpro.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("secure_broker_credentials", Context.MODE_PRIVATE)
    private val alias = "multify_trader_broker_v21" // keep alias for seamless v2.1 -> v2.2 upgrade
    private val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun hasApiKey(): Boolean = prefs.contains(key("api_key", "ciphertext"))
    fun hasTotpSecret(): Boolean = prefs.contains(key("api_secret", "ciphertext"))
    fun hasAccessToken(): Boolean = prefs.contains(key("access_token", "ciphertext"))
    fun hasBrokerCredentials(): Boolean = hasApiKey() && (hasTotpSecret() || hasAccessToken())

    fun putApiKey(value: String) = put("api_key", value)
    fun putTotpSecret(value: String) = put("api_secret", value)
    fun putAccessToken(value: String) = put("access_token", value)

    fun getApiKey(): String? = get("api_key")
    fun getTotpSecret(): String? = get("api_secret")
    fun getAccessToken(): String? = get("access_token")

    fun clearAccessToken() = clear("access_token")

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(name: String, suffix: String) = "${name}_$suffix"

    private fun put(name: String, value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) return
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(normalized.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit()
            .putString(key(name, "iv"), iv)
            .putString(key(name, "ciphertext"), encrypted)
            .apply()
    }

    private fun get(name: String): String? {
        val iv = prefs.getString(key(name, "iv"), null) ?: return null
        val encrypted = prefs.getString(key(name, "ciphertext"), null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun clear(name: String) {
        prefs.edit().remove(key(name, "iv")).remove(key(name, "ciphertext")).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = androidKeyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
