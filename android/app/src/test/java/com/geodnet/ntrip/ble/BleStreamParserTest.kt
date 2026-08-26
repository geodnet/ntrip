package com.geodnet.ntrip.ble

import com.geodnet.ntrip.rtcm.TestBitWriter
import com.geodnet.ntrip.rtcm.TestFrameBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BleStreamParserTest {

    @Test
    fun `parses mixed NMEA sentences and RTCM3 frames in incoming BLE stream`() {
        val receiver = BleRtkReceiver(null)

        // 1. Build a synthetic RTCM 1005 frame
        val bitWriter1005 = TestBitWriter(19)
        bitWriter1005.writeUnsigned(1005, 12)
        bitWriter1005.writeUnsigned(1234, 12) // Station ID 1234
        bitWriter1005.writeUnsigned(0, 6)
        bitWriter1005.writeUnsigned(1, 1) // GPS
        bitWriter1005.writeUnsigned(0, 1)
        bitWriter1005.writeUnsigned(0, 1)
        bitWriter1005.writeSigned(0, 38)
        bitWriter1005.writeUnsigned(0, 1)
        bitWriter1005.writeUnsigned(0, 1)
        bitWriter1005.writeSigned(0, 38)
        bitWriter1005.writeUnsigned(0, 2)
        bitWriter1005.writeSigned(0, 38)
        val rtcm1005Frame = TestFrameBuilder.buildFrame(bitWriter1005.buf)

        // 2. Build a synthetic RTCM 1077 MSM7 frame
        val bitWriter1077 = TestBitWriter(10)
        bitWriter1077.writeUnsigned(1077, 12)
        bitWriter1077.writeUnsigned(1234, 12)
        bitWriter1077.writeUnsigned(300000, 30)
        val rtcm1077Frame = TestFrameBuilder.buildFrame(bitWriter1077.buf)

        // 3. Create NMEA sentences
        val nmea1 = "\$GNGGA,123519,4807.038,N,01131.000,E,4,12,0.8,545.4,M,46.9,M,1.0,1234*7D\r\n".toByteArray(Charsets.US_ASCII)
        val nmea2 = "\$GNGST,123519,0.015,0.012,0.010,45.0,0.012,0.010,0.025*58\r\n".toByteArray(Charsets.US_ASCII)

        // 4. Send mixed stream: NMEA1 + RTCM1005 + NMEA2 + RTCM1077 in split chunks
        val mixedStream = nmea1 + rtcm1005Frame + nmea2 + rtcm1077Frame

        // Chunk into 16-byte slices to test reassembly across BLE MTU boundaries
        var offset = 0
        while (offset < mixedStream.size) {
            val end = (offset + 16).coerceAtMost(mixedStream.size)
            receiver.handleIncoming(mixedStream.copyOfRange(offset, end))
            offset = end
        }

        val state = receiver.state.value
        assertEquals(4, state.messagesReceived)

        // Verify NMEA counts
        assertEquals(1, state.nmeaCounts["GNGGA"])
        assertEquals(1, state.nmeaCounts["GNGST"])

        // Verify RTCM counts
        assertEquals(1, state.rtcmCounts["RTCM 1005"])
        assertEquals(1, state.rtcmCounts["RTCM 1077"])

        // Verify GGA fix was correctly parsed
        assertNotNull(state.latestFix)
        assertEquals(4, state.latestFix?.fixQuality)
        assertEquals(12, state.latestFix?.numSatellites)
        assertEquals(1234, state.latestFix?.diffStationId)
    }
}
