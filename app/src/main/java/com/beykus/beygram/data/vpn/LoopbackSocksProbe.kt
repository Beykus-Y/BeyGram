package com.beykus.beygram.data.vpn

import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tells BeyGram's own loopback SOCKS listener apart from another process on the same port.
 *
 * Mihomo reports a failed bind only in its log, so an open port alone does not prove the listener is ours: another
 * app (another BeyGram variant included) may hold it, and TDLib would then wait on a proxy that rejects it (issue #75).
 * Only our listener requires and accepts this installation's credentials.
 */
internal object LoopbackSocksProbe {
    enum class Result {
        /** The listener asked for credentials and accepted ours. */
        OWN_LISTENER,
        /** Something answers on the port but rejects our credentials or does not require any. */
        FOREIGN_LISTENER
    }

    /** Throws [IOException] when nothing answers yet, so the caller can retry. */
    fun check(host: String, port: Int, username: String, password: String, connectTimeoutMillis: Int, readTimeoutMillis: Int): Result {
        val user = username.toByteArray(Charsets.UTF_8)
        val pass = password.toByteArray(Charsets.UTF_8)
        require(user.size in 1..255 && pass.size in 1..255) { "SOCKS5 credentials must be 1..255 bytes" }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.soTimeout = readTimeoutMillis
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            // Offer "no auth" too: a listener that picks it is not ours, since ours always requires credentials.
            // Offering only username/password would let a listener without auth accept any credentials.
            output.write(byteArrayOf(SOCKS_VERSION, 2, METHOD_NO_AUTH, METHOD_USER_PASSWORD))
            output.flush()
            val method = input.readExactly(2)
            if (method[0] != SOCKS_VERSION || method[1] != METHOD_USER_PASSWORD) return Result.FOREIGN_LISTENER
            output.write(byteArrayOf(AUTH_VERSION, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
            output.flush()
            val status = input.readExactly(2)
            return if (status[0] == AUTH_VERSION && status[1] == AUTH_SUCCESS) Result.OWN_LISTENER else Result.FOREIGN_LISTENER
        }
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val chunk = read(buffer, read, count - read)
            if (chunk < 0) throw IOException("SOCKS listener closed the connection")
            read += chunk
        }
        return buffer
    }

    private const val SOCKS_VERSION: Byte = 5
    private const val METHOD_NO_AUTH: Byte = 0
    private const val METHOD_USER_PASSWORD: Byte = 2
    private const val AUTH_VERSION: Byte = 1
    private const val AUTH_SUCCESS: Byte = 0
}
