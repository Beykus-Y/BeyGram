package com.beykus.beygram.data.yandex

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Signed-in state kept on this device. Tokens never leave this store except in request headers. */
internal data class YandexSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val uid: String,
    val displayName: String,
    val hasSubscription: Boolean,
)

/**
 * Keeps the Yandex session in a file under noBackupFilesDir, encrypted with an AES-GCM key held in the Android
 * Keystore. The file is excluded from backups by location. A file that cannot be decrypted counts as signed out.
 */
internal class YandexSessionStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    @Synchronized
    fun load(): YandexSession? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val ivLength = bytes[0].toInt() and 0xFF
            val iv = bytes.copyOfRange(1, 1 + ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(bytes, 1 + ivLength, bytes.size - 1 - ivLength), Charsets.UTF_8))
            YandexSession(
                accessToken = json.getString("access"),
                refreshToken = json.optString("refresh").takeIf { it.isNotEmpty() },
                expiresAtMs = json.getLong("expires"),
                uid = json.getString("uid"),
                displayName = json.optString("name"),
                hasSubscription = json.optBoolean("plus"),
            )
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    @Synchronized
    fun save(session: YandexSession) {
        val json = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken ?: "")
            .put("expires", session.expiresAtMs)
            .put("uid", session.uid)
            .put("name", session.displayName)
            .put("plus", session.hasSubscription)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    /**
     * Device id sent with every sign-in. It is created once and kept across sign-outs, because the service limits
     * the number of device tokens per user and a new id on each sign-in would keep adding them.
     */
    @Synchronized
    fun deviceId(): String {
        val idFile = File(file.parentFile, DEVICE_FILE_NAME)
        val existing = try { idFile.readText().trim() } catch (e: Exception) { "" }
        if (existing.length == 36) return existing
        val created = java.util.UUID.randomUUID().toString()
        idFile.writeText(created)
        return created
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "yandex-session.bin"
        const val DEVICE_FILE_NAME = "yandex-device-id"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "beygram_yandex_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
