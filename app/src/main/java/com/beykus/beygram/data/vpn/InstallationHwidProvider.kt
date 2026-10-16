package com.beykus.beygram.data.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Stable, app-scoped installation identifier. This deliberately does not read IMEI,
 * serial number, MAC address, Android ID, or any other hardware/account identifier.
 * The encrypted value lives in noBackupFilesDir so Android backup cannot clone an
 * installation identity to another device.
 */
internal class InstallationHwidProvider(context: Context) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, "vpn-installation-id.bin"))

    @Synchronized
    fun getOrCreate(): String {
        if (atomicFile.baseFile.exists()) return decrypt(atomicFile.readFully())

        val id = "bg-${UUID.randomUUID()}"
        val encrypted = encrypt(id)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(encrypted)
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IllegalStateException("Could not persist the BeyGram installation ID", error)
        }
        return id
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.isNotEmpty()) { "Empty installation ID payload" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) { "Invalid installation ID payload" }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val decoded = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        require(decoded.startsWith("bg-") && runCatching { UUID.fromString(decoded.removePrefix("bg-")) }.isSuccess) {
            "Invalid installation ID"
        }
        return decoded
    }

    private fun getOrCreateKey(): java.security.Key {
        val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        store.getKey(KEY_ALIAS, null)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "com.beykus.beygram.vpn.installation-hwid"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
