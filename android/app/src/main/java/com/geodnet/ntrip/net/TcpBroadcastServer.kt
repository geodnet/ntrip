package com.geodnet.ntrip.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * A loopback-only TCP server that broadcasts every chunk passed to [broadcast] to all currently
 * connected clients. Backs both the NMEA (127.0.0.1:10110) and RTCM (127.0.0.1:10120) outputs
 * described in readme.md, so GIS apps like SW Maps running on the same device can read the
 * streams over a local socket -- bound to 127.0.0.1 only, this is a same-device bridge, not a
 * network service.
 *
 * A slow/stalled client gets its oldest unsent chunks dropped ([BufferOverflow.DROP_OLDEST])
 * rather than blocking or backing up the other clients.
 */
class TcpBroadcastServer(private val port: Int) {

    private val _state = MutableStateFlow(TcpServerState(port = port))
    val state: StateFlow<TcpServerState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val clients = Collections.synchronizedList(mutableListOf<ClientWriter>())

    fun start(scope: CoroutineScope) {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port, BACKLOG, InetAddress.getByName(LOOPBACK))
                serverSocket = socket
                _state.update { it.copy(listening = true, errorMessage = null) }
                while (isActive) {
                    val clientSocket = socket.accept()
                    val writer = ClientWriter(clientSocket) { closed ->
                        clients.remove(closed)
                        _state.update { it.copy(clientCount = clients.size) }
                    }
                    clients.add(writer)
                    _state.update { it.copy(clientCount = clients.size) }
                    writer.start(scope)
                }
            } catch (e: IOException) {
                _state.update { it.copy(listening = false, errorMessage = e.message) }
            } finally {
                closeAllClients()
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        closeAllClients()
        _state.value = TcpServerState(port = port)
    }

    fun broadcast(data: ByteArray) {
        if (clients.isEmpty()) return
        val snapshot = synchronized(clients) { clients.toList() }
        snapshot.forEach { it.send(data) }
        _state.update { it.copy(bytesSent = it.bytesSent + data.size) }
    }

    private fun closeAllClients() {
        val snapshot = synchronized(clients) { clients.toList() }
        snapshot.forEach { it.close() }
        clients.clear()
    }

    private class ClientWriter(
        private val socket: Socket,
        private val onClosed: (ClientWriter) -> Unit,
    ) {
        private val queue = Channel<ByteArray>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private var job: Job? = null

        fun start(scope: CoroutineScope) {
            job = scope.launch(Dispatchers.IO) {
                try {
                    val out = socket.getOutputStream()
                    for (data in queue) {
                        out.write(data)
                        out.flush()
                    }
                } catch (_: IOException) {
                } finally {
                    close()
                    onClosed(this@ClientWriter)
                }
            }
        }

        fun send(data: ByteArray) {
            queue.trySend(data)
        }

        fun close() {
            job?.cancel()
            queue.close()
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val BACKLOG = 10
    }
}
