package com.geodnet.ntrip.rtcm

/** Decoded common MSM header fields (satellite/signal counts + resolved signal names). */
data class MsmHeaderInfo(
    val staId: Int,
    val towSec: Double?,
    val glonassDow: Int?,
    val glonassTodSec: Double?,
    val isMoreMessagesInEpoch: Boolean, // Multiple Message Bit: 1 = more messages in epoch, 0 = last message of epoch
    val nsat: Int,
    val nsig: Int,
    val sigNames: List<String>,
)

/** Ported from node/ntrip_client.js's decodeMsmHeader(). */
object MsmHeaderDecoder {

    fun decode(payload: ByteArray, sys: MsmSystem): MsmHeaderInfo {
        val br = RtcmBitReader(payload)
        br.skip(12) // message number
        val staId = br.readUnsigned(12).toInt()

        var towSec: Double? = null
        var glonassDow: Int? = null
        var glonassTodSec: Double? = null

        when (sys.name) {
            "GLONASS" -> {
                glonassDow = br.readUnsigned(3).toInt()
                glonassTodSec = br.readUnsigned(27) * 0.001
            }
            "BeiDou" -> {
                towSec = br.readUnsigned(30) * 0.001 + 14.0 // BDT -> GPST
            }
            else -> {
                towSec = br.readUnsigned(30) * 0.001
            }
        }

        val sync = br.readUnsigned(1) != 0L // Multiple Message Bit (1 = more to follow, 0 = last of epoch)
        br.skip(3) // iod
        br.skip(7) // reserved
        br.skip(2) // clk steering
        br.skip(2) // ext clock
        br.skip(1) // smoothing
        br.skip(3) // smooth interval

        var nsat = 0
        for (j in 1..64) {
            if (br.readUnsigned(1) == 1L) nsat++
        }
        val sigIds = mutableListOf<Int>()
        for (j in 1..32) {
            if (br.readUnsigned(1) == 1L) sigIds.add(j)
        }

        val sigNames = sigIds.map { id -> sys.sigTable?.getOrNull(id - 1)?.takeIf { it.isNotEmpty() } ?: "#$id" }

        return MsmHeaderInfo(staId, towSec, glonassDow, glonassTodSec, sync, nsat, sigIds.size, sigNames)
    }
}
