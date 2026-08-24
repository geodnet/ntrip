package com.geodnet.ntrip.rtcm

/** RTCM3 CRC-24Q, poly 0x1864CFB, init 0 -- ported from node/ntrip_client.js's crc24q(). */
object Crc24Q {
    private const val POLY = 0x1864CFB

    fun compute(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 16)
            repeat(8) {
                crc = crc shl 1
                if (crc and 0x1000000 != 0) {
                    crc = crc xor POLY
                }
            }
            crc = crc and 0xFFFFFF
        }
        return crc
    }
}
