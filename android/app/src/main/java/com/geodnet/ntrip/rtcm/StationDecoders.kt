package com.geodnet.ntrip.rtcm

/** Decoded 1005 (Stationary RTK Reference Station ARP) / 1006 (+ antenna height). */
data class StationArp(
    val staId: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val antHeightM: Double?,
)

/** Decoded 1033 (Receiver and Antenna Descriptors). */
data class ReceiverAntennaInfo(
    val staId: Int,
    val antDescriptor: String,
    val antSetupId: Int,
    val antSerial: String,
    val recType: String,
    val recFirmware: String,
    val recSerial: String,
)

/** Ported from node/ntrip_client.js's decodeMsg1005_1006() / decodeMsg1033(). */
object StationDecoders {

    fun decode1005Or1006(payload: ByteArray, msgType: Int): StationArp {
        val br = RtcmBitReader(payload)
        br.skip(12) // message number
        val staId = br.readUnsigned(12).toInt()
        br.skip(6) // itrf
        br.skip(4) // GPS/GLONASS/Galileo indicators + reference-station indicator
        val x = br.readSigned(38) * 0.0001
        br.skip(2) // single receiver oscillator indicator + reserved
        val y = br.readSigned(38) * 0.0001
        br.skip(2) // quarter cycle indicator
        val z = br.readSigned(38) * 0.0001
        val antHeightM = if (msgType == 1006) br.readUnsigned(16) * 0.0001 else null
        return StationArp(staId, x, y, z, antHeightM)
    }

    fun decode1033(payload: ByteArray): ReceiverAntennaInfo {
        val br = RtcmBitReader(payload)
        br.skip(12) // message number
        val staId = br.readUnsigned(12).toInt()
        val n = br.readUnsigned(8).toInt()
        val antDescriptor = br.readString(n)
        val antSetupId = br.readUnsigned(8).toInt()
        val m = br.readUnsigned(8).toInt()
        val antSerial = br.readString(m)
        val n1 = br.readUnsigned(8).toInt()
        val recType = br.readString(n1)
        val n2 = br.readUnsigned(8).toInt()
        val recFirmware = br.readString(n2)
        val n3 = br.readUnsigned(8).toInt()
        val recSerial = br.readString(n3)
        return ReceiverAntennaInfo(staId, antDescriptor, antSetupId, antSerial, recType, recFirmware, recSerial)
    }
}
