package com.geodnet.ntrip.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Exercises real loopback sockets rather than mocking java.net -- this class's whole job is TCP
 * accept/broadcast/disconnect plumbing, so a socket-free test would just be re-testing Kotlin
 * control flow. Uses distinct, non-production ports per test to avoid clashing with each other or
 * with a real app instance that might be running the actual 10110/10120 servers.
 */
class TcpBroadcastServerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: TcpBroadcastServer? = null
    private val openSockets = mutableListOf<Socket>()

    @After
    fun tearDown() {
        server?.stop()
        openSockets.forEach { runCatching { it.close() } }
        scope.cancel()
    }

    private fun startServer(port: Int): TcpBroadcastServer =
        TcpBroadcastServer(port).also {
            server = it
            it.start(scope)
        }

    private fun connectClient(port: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
        openSockets += socket
        return socket
    }

    private fun waitUntil(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("condition not met within ${timeoutMs}ms", predicate())
    }

    @Test
    fun `starts listening and stops cleanly`() {
        val s = startServer(19110)
        waitUntil { s.state.value.listening }

        s.stop()
        waitUntil { !s.state.value.listening }
        assertEquals(0, s.state.value.clientCount)
    }

    @Test
    fun `broadcasts to every connected client`() {
        val s = startServer(19111)
        waitUntil { s.state.value.listening }

        val client1 = connectClient(19111)
        val client2 = connectClient(19111)
        waitUntil { s.state.value.clientCount == 2 }

        val payload = "hello".toByteArray(Charsets.US_ASCII)
        s.broadcast(payload)

        for (client in listOf(client1, client2)) {
            val buffer = ByteArray(payload.size)
            client.getInputStream().read(buffer)
            assertEquals("hello", String(buffer, Charsets.US_ASCII))
        }
        waitUntil { s.state.value.bytesSent == payload.size.toLong() }
    }

    @Test
    fun `a client that disconnects is dropped from the client count`() {
        val s = startServer(19112)
        waitUntil { s.state.value.listening }

        val client = connectClient(19112)
        waitUntil { s.state.value.clientCount == 1 }

        client.close()
        // The server only notices on its next write attempt -- broadcast until the count drops.
        waitUntil {
            s.broadcast("ping".toByteArray(Charsets.US_ASCII))
            s.state.value.clientCount == 0
        }
    }

    @Test
    fun `broadcast with no clients is a no-op, not an error`() {
        val s = startServer(19113)
        waitUntil { s.state.value.listening }

        s.broadcast("nobody listening".toByteArray(Charsets.US_ASCII))

        assertEquals(0, s.state.value.clientCount)
        assertTrue(s.state.value.listening)
    }
}
