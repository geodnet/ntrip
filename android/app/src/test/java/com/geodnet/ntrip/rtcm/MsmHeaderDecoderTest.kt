package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MsmHeaderDecoderTest {

    private fun buildMsm7(msgType: Int, staId: Int, towSec: Double, satIds: List<Int>, sigIds: List<Int>): ByteArray {
        val headerBits = 12 + 12 + 30 + 1 + 3 + 7 + 2 + 2 + 1 + 3 + 64 + 32
        val cellBits = satIds.size * sigIds.size
        val nbytes = (headerBits + cellBits + 7) / 8
        val w = TestBitWriter(nbytes)
        w.writeUnsigned(msgType, 12)
        w.writeUnsigned(staId, 12)
        w.writeUnsigned(Math.round(towSec * 1000), 30)
        w.writeUnsigned(0, 1); w.writeUnsigned(3, 3); w.writeUnsigned(0, 7)
        w.writeUnsigned(0, 2); w.writeUnsigned(0, 2); w.writeUnsigned(0, 1); w.writeUnsigned(0, 3)
        for (j in 1..64) w.writeUnsigned(if (satIds.contains(j)) 1 else 0, 1)
        for (j in 1..32) w.writeUnsigned(if (sigIds.contains(j)) 1 else 0, 1)
        repeat(cellBits) { w.writeUnsigned(1, 1) }
        return w.buf
    }

    @Test
    fun `decodes GPS MSM7 header with resolved signal names`() {
        // ids: 1C(2), 1W(4), 2W(10), 5Q(23)
        val payload = buildMsm7(1077, 777, 123456.789, listOf(1, 2, 5, 32), listOf(2, 4, 10, 23))
        val sys = getMsmSystem(1077)!!

        val h = MsmHeaderDecoder.decode(payload, sys)

        assertEquals(777, h.staId)
        assertEquals(4, h.nsat)
        assertEquals(4, h.nsig)
        assertEquals(listOf("1C", "1W", "2W", "5Q"), h.sigNames)
        assertNotNull(h.towSec)
        assertEquals(123456.789, h.towSec!!, 0.001)
        assertNull(h.glonassDow)
    }

    @Test
    fun `unmapped signal id falls back to hash-id label`() {
        // GLONASS id 11 (3I) was removed to match RTKLIB -- see node CLAUDE.md
        val payload = buildMsm7(1087, 2, 0.0, listOf(1), listOf(2, 11))
        val sys = getMsmSystem(1087)!!

        val h = MsmHeaderDecoder.decode(payload, sys)

        assertEquals(listOf("1C", "#11"), h.sigNames)
    }

    @Test
    fun `BeiDou MSM resolves updated signal ids`() {
        // 2I(2), 6I(8), 5P(23), 7D(25), 1D(30)
        val payload = buildMsm7(1127, 555, 100000.0, listOf(1, 2, 3), listOf(2, 8, 23, 25, 30))
        val sys = getMsmSystem(1127)!!

        val h = MsmHeaderDecoder.decode(payload, sys)

        assertEquals(listOf("2I", "6I", "5P", "7D", "1D"), h.sigNames)
    }

    @Test
    fun `unknown MSM base or out-of-range ordinal returns null system`() {
        assertNull(getMsmSystem(1070)) // ordinal 0, reserved
        assertNull(getMsmSystem(1200)) // no such base
    }
}
