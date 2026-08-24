package com.geodnet.ntrip.rtcm

import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.BufferOverflow
import kotlin.math.sqrt

/** One decoded (or CRC-failed) RTCM frame, for the live inspector log. */
data class RtcmMessage(
    val msgKey: String,
    val msgType: Int,
    val lengthBytes: Int,
    val crcOk: Boolean,
    val timestampMs: Long,
    val summary: String,
)

/** Cumulative counters since the connection started, mirroring node/ntrip_client.js's stats. */
data class RtcmStats(
    val bytesReceived: Long = 0,
    val bytesCrcFail: Long = 0,
    val msgsDecoded: Long = 0,
    val msgsCrcFail: Long = 0,
    val msgCounts: Map<String, Int> = emptyMap(),
)

private val EPH_TYPES = setOf(1019, 1020, 1042, 1044, 1045, 1046)

/**
 * Buffers incoming TCP bytes, extracts complete RTCM3 frames (tolerating split/merged chunks),
 * verifies CRC-24Q, and decodes the message types this app understands in detail. Ported from
 * node/ntrip_client.js's processRtcmBuffer()/handleRtcmFrame()/describeFrameDetail() -- see
 * node/CLAUDE.md before changing any bit layout here.
 *
 * [refPosition] supplies the configured receiver lat/lon/alt (degrees, degrees, meters) used to
 * compute the 1005/1006 baseline distance.
 */
class RtcmFrameParser(private val refPosition: () -> Triple<Double, Double, Double>) {

    private var buffer = ByteArray(0)

    private val _stats = MutableStateFlow(RtcmStats())
    val stats: StateFlow<RtcmStats> = _stats.asStateFlow()

    private val _messages = MutableSharedFlow<RtcmMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<RtcmMessage> = _messages.asSharedFlow()

    fun reset() {
        buffer = ByteArray(0)
        _stats.value = RtcmStats()
    }

    fun feed(chunk: ByteArray, length: Int) {
        _stats.update { it.copy(bytesReceived = it.bytesReceived + length) }
        buffer += chunk.copyOf(length)
        process()
    }

