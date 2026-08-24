package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EphemerisDecodersTest {

    private val gpsEpochMs = utcMillis(1980, 1, 6)
    private val bdtEpochMs = utcMillis(2006, 1, 1)
    private val weekMs = 7L * 86400L * 1000L

    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, 0, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun `decodes 1019 GPS ephemeris`() {
        val now = System.currentTimeMillis()
        val gpsWeekFull = (now - gpsEpochMs) / weekMs
        val week10 = (gpsWeekFull % 1024).toInt()
        val toes = 300000.0
        val prn = 21
        val svh = 0

        val w = TestBitWriter(61) // payload is exactly 488 bits (12 + 476) per RTKLIB
        w.writeUnsigned(1019, 12)
        w.writeUnsigned(prn, 6)
        w.writeUnsigned(week10, 10)
        w.writeUnsigned(0, 4); w.writeUnsigned(0, 2); w.writeUnsigned(0, 14); w.writeUnsigned(5, 8)
        w.writeUnsigned(0, 16)
        w.writeUnsigned(0, 8); w.writeUnsigned(0, 16); w.writeUnsigned(0, 22)
        w.writeUnsigned(0, 10)
        w.writeUnsigned(0, 16); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32)
        w.writeUnsigned(Math.round(toes / 16), 16)
        w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 24)
        w.writeUnsigned(0, 8)
        w.writeUnsigned(svh, 6)
        w.writeUnsigned(0, 1); w.writeUnsigned(0, 1)

        val e = EphemerisDecoders.decode1019(w.buf, now)
        val expectedToeMs = gpsEpochMs + gpsWeekFull * weekMs + (toes * 1000).toLong() - 18000

        assertEquals("GPS", e.sys)
        assertEquals(prn, e.prn)
        assertEquals(svh, e.svh)
        assertEquals(expectedToeMs, e.toeMs)
    }

    @Test
    fun `decodes 1042 BeiDou ephemeris with correct svh bit`() {
        val bdtWeekFull = (System.currentTimeMillis() - bdtEpochMs) / weekMs
        val toes = 400008.0
        val prn = 6
        val svh = 1

        val w = TestBitWriter(64)
        w.writeUnsigned(1042, 12)
        w.writeUnsigned(prn, 6)
        w.writeUnsigned(bdtWeekFull, 13)
        w.writeUnsigned(0, 4); w.writeUnsigned(0, 14); w.writeUnsigned(3, 5)
        w.writeUnsigned(0, 17)
        w.writeUnsigned(0, 11); w.writeUnsigned(0, 22); w.writeUnsigned(0, 24)
        w.writeUnsigned(0, 5)
        w.writeUnsigned(0, 18); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 18); w.writeUnsigned(0, 32); w.writeUnsigned(0, 18); w.writeUnsigned(0, 32)
        w.writeUnsigned(Math.round(toes / 8), 17)
        w.writeUnsigned(0, 18); w.writeUnsigned(0, 32); w.writeUnsigned(0, 18); w.writeUnsigned(0, 32); w.writeUnsigned(0, 18); w.writeUnsigned(0, 32); w.writeUnsigned(0, 24)
        w.writeUnsigned(0, 10); w.writeUnsigned(0, 10)
        w.writeUnsigned(svh, 1)

        val e = EphemerisDecoders.decode1042(w.buf)
        val expectedToeMs = bdtEpochMs + bdtWeekFull * weekMs + (toes * 1000).toLong() - 4000

        assertEquals("BeiDou", e.sys)
        assertEquals(prn, e.prn)
        assertEquals(svh, e.svh) // regression test for the buffer-too-short bug found during node.js testing
        assertEquals(expectedToeMs, e.toeMs)
    }

    @Test
    fun `decodes 1046 Galileo I_NAV combined health bits`() {
        val gpsWeekFull = (System.currentTimeMillis() - gpsEpochMs) / weekMs
        val gstWeek = (gpsWeekFull - 1024).toInt()
        val toes = 90000.0
        val prn = 14
        val e5bHs = 2; val e5bDvs = 1; val e1Hs = 3; val e1Dvs = 0

        val w = TestBitWriter(63)
        w.writeUnsigned(1046, 12)
        w.writeUnsigned(prn, 6)
        w.writeUnsigned(gstWeek, 12)
        w.writeUnsigned(0, 10); w.writeUnsigned(0, 8); w.writeUnsigned(0, 14); w.writeUnsigned(0, 14)
        w.writeUnsigned(0, 6); w.writeUnsigned(0, 21); w.writeUnsigned(0, 31)
        w.writeUnsigned(0, 16); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32)
        w.writeUnsigned(Math.round(toes / 60), 14)
        w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 16); w.writeUnsigned(0, 32); w.writeUnsigned(0, 24)
        w.writeUnsigned(0, 10); w.writeUnsigned(0, 10)
        w.writeUnsigned(e5bHs, 2); w.writeUnsigned(e5bDvs, 1); w.writeUnsigned(e1Hs, 2); w.writeUnsigned(e1Dvs, 1)

        val e = EphemerisDecoders.decodeGalileo(w.buf, isInav = true)
        val expectedSvh = (e5bHs shl 7) or (e5bDvs shl 6) or (e1Hs shl 1) or e1Dvs

        assertEquals("Galileo", e.sys)
        assertEquals(prn, e.prn)
        assertEquals(expectedSvh, e.svh)
    }

    @Test
    fun `GLONASS toe brackets to within one day of reference time`() {
        val now = System.currentTimeMillis()
        val prn = 8
        val svh = 0
        val nowMoscowSecOfDay = ((now / 1000 % 86400) + 3 * 3600) % 86400
        val targetSecOfDay = (nowMoscowSecOfDay - 600 + 86400) % 86400
        val tb = Math.round(targetSecOfDay / 900.0).toInt()

        val w = TestBitWriter(48)
        w.writeUnsigned(1020, 12)
        w.writeUnsigned(prn, 6)
        w.writeUnsigned(7, 5); w.writeUnsigned(0, 2); w.writeUnsigned(0, 2)
        w.writeUnsigned(0, 5); w.writeUnsigned(0, 6); w.writeUnsigned(0, 1)
        w.writeUnsigned(svh, 1)
        w.writeUnsigned(0, 1)
        w.writeUnsigned(tb, 7)

        val e = EphemerisDecoders.decode1020(w.buf, now)

        assertEquals("GLONASS", e.sys)
        assertEquals(prn, e.prn)
        // tb has 900s (15 min) granularity, so allow up to half a step of rounding error
        val ageSec = (now - e.toeMs) / 1000
        assert(ageSec in 0..1200) { "expected age near 600s, got ${ageSec}s" }
    }
}
