package com.geodnet.ntrip.rtcm

/**
 * MSB-first bit reader over a ByteArray, for RTCM3 payload field extraction. Uses Long (not Int)
 * because some fields (e.g. the 38-bit ECEF coordinates in 1005/1006) exceed 32 bits.
 */
class RtcmBitReader(private val buf: ByteArray) {
    private var bitPos = 0

    fun readUnsigned(nbits: Int): Long {
        var value = 0L
        repeat(nbits) {
            val bytePos = bitPos shr 3
            val bitInByte = 7 - (bitPos and 7)
            val bit = (buf[bytePos].toInt() shr bitInByte) and 1
            value = value * 2 + bit
            bitPos++
        }
        return value
    }

    fun readSigned(nbits: Int): Long {
        val value = readUnsigned(nbits)
        val signBit = 1L shl (nbits - 1)
        return if (value >= signBit) value - (1L shl nbits) else value
    }

    fun readString(nbytes: Int): String {
        val sb = StringBuilder(nbytes)
        repeat(nbytes) { sb.append(readUnsigned(8).toInt().toChar()) }
        return sb.toString()
    }

    fun skip(nbits: Int) {
        bitPos += nbits
    }
}
