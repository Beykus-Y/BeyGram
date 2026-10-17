package com.beykus.beygram.data.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class LoopbackSocksProbeTest {
    @Test
    fun listenerThatAcceptsOurCredentialsIsOurs() {
        val received = AtomicReference<Pair<String, String>>()
        val result = withServer(acceptCredentials = { user, pass -> received.set(user to pass); true }) { port ->
            LoopbackSocksProbe.check(HOST, port, "user", "secret", 1_000, 1_000)
        }
        assertEquals(LoopbackSocksProbe.Result.OWN_LISTENER, result)
        assertEquals("user" to "secret", received.get())
    }

    @Test
    fun listenerWithOtherCredentialsIsForeign() {
        // Another BeyGram variant on the same port: it requires credentials, but not ours (issue #75).
        val result = withServer(acceptCredentials = { _, _ -> false }) { port ->
            LoopbackSocksProbe.check(HOST, port, "user", "secret", 1_000, 1_000)
        }
        assertEquals(LoopbackSocksProbe.Result.FOREIGN_LISTENER, result)
    }

    @Test
    fun listenerWithoutAuthenticationIsForeign() {
        val result = withServer(acceptCredentials = null) { port ->
            LoopbackSocksProbe.check(HOST, port, "user", "secret", 1_000, 1_000)
        }
        assertEquals(LoopbackSocksProbe.Result.FOREIGN_LISTENER, result)
    }

    @Test
    fun greetingOffersNoAuthAndPassword() {
        val greeting = AtomicReference<ByteArray>()
        withServer(acceptCredentials = { _, _ -> true }, onGreeting = greeting::set) { port ->
            LoopbackSocksProbe.check(HOST, port, "user", "secret", 1_000, 1_000)
        }
        assertArrayEquals(byteArrayOf(5, 2, 0, 2), greeting.get())
    }

    @Test(expected = java.io.IOException::class)
    fun closedPortIsNotReadyYet() {
        val port = ServerSocket(0, 1, InetAddress.getByName(HOST)).use { it.localPort }
        LoopbackSocksProbe.check(HOST, port, "user", "secret", 300, 300)
    }

    /** A minimal RFC 1928/1929 server. A null [acceptCredentials] means the server requires no authentication. */
    private fun <T> withServer(
        acceptCredentials: ((String, String) -> Boolean)?,
        onGreeting: (ByteArray) -> Unit = {},
        block: (Int) -> T
    ): T = ServerSocket(0, 1, InetAddress.getByName(HOST)).use { server ->
        val worker = thread {
            runCatching {
                server.accept().use { client ->
                    val input = DataInputStream(client.getInputStream())
                    val output = client.getOutputStream()
                    val head = ByteArray(2).also(input::readFully)
                    val methods = ByteArray(head[1].toInt()).also(input::readFully)
                    onGreeting(head + methods)
                    if (acceptCredentials == null) {
                        output.write(byteArrayOf(5, 0))
                        return@use
                    }
                    output.write(byteArrayOf(5, 2))
                    input.readUnsignedByte()
                    val user = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    val pass = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    val ok = acceptCredentials(String(user), String(pass))
                    output.write(byteArrayOf(1, if (ok) 0 else 1))
                    output.flush()
                }
            }
        }
        try {
            block(server.localPort)
        } finally {
            worker.join(2_000)
        }
    }

    private companion object {
        const val HOST = "127.0.0.1"
    }
}
