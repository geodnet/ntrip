package com.geodnet.ntrip.rtcm

/** MSB-first bit writer for constructing synthetic RTCM3 payloads in tests. */
class TestBitWriter(nbytes: Int) {
    val buf = ByteArray(nbytes)
    private var bitPos = 0

    fun writeUnsigned(value: Long, nbits: Int) {
        for (i in nbits - 1 downTo 0) {
            val bit = ((value ushr i) and 1L).toInt()
            val bytePos = bitPos shr 3
            val bitInByte = 7 - (bitPos and 7)
            buf[bytePos] = (buf[bytePos].toInt() or (bit shl bitInByte)).toByte()
            bitPos++
        }
    }

    fun writeUnsigned(value: Int, nbits: Int) = writeUnsigned(value.toLong(), nbits)

    fun writeSigned(value: Long, nbits: Int) {
        val v = if (value < 0) value + (1L shl nbits) else value
        writeUnsigned(v, nbits)
    }

    fun writeString(s: String) {
        for (c in s) writeUnsigned(c.code, 8)
    }
}

/** Helpers to wrap a payload (already including the 12-bit message number) into a full,
 * correctly CRC'd RTCM3 frame, as would arrive from a real caster. */
object TestFrameBuilder {

    fun buildFrame(payload: ByteArray): ByteArray {
        val len = payload.size
        val header = byteArrayOf(0xD3.toByte(), ((len shr 8) and 0x03).toByte(), (len and 0xFF).toByte())
        val withHeader = header + payload
        val crc = Crc24Q.compute(withHeader, 0, withHeader.size)
        val crcBytes = byteArrayOf(
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte(),
        )
        return withHeader + crcBytes
    }
}
