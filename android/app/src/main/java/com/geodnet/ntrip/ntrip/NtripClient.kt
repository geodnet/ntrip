package com.geodnet.ntrip.ntrip

import com.geodnet.ntrip.rtcm.BaseStationFix
import com.geodnet.ntrip.rtcm.EpochLatencyStats
import com.geodnet.ntrip.rtcm.RtcmFrame
import com.geodnet.ntrip.rtcm.RtcmFrameParser
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * Ntrip caster connection: opens a TCP socket, sends the Ntrip GET request with Basic Auth,
 * uploads a GGA position fix on an interval, and decodes incoming RTCM3 frames via
 * [RtcmFrameParser]. Mirrors node/ntrip_client.js's connection flow.
 */
class NtripClient(
    private val config: NtripConfig,
    /** Supplies a live rover position (BLE fix if connected, else phone GPS fallback -- see
     * `location.LocationFixAggregator`) to use instead of `config`'s static lat/lon/alt for both
     * the GGA uploaded to the caster and the baseline-distance reference position, whenever it
     * returns non-null. Null (the default) preserves the original always-static behavior. */
    private val livePosition: (() -> GgaPositionOverride?)? = null,
) {

    private val _state = MutableStateFlow(NtripState(status = NtripStatus.CONNECTING))
    val state: StateFlow<NtripState> = _state.asStateFlow()

    private val rtcmParser = RtcmFrameParser {
        val live = if (config.useLiveLocation) livePosition?.invoke() else null
        if (live != null) Triple(live.latitude, live.longitude, live.altitudeM)
        else Triple(config.latitude, config.longitude, config.altitude)
    }
    val rtcmStats: StateFlow<RtcmStats> = rtcmParser.stats
    val rtcmMessages: SharedFlow<RtcmMessage> = rtcmParser.messages
    val baseStation: StateFlow<BaseStationFix?> = rtcmParser.baseStation
    val rtcmFrames: SharedFlow<RtcmFrame> = rtcmParser.frames
    val epochStats: StateFlow<EpochLatencyStats> = rtcmParser.epochStats

    /** Raw bytes as received from the caster, for forwarding to a BLE RTK receiver -- separate
     * from [rtcmMessages], which carries decoded/summarized messages, not the original bytes. */
    private val _rawBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rawBytes: SharedFlow<ByteArray> = _rawBytes.asSharedFlow()

    private var socket: Socket? = null

    /** Connects and runs until cancelled or the connection drops; suspends for its duration. */
    suspend fun run() = coroutineScope {
        _state.value = NtripState(status = NtripStatus.CONNECTING)
        rtcmParser.reset()
        try {
            withContext(Dispatchers.IO) {
                socket = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    soTimeout = SOCKET_READ_TIMEOUT_MS
                    connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
                }
                sendRequest()
            }

            _state.update { it.copy(status = NtripStatus.CONNECTED) }

            val ggaJob = if (config.ggaIntervalMs > 0) {
                launch {
                    sendGga()
                    while (isActive) {
                        delay(config.ggaIntervalMs)
                        sendGga()
                    }
                }
            } else null

            try {
                readLoop()
            } finally {
                ggaJob?.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            _state.update {
                it.copy(
                    status = NtripStatus.ERROR,
                    errorMessage = "Stream data timeout (no data for ${SOCKET_READ_TIMEOUT_MS / 1000}s)"
                )
            }
        } catch (e: IOException) {
            _state.update { it.copy(status = NtripStatus.ERROR, errorMessage = e.message ?: "Connection error") }
        } finally {
            closeSocket()
            _state.update {
                if (it.status == NtripStatus.ERROR) it else it.copy(status = NtripStatus.DISCONNECTED)
            }
        }
    }

    /** Closes the socket, interrupting [run] if it's suspended in a blocking read/write. */
    fun stop() {
        closeSocket()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
            // already closed / never opened
        }
        socket = null
    }

    private fun sendRequest() {
        val cleanMount = config.mountpoint.trim().removePrefix("/").ifBlank { "AUTO" }
        val credentials = "${config.username}:${config.password}"
        val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        val request = "GET /$cleanMount HTTP/1.0\r\n" +
            "User-Agent: $USER_AGENT\r\n" +
            "Authorization: Basic $auth\r\n\r\n"
        socket?.getOutputStream()?.apply {
            write(request.toByteArray(Charsets.US_ASCII))
            flush()
        }
    }

    private suspend fun sendGga() = withContext(Dispatchers.IO) {
        val live = if (config.useLiveLocation) livePosition?.invoke() else null
        val sentence = (
            if (live != null) {
                GgaGenerator.generate(live.latitude, live.longitude, live.altitudeM, live.numSatellites, live.hdop)
            } else {
                GgaGenerator.generate(config)
            }
            ) + "\r\n"
        try {
            socket?.getOutputStream()?.apply {
                write(sentence.toByteArray(Charsets.US_ASCII))
                flush()
            }
        } catch (_: IOException) {
            // Output stream broken; close socket so readLoop terminates and triggers auto-reconnect
            closeSocket()
        }
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val input = socket?.getInputStream() ?: return@withContext
        val buffer = ByteArray(4096)
        var firstChunk = true
        while (isActive) {
            val n = input.read(buffer)
            if (n < 0) break
            if (firstChunk) {
                firstChunk = false
                val preview = String(buffer, 0, n.coerceAtMost(256), Charsets.US_ASCII)
                if (preview.startsWith("HTTP/") || preview.startsWith("ICY ") || preview.startsWith("SOURCETABLE")) {
                    val statusLine = preview.lines().firstOrNull() ?: ""
                    val isOk = statusLine.contains("200") || statusLine.contains("OK")
                    if (preview.startsWith("SOURCETABLE")) {
                        _state.update {
                            it.copy(
                                status = NtripStatus.ERROR,
                                errorMessage = "Caster returned sourcetable instead of stream (check mountpoint)"
                            )
                        }
                        return@withContext
                    } else if (!isOk) {
                        _state.update {
                            it.copy(
                                status = NtripStatus.ERROR,
                                errorMessage = "Caster response: $statusLine".trim()
                            )
                        }
                        return@withContext
                    }
                }
            }
            _state.update { it.copy(bytesReceived = it.bytesReceived + n) }
            rtcmParser.feed(buffer, n)
            _rawBytes.tryEmit(buffer.copyOf(n))
        }
    }

    companion object {
        private const val USER_AGENT = "ntrip client Android/0.1.0"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKET_READ_TIMEOUT_MS = 15_000
    }
}
