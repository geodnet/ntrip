package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpochLatencyEngineTest {

    private val gapNanos = 600_000_000L

    @Test
    fun `first message latency is measured from connection start`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 1_000_000_000L)
        engine.onObservationMessage(nowNanos = 1_050_000_000L, msgType = 1074) // 50ms later

        assertEquals(50L, engine.snapshot(nowNanos = 1_050_000_000L).firstMessageLatencyMs)
    }

    @Test
    fun `msm messages with same time tag belong to the same epoch regardless of network jitter`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)

        // Epoch 1 (time tag 12345.0s): 1074 GPS, 1084 GLO, 1094 GAL, 1124 BDS across 350ms
        engine.onObservationMessage(nowNanos = 0L, msgType = 1074, baseTimeTagUtcSec = 12345.0)
        engine.onObservationMessage(nowNanos = 50_000_000L, msgType = 1084, baseTimeTagUtcSec = 12345.0)
        engine.onObservationMessage(nowNanos = 250_000_000L, msgType = 1094, baseTimeTagUtcSec = 12345.0) // 200ms+ jitter
        engine.onObservationMessage(nowNanos = 350_000_000L, msgType = 1124, baseTimeTagUtcSec = 12345.0)

        val s1 = engine.snapshot(nowNanos = 350_000_000L)
        assertEquals(1L, s1.epochsCompleted)
        assertEquals(4, s1.epochMessageCount)

        // Epoch 2 (time tag 12346.0s): next second
        engine.onObservationMessage(nowNanos = 1_000_000_000L, msgType = 1074, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_050_000_000L, msgType = 1084, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_100_000_000L, msgType = 1094, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_150_000_000L, msgType = 1124, baseTimeTagUtcSec = 12346.0)

        val s2 = engine.snapshot(nowNanos = 1_150_000_000L)
        assertEquals(2L, s2.epochsCompleted)
        assertEquals(350.0, s2.lastEpochSpanMs!!, 1e-3)
        assertEquals(4, s2.epochMessageCount)
    }

    @Test
    fun `repeating gps msm4 type begins new epoch matching msm4 count`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)

        // Stream 5 epochs of 1074 without time tags
        for (i in 0 until 5) {
            engine.onObservationMessage(nowNanos = i * 1_000_000_000L, msgType = 1074)
            engine.onObservationMessage(nowNanos = i * 1_000_000_000L + 20_000_000L, msgType = 1084)
            engine.onObservationMessage(nowNanos = i * 1_000_000_000L + 40_000_000L, msgType = 1094)
            engine.onObservationMessage(nowNanos = i * 1_000_000_000L + 60_000_000L, msgType = 1124)
        }

        val s = engine.snapshot(nowNanos = 5_000_000_000L)
        assertEquals(5L, s.epochsCompleted)
    }

    @Test
    fun `last message age reflects time since the most recent observation message`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L, msgType = 1074)

        assertEquals(25L, engine.snapshot(nowNanos = 25_000_000L).lastMessageAgeMs)
    }

    @Test
    fun `reset clears all accumulated state`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L, msgType = 1074)
        engine.onObservationMessage(nowNanos = 1_000_000_000L, msgType = 1074)

        engine.reset()

        val snapshot = engine.snapshot(nowNanos = 1_000_000_000L)
        assertNull(snapshot.firstMessageLatencyMs)
        assertNull(snapshot.lastEpochSpanMs)
        assertEquals(0, snapshot.epochMessageCount)
        assertEquals(0L, snapshot.epochsCompleted)
        assertEquals(0L, snapshot.lastMessageAgeMs)
    }
}
