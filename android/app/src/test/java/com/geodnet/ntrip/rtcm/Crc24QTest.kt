package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc24QTest {

    @Test
    fun `matches independently computed reference value`() {
        // D3 00 03 41 42 43 -> 0xA4F833, verified against an independent Python CRC-24Q
        // implementation, not just self-consistency with this code.
        val data = byteArrayOf(0xD3.toByte(), 0x00, 0x03, 0x41, 0x42, 0x43)
        assertEquals(0xA4F833, Crc24Q.compute(data, 0, data.size))
    }

    @Test
    fun `empty range is zero`() {
        assertEquals(0, Crc24Q.compute(ByteArray(3), 0, 0))
    }
}
