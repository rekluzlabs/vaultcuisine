package com.rekluzlabs.vaultcuisine.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

class GeminiCredentialStore(context: Context) {

    init {
        migrateLegacyPlaintextKey(context)
    }

    private val prefs: SharedPreferences = run {
        val mk = try {
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: GeneralSecurityException) {
            context.getSharedPreferences("_androidx_security_crypto_master_key_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit()
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).commit()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).commit()
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    /**
     * One-time migration from the pre-encryption plaintext store. The key was
     * historically persisted (if at all) under the same prefs file this store
     * now uses, before EncryptedSharedPreferences took it over. If a plaintext
     * `api_key` entry still exists there, copy it into the encrypted store and
     * delete the plaintext entry. When the file is already the encrypted store
     * (the normal case) `contains(KEY_API_KEY)` is false — encrypted keys never
     * match the literal name — so this is a safe no-op.
     */
    private fun migrateLegacyPlaintextKey(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (!legacy.contains(KEY_API_KEY)) return
        val plain = legacy.getString(KEY_API_KEY, null)
        if (!plain.isNullOrBlank() && getApiKey() == null) {
            saveApiKey(plain)
        }
        legacy.edit().remove(KEY_API_KEY).commit()
    }

    companion object {
        private const val PREFS_NAME = "gemini_credentials"
        private const val LEGACY_PREFS_NAME = "gemini_credentials"
        private const val KEY_API_KEY = "api_key"
    }
}
