package com.geodnet.ntrip.ntrip

import com.geodnet.ntrip.ble.NmeaParser
import com.geodnet.ntrip.ble.NmeaSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips generated sentences through [NmeaParser] rather than reimplementing NMEA checksum
 * math here -- NmeaParser already validates the checksum independently (NmeaParserTest) and
 * returns null on mismatch, so a successful round-trip is a real correctness signal, not just
 * self-consistency with GgaGenerator's own checksum() function.
 */
class GgaGeneratorTest {

    @Test
    fun `round-trips northern-eastern hemisphere position through NmeaParser`() {
        val line = GgaGenerator.generate(
            latitude = 48.1173,
            longitude = 11.516667,
            altitude = 545.4,
            numSatellites = 8,
            hdop = 0.9,
        )

        val sentence = NmeaParser.parse(line)
        assertNotNull("checksum should validate", sentence)
        val gga = sentence as NmeaSentence.Gga
        assertEquals(48.1173, gga.latitude, 1e-4)
        assertEquals(11.516667, gga.longitude, 1e-4)
        assertEquals(8, gga.numSatellites)
        assertEquals(0.9, gga.hdop, 1e-9)
        assertEquals(545.4, gga.altitudeM, 1e-2)
    }

    @Test
    fun `round-trips southern-western hemisphere position through NmeaParser`() {
        val line = GgaGenerator.generate(
            latitude = -37.398583184829945,
            longitude = -121.97869617705001,
            altitude = 100.0,
            numSatellites = 20,
            hdop = 1.0,
        )

        val gga = NmeaParser.parse(line) as? NmeaSentence.Gga
        assertNotNull("checksum should validate", gga)
        assertEquals(-37.398583, gga!!.latitude, 1e-5)
        assertEquals(-121.978697, gga.longitude, 1e-5)
    }

    @Test
    fun `degrees-to-minutes conversion multiplies fractional degrees by 60, not 100`() {
        // Regression guard for the bug node/CLAUDE.md and this file's own doc comment call out:
        // 0.5 degrees must become 30.0 minutes, not 50.0.
        val line = GgaGenerator.generate(
            latitude = 10.5,
            longitude = 20.5,
            altitude = 0.0,
            numSatellites = 4,
            hdop = 1.0,
        )
        val fields = line.split(",")
        assertTrue("latitude field should encode 30.0000 minutes", fields[2].endsWith("30.0000"))
        assertTrue("longitude field should encode 30.0000 minutes", fields[4].endsWith("30.0000"))
    }

    @Test
    fun `config overload matches the explicit-params overload`() {
        val config = NtripConfig(latitude = 1.0, longitude = 2.0, altitude = 3.0, numSatellites = 5, hdop = 0.5)
        val viaConfig = GgaGenerator.generate(config)
        val viaParams = GgaGenerator.generate(1.0, 2.0, 3.0, 5, 0.5)
        // Drop the leading "$GPGGA" and time fields (time is wall-clock-dependent and could
        // theoretically tick over between the two calls above) before comparing the rest.
        fun withoutTime(line: String) = line.split(",").drop(2).joinToString(",")
        assertEquals(withoutTime(viaConfig), withoutTime(viaParams))
    }
}
