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
    fun `msm sync flag correctly groups and completes epochs matching gps 1074 count`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)

        // Epoch 1: 1074 (sync=true), 1084 (sync=true), 1094 (sync=true), 1124 (sync=false)
        engine.onObservationMessage(nowNanos = 0L, msgType = 1074, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12345.0)
        engine.onObservationMessage(nowNanos = 50_000_000L, msgType = 1084, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12345.0)
        engine.onObservationMessage(nowNanos = 100_000_000L, msgType = 1094, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12345.0)
        engine.onObservationMessage(nowNanos = 150_000_000L, msgType = 1124, isMoreMessagesInEpoch = false, baseTimeTagUtcSec = 12345.0)

        val s1 = engine.snapshot(nowNanos = 150_000_000L)
        assertEquals(1L, s1.epochsCompleted)
        assertEquals(150.0, s1.lastEpochSpanMs!!, 1e-3)

        // Epoch 2: next second
        engine.onObservationMessage(nowNanos = 1_000_000_000L, msgType = 1074, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_050_000_000L, msgType = 1084, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_100_000_000L, msgType = 1094, isMoreMessagesInEpoch = true, baseTimeTagUtcSec = 12346.0)
        engine.onObservationMessage(nowNanos = 1_180_000_000L, msgType = 1124, isMoreMessagesInEpoch = false, baseTimeTagUtcSec = 12346.0)

        val s2 = engine.snapshot(nowNanos = 1_180_000_000L)
        assertEquals(2L, s2.epochsCompleted)
        assertEquals(180.0, s2.lastEpochSpanMs!!, 1e-3)
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