    private fun process() {
        while (true) {
            if (buffer.isEmpty()) return
            if (buffer[0] != SYNC_BYTE) {
                var idx = -1
                for (i in 1 until buffer.size) {
                    if (buffer[i] == SYNC_BYTE) {
                        idx = i
                        break
                    }
                }
                buffer = if (idx >= 0) buffer.copyOfRange(idx, buffer.size) else ByteArray(0)
                continue
            }
            if (buffer.size < 3) return // wait for the rest of the header
            if ((buffer[1].toInt() and 0xFC) != 0) {
                // reserved bits must be 0; this sync byte is data, not a frame start
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            val payloadLen = ((buffer[1].toInt() and 0x03) shl 8) or (buffer[2].toInt() and 0xFF)
            val frameLen = payloadLen + 6 // 3-byte header + payload + 3-byte CRC
            if (buffer.size < frameLen) return // wait for the rest of the frame

            val frame = buffer.copyOfRange(0, frameLen)
            buffer = buffer.copyOfRange(frameLen, buffer.size)
            handleFrame(frame, payloadLen)
        }
    }

    private fun handleFrame(frame: ByteArray, payloadLen: Int) {
        val computedCrc = Crc24Q.compute(frame, 0, 3 + payloadLen)
        val receivedCrc = ((frame[3 + payloadLen].toInt() and 0xFF) shl 16) or
            ((frame[3 + payloadLen + 1].toInt() and 0xFF) shl 8) or
            (frame[3 + payloadLen + 2].toInt() and 0xFF)
        val crcOk = computedCrc == receivedCrc

        val msgType = ((frame[3].toInt() and 0xFF) shl 4) or ((frame[4].toInt() and 0xFF) shr 4)
        var msgKey = msgType.toString()
        if (msgType == 4076 && payloadLen >= 3) {
            // IGS SSR: 12-bit type, 3-bit version, 8-bit IGS sub-message number
            val subType = ((frame[4].toInt() and 0x01) shl 7) or ((frame[5].toInt() and 0xFF) shr 1)
            msgKey = "$msgType.$subType"
        } else if (msgType in 4070..4095 && payloadLen >= 2) {
            // other proprietary ranges (e.g. u-blox 4072.x): 12-bit type + 4-bit subtype
            val subType = frame[4].toInt() and 0x0F
            msgKey = "$msgType.$subType"
        }

        val now = System.currentTimeMillis()

        if (!crcOk) {
            _stats.update { it.copy(msgsCrcFail = it.msgsCrcFail + 1, bytesCrcFail = it.bytesCrcFail + frame.size) }
            _messages.tryEmit(RtcmMessage(msgKey, msgType, frame.size, false, now, "CRC mismatch"))
            return
        }

        val payload = frame.copyOfRange(3, 3 + payloadLen)
        val summary = describeFrameDetail(msgType, payload)

        _stats.update {
            val counts = it.msgCounts.toMutableMap()
            counts[msgKey] = (counts[msgKey] ?: 0) + 1
            it.copy(msgsDecoded = it.msgsDecoded + 1, msgCounts = counts)
        }
        _messages.tryEmit(RtcmMessage(msgKey, msgType, frame.size, true, now, summary))
    }

    private fun describeFrameDetail(msgType: Int, payload: ByteArray): String = try {
        when {
            msgType == 1005 || msgType == 1006 -> {
                val d = StationDecoders.decode1005Or1006(payload, msgType)
                val llh = GeoMath.ecefToLlh(d.x, d.y, d.z)
                val (lat, lon, alt) = refPosition()
                val rover = GeoMath.llhToEcef(lat, lon, alt)
                val dx = d.x - rover.x
                val dy = d.y - rover.y
                val dz = d.z - rover.z
                val baselineKm = sqrt(dx * dx + dy * dy + dz * dz) / 1000
                var s = "staid=${d.staId} xyz=(%.3f,%.3f,%.3f) llh=(%.7f,%.7f,%.3f) base=%.3fkm".format(
                    d.x, d.y, d.z, llh.latDeg, llh.lonDeg, llh.heightM, baselineKm,
                )
                if (msgType == 1006 && d.antHeightM != null) {
                    s += " antHt=%.4fm".format(d.antHeightM)
                }
                s
            }
            msgType == 1033 -> {
                val d = StationDecoders.decode1033(payload)
                "staid=${d.staId} ant=\"${d.antDescriptor}\"(${d.antSetupId}) sn=\"${d.antSerial}\" " +
                    "rx=\"${d.recType}\" fw=\"${d.recFirmware}\" rxsn=\"${d.recSerial}\""
            }
            msgType in EPH_TYPES -> {
                val now = System.currentTimeMillis()
                val e = when (msgType) {
                    1019 -> EphemerisDecoders.decode1019(payload, now)
                    1020 -> EphemerisDecoders.decode1020(payload, now)
                    1042 -> EphemerisDecoders.decode1042(payload)
                    1044 -> EphemerisDecoders.decode1044(payload, now)
                    1045 -> EphemerisDecoders.decodeGalileo(payload, isInav = false)
                    else -> EphemerisDecoders.decodeGalileo(payload, isInav = true) // 1046
                }
                val ageSec = (now - e.toeMs) / 1000.0
                val toeIso = Instant.ofEpochMilli(e.toeMs).toString()
                val svhHex = e.svh.toString(16).padStart(2, '0')
                "sys=${e.sys} prn=${e.prn} toe=$toeIso svh=0x$svhHex age=${if (ageSec >= 0) "+" else ""}${"%.0f".format(ageSec)}s"
            }
            else -> {
                val sys = getMsmSystem(msgType)
                if (sys != null) {
                    val h = MsmHeaderDecoder.decode(payload, sys)
                    val epochStr = if (h.glonassDow != null) {
                        "dow=${h.glonassDow} tod=%.3f".format(h.glonassTodSec)
                    } else {
                        "tow=%.3f".format(h.towSec)
                    }
                    "staid=${h.staId} sys=${sys.name} $epochStr nsat=${h.nsat} nsig=${h.nsig} sig=[${h.sigNames.joinToString(",")}]"
                } else {
                    ""
                }
            }
        }
    } catch (e: Exception) {
        "(decode error: ${e.message})"
    }

    companion object {
        private const val SYNC_BYTE: Byte = 0xD3.toByte()
    }
}
