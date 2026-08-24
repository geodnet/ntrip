package com.geodnet.ntrip.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserTest {

    @Test
    fun `parses a valid GGA sentence`() {
        val line = "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*59"
        val sentence = NmeaParser.parse(line)

        assertTrue(sentence is NmeaSentence.Gga)
        val gga = sentence as NmeaSentence.Gga
        assertEquals(48.1173, gga.latitude, 1e-4)
        assertEquals(11.516667, gga.longitude, 1e-4)
        assertEquals(1, gga.fixQuality)
        assertEquals(8, gga.numSatellites)
        assertEquals(0.9, gga.hdop, 1e-9)
        assertEquals(545.4, gga.altitudeM, 1e-9)
        assertEquals(46.9, gga.geoidSeparationM, 1e-9)
    }

    @Test
    fun `parses southern and western hemisphere signs correctly`() {
        val line = "\$GPGGA,123519,3723.9150,S,12158.7218,W,1,20,1.00,100.00,M,0.0,M,,*79"
        val gga = NmeaParser.parse(line) as NmeaSentence.Gga

        assertTrue("latitude should be negative", gga.latitude < 0)
        assertTrue("longitude should be negative", gga.longitude < 0)
        assertEquals(-37.398583, gga.latitude, 1e-5)
        assertEquals(-121.978697, gga.longitude, 1e-5)
    }

    @Test
    fun `parses a valid RMC sentence`() {
        val line = "\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*74"
        val rmc = NmeaParser.parse(line) as NmeaSentence.Rmc

        assertEquals('A', rmc.status)
        assertEquals(48.1173, rmc.latitude, 1e-4)
        assertEquals(11.516667, rmc.longitude, 1e-4)
        assertEquals(22.4, rmc.speedKnots, 1e-9)
        assertEquals(84.4, rmc.courseDeg, 1e-9)
        assertEquals("230394", rmc.date)
    }

    @Test
    fun `parses a valid GST sentence`() {
        val line = "\$GNGST,024603.00,3.2,1.5,1.0,89.4,0.8,0.6,1.2*76"
        val gst = NmeaParser.parse(line) as NmeaSentence.Gst

        assertEquals(3.2, gst.rmsResidualM, 1e-9)
        assertEquals(1.5, gst.semiMajorStdDevM, 1e-9)
        assertEquals(1.0, gst.semiMinorStdDevM, 1e-9)
        assertEquals(0.8, gst.latStdDevM, 1e-9)
        assertEquals(0.6, gst.lonStdDevM, 1e-9)
        assertEquals(1.2, gst.altStdDevM, 1e-9)
    }

    @Test
    fun `rejects a sentence with a bad checksum`() {
        val line = "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*FF"
        assertNull(NmeaParser.parse(line))
    }

    @Test
    fun `rejects non-sentence input`() {
        assertNull(NmeaParser.parse("not a sentence"))
        assertNull(NmeaParser.parse(""))
        assertNull(NmeaParser.parse("\$"))
    }

    @Test
    fun `ignores unrecognized sentence types without crashing`() {
        // GSA is a real NMEA type we don't parse; should just return null.
        assertNull(NmeaParser.parse("\$GNGSA,A,3,01,02,03,,,,,,,,,,2.0,1.0,1.7*3D"))
    }

    @Test
    fun `accepts sentences without a checksum`() {
        val line = "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"
        val gga = NmeaParser.parse(line) as NmeaSentence.Gga
        assertEquals(48.1173, gga.latitude, 1e-4)
    }
}
