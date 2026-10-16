package com.beykus.beygram.data.vpn

import android.content.Context
import java.security.SecureRandom
import java.util.Base64

/**
 * Random credentials for BeyGram's loopback SOCKS listener. Every app on the device can reach 127.0.0.1,
 * so the listener rejects clients that do not present these values.
 */
internal class LoopbackProxyCredentials private constructor(val username: String, val password: String) {
    companion object {
        private const val PREFERENCES = "mihomo-loopback"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val TOKEN_BYTES = 24
        private val random = SecureRandom()

        @Synchronized
        fun get(context: Context): LoopbackProxyCredentials {
            val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val username = preferences.getString(KEY_USERNAME, null)
            val password = preferences.getString(KEY_PASSWORD, null)
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) return LoopbackProxyCredentials(username, password)
            val created = LoopbackProxyCredentials(randomToken(), randomToken())
            preferences.edit()
                .putString(KEY_USERNAME, created.username)
                .putString(KEY_PASSWORD, created.password)
                .commit()
            return created
        }

        private fun randomToken(): String {
            val bytes = ByteArray(TOKEN_BYTES)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
