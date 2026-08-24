package com.geodnet.ntrip.rtcm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtcmFrameParserTest {

    private fun build1005Frame(staId: Int): ByteArray {
        val w = TestBitWriter(19)
        w.writeUnsigned(1005, 12)
        w.writeUnsigned(staId, 12)
        w.writeUnsigned(32, 6)
        w.writeUnsigned(0, 4)
        w.writeSigned(0L, 38)
        w.writeUnsigned(0, 2)
        w.writeSigned(0L, 38)
        w.writeUnsigned(0, 2)
        w.writeSigned(0L, 38)
        return TestFrameBuilder.buildFrame(w.buf)
    }

    /**
     * [messages] has replay=0, so a collector started *after* an emission never sees it -- unlike
     * a real UI (which is already collecting when data arrives), tests must subscribe first. This
     * starts an UNDISPATCHED collector so it's guaranteed to be listening before [body] runs.
     */
    private suspend fun CoroutineScope.collectN(
        parser: RtcmFrameParser,
        n: Int,
        body: suspend () -> Unit,
    ): List<RtcmMessage> {
        val collected = mutableListOf<RtcmMessage>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collected.addAll(parser.messages.take(n).toList())
        }
        body()
        job.join()
        return collected
    }

    @Test
    fun `decodes a valid frame and separately counts a CRC failure`() = runTest {
        val parser = RtcmFrameParser { Triple(0.0, 0.0, 0.0) }

        val validFrame = build1005Frame(9)
        val badFrame = build1005Frame(10).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0xFF).toByte() }

        val messages = collectN(parser, 2) {
            parser.feed(validFrame, validFrame.size)
            parser.feed(badFrame, badFrame.size)
        }

        assertTrue(messages[0].crcOk)
        assertEquals("1005", messages[0].msgKey)
        assertTrue(messages[0].summary.contains("staid=9"))

        assertFalse(messages[1].crcOk)

        val stats = parser.stats.value
        assertEquals(1L, stats.msgsDecoded)
        assertEquals(1L, stats.msgsCrcFail)
        assertEquals((validFrame.size + badFrame.size).toLong(), stats.bytesReceived)
        assertEquals(badFrame.size.toLong(), stats.bytesCrcFail)
        assertEquals(1, stats.msgCounts["1005"])
    }

    @Test
    fun `reassembles a frame split across two TCP chunks`() = runTest {
        val parser = RtcmFrameParser { Triple(0.0, 0.0, 0.0) }
        val frame = build1005Frame(42)
        val splitAt = 10

        val messages = collectN(parser, 1) {
            parser.feed(frame.copyOfRange(0, splitAt), splitAt)
            parser.feed(frame.copyOfRange(splitAt, frame.size), frame.size - splitAt)
        }

        assertTrue(messages[0].crcOk)
        assertEquals(frame.size, messages[0].lengthBytes)
    }

    @Test
    fun `skips garbage bytes before the next sync byte`() = runTest {
        val parser = RtcmFrameParser { Triple(0.0, 0.0, 0.0) }
        val frame = build1005Frame(1)
        val withGarbagePrefix = byteArrayOf(0x00, 0x11, 0x22) + frame

        val messages = collectN(parser, 1) {
            parser.feed(withGarbagePrefix, withGarbagePrefix.size)
        }

        assertTrue(messages[0].crcOk)
        assertEquals("1005", messages[0].msgKey)
    }

    @Test
    fun `identifies IGS SSR sub-type from message 4076`() = runTest {
        val parser = RtcmFrameParser { Triple(0.0, 0.0, 0.0) }
        // IGS SSR: type=4076, version=0, IGS sub-type=21 (GPS Orbit Correction)
        val w = TestBitWriter(10)
        w.writeUnsigned(4076, 12)
        w.writeUnsigned(0, 3) // version
        w.writeUnsigned(21, 8) // IGS message number
        val frame = TestFrameBuilder.buildFrame(w.buf)

        val messages = collectN(parser, 1) {
            parser.feed(frame, frame.size)
        }

        assertEquals("4076.21", messages[0].msgKey)
        // Matches node/ntrip_client.js's wording exactly (including the doubled "SSR").
        assertEquals("IGS SSR GPS SSR Orbit Correction", RtcmMessageDescriptions.describe(messages[0].msgKey))
    }
}
