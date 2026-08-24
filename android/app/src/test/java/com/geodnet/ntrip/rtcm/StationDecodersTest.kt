package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationDecodersTest {

    private fun build1005Or1006(msgType: Int, staId: Int, x: Double, y: Double, z: Double, antHeightM: Double?): ByteArray {
        val nbytes = if (msgType == 1006) 21 else 19
        val w = TestBitWriter(nbytes)
        w.writeUnsigned(msgType, 12)
        w.writeUnsigned(staId, 12)
        w.writeUnsigned(32, 6) // itrf
        w.writeUnsigned(0, 4)
        w.writeSigned(Math.round(x / 0.0001), 38)
        w.writeUnsigned(0, 2)
        w.writeSigned(Math.round(y / 0.0001), 38)
        w.writeUnsigned(0, 2)
        w.writeSigned(Math.round(z / 0.0001), 38)
        if (msgType == 1006 && antHeightM != null) {
            w.writeUnsigned(Math.round(antHeightM / 0.0001), 16)
        }
        return w.buf
    }

    @Test
    fun `decodes 1005 station ARP`() {
        val payload = build1005Or1006(1005, 4001, -2686854.5403, -4303425.8344, 3852688.9046, null)
        val d = StationDecoders.decode1005Or1006(payload, 1005)

        assertEquals(4001, d.staId)
        assertEquals(-2686854.5403, d.x, 0.001)
        assertEquals(-4303425.8344, d.y, 0.001)
        assertEquals(3852688.9046, d.z, 0.001)
        assertNull(d.antHeightM)
    }

    @Test
    fun `decodes 1006 with antenna height`() {
        val payload = build1005Or1006(1006, 4001, -2686854.5403, -4303425.8344, 3852688.9046, 0.0500)
        val d = StationDecoders.decode1005Or1006(payload, 1006)

        assertEquals(4001, d.staId)
        assertEquals(0.0500, d.antHeightM!!, 0.0001)
    }

    @Test
    fun `ecef round-trips through llh within a millimeter`() {
        val lat = 37.398583184829945
        val lon = -121.97869617705001
        val alt = 100.0
        val ecef = GeoMath.llhToEcef(lat, lon, alt)
        val llh = GeoMath.ecefToLlh(ecef.x, ecef.y, ecef.z)

        assertEquals(lat, llh.latDeg, 1e-7)
        assertEquals(lon, llh.lonDeg, 1e-7)
        assertEquals(alt, llh.heightM, 1e-3)
    }

    @Test
    fun `decodes 1033 receiver and antenna descriptors`() {
        val antDesc = "TRM_TEST_ANT"
        val antSerial = "SN12345"
        val recType = "TESTRX"
        val recFw = "1.02"
        val recSerial = "RXSN9999"
        val totalBits = 12 + 12 +
            8 + antDesc.length * 8 + 8 +
            8 + antSerial.length * 8 +
            8 + recType.length * 8 +
            8 + recFw.length * 8 +
            8 + recSerial.length * 8
        val w = TestBitWriter((totalBits + 7) / 8)
        w.writeUnsigned(1033, 12)
        w.writeUnsigned(4001, 12)
        w.writeUnsigned(antDesc.length, 8)
        w.writeString(antDesc)
        w.writeUnsigned(1, 8) // setup id
        w.writeUnsigned(antSerial.length, 8)
        w.writeString(antSerial)
        w.writeUnsigned(recType.length, 8)
        w.writeString(recType)
        w.writeUnsigned(recFw.length, 8)
        w.writeString(recFw)
        w.writeUnsigned(recSerial.length, 8)
        w.writeString(recSerial)

        val d = StationDecoders.decode1033(w.buf)

        assertEquals(4001, d.staId)
        assertEquals(antDesc, d.antDescriptor)
        assertEquals(1, d.antSetupId)
        assertEquals(antSerial, d.antSerial)
        assertEquals(recType, d.recType)
        assertEquals(recFw, d.recFirmware)
        assertEquals(recSerial, d.recSerial)
    }
}
