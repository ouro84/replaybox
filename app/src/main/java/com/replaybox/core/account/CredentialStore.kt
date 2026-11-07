package com.replaybox.core.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * A Provider's credentials at rest, encrypted with an Android Keystore-backed
 * master key (AGENTS.md: no secret in the repo, none in logs — this is the
 * only place they exist on the device). One encrypted file per Provider,
 * named by [fileName]; TF1+ keeps the file it always used, so existing
 * sign-ins survive the move.
 */
class CredentialStore(context: Context, providerId: String) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName(providerId),
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): Pair<String, String>? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return email to password
    }

    fun save(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"

        /** Encrypted prefs file of [providerId]; `tf1` maps to the legacy `replaybox_tf1_credentials`. */
        fun fileName(providerId: String): String = "replaybox_${providerId}_credentials"
    }
}
