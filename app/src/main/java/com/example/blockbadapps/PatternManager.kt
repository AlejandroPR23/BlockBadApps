package com.example.blockbadapps

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class PatternManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "pattern_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_PATTERN_SET = "pattern_set"
    }

    fun isPatternSet(): Boolean {
        return sharedPreferences.getBoolean(KEY_PATTERN_SET, false)
    }

    fun savePattern(pattern: String) {
        val hash = hashPattern(pattern)
        sharedPreferences.edit()
            .putString(KEY_PATTERN_HASH, hash)
            .putBoolean(KEY_PATTERN_SET, true)
            .apply()
    }

    fun verifyPattern(pattern: String): Boolean {
        val savedHash = sharedPreferences.getString(KEY_PATTERN_HASH, null) ?: return false
        val inputHash = hashPattern(pattern)
        return savedHash == inputHash
    }

    fun clearPattern() {
        sharedPreferences.edit()
            .remove(KEY_PATTERN_HASH)
            .putBoolean(KEY_PATTERN_SET, false)
            .apply()
    }

    private fun hashPattern(pattern: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pattern.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}