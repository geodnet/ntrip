package com.geodnet.ntrip.rtcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpochLatencyEngineTest {

    private val gapNanos = 200_000_000L // matches the class's own default

    @Test
    fun `first message latency is measured from connection start`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 1_000_000_000L)
        engine.onObservationMessage(nowNanos = 1_050_000_000L) // 50ms later

        assertEquals(50L, engine.snapshot(nowNanos = 1_050_000_000L).firstMessageLatencyMs)
    }

    @Test
    fun `messages within the gap threshold belong to the same epoch`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 50_000_000L) // +50ms, well under 200ms gap
        engine.onObservationMessage(nowNanos = 120_000_000L) // +70ms more, still under gap

        val snapshot = engine.snapshot(nowNanos = 120_000_000L)
        assertEquals(3, snapshot.epochMessageCount)
        assertEquals(0L, snapshot.epochsCompleted) // still open -- nothing has closed it yet
        assertNull(snapshot.lastEpochSpanMs)
    }

    @Test
    fun `a gap beyond the threshold closes the epoch with the correct sub-millisecond span`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 12_500_000L) // +12.5ms -- last message of epoch 1
        engine.onObservationMessage(nowNanos = 300_000_000L) // +287.5ms -- past the 200ms gap, starts epoch 2

        val snapshot = engine.snapshot(nowNanos = 300_000_000L)
        assertEquals(1L, snapshot.epochsCompleted)
        assertEquals(12.5, snapshot.lastEpochSpanMs!!, 1e-9)
        assertEquals(1, snapshot.epochMessageCount) // epoch 2 has just the one message so far
    }

    @Test
    fun `last message age reflects time since the most recent observation message`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L)

        assertEquals(25L, engine.snapshot(nowNanos = 25_000_000L).lastMessageAgeMs)
    }

    @Test
    fun `reset clears all accumulated state`() {
        val engine = EpochLatencyEngine(gapNanos)
        engine.onConnectionStart(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 0L)
        engine.onObservationMessage(nowNanos = 300_000_000L)

        engine.reset()

        val snapshot = engine.snapshot(nowNanos = 300_000_000L)
        assertNull(snapshot.firstMessageLatencyMs)
        assertNull(snapshot.lastEpochSpanMs)
        assertEquals(0, snapshot.epochMessageCount)
        assertEquals(0L, snapshot.epochsCompleted)
        assertEquals(0L, snapshot.lastMessageAgeMs)
    }
}
