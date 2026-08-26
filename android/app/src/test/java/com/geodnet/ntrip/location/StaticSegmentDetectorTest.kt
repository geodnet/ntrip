package com.geodnet.ntrip.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticSegmentDetectorTest {

    private fun fix(latitude: Double, longitude: Double, altitudeM: Double, timestampMs: Long) = PositionFix(
        source = FixSource.BLE,
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeM,
        fixQuality = 4,
        numSatellites = 20,
        hdop = 0.8,
        timestampMs = timestampMs,
    )

    // ~37.0 deg N: 1e-7 deg latitude is about 1.1cm, well under the 5cm default cutoff, and a good
    // way to construct "essentially the same point" test fixtures without hardcoding meters.
    private val base = fix(37.0, -122.0, 10.0, 0L)

    @Test
    fun `a cluster held for over the minimum duration is reported as a segment`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 5_000)
        // Five fixes, 2s apart, all within a centimeter of each other -- 8s total, over the 5s minimum.
        var result: StaticSegment? = null
        for (i in 0..4) {
            result = detector.accept(fix(37.0 + 1e-8 * i, -122.0, 10.0, i * 2_000L))
        }
        // The cluster is still open (nothing has broken it yet) -- nothing reported mid-stream.
        assertNull(result)

        // A fix 1 meter away breaks the cluster and should finalize the held segment.
        val breaking = detector.accept(fix(37.0 + 0.00001, -122.0, 10.0, 8_000L))
        assertNotNull("a segment held for 8s should be reported", breaking)
        assertEquals(5, breaking!!.epochCount)
        assertEquals(0L, breaking.startTimeMs)
        assertEquals(8_000L, breaking.endTimeMs)
        assertTrue("mean latitude should be close to the cluster", kotlin.math.abs(breaking.meanLatDeg - 37.0) < 1e-6)
    }

    @Test
    fun `a cluster shorter than the minimum duration is discarded, not reported`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 5_000)
        detector.accept(base)
        detector.accept(fix(37.0, -122.0, 10.0, 1_000L)) // 1s later, still within cutoff

        // Breaks after only 1s held -- below the 5s minimum, so no segment.
        val result = detector.accept(fix(37.0 + 0.001, -122.0, 10.0, 1_500L))
        assertNull(result)
    }

    @Test
    fun `movement beyond the cutoff starts a fresh cluster rather than being dropped`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 1_000)
        detector.accept(base)
        detector.accept(fix(37.0, -122.0, 10.0, 2_000L)) // holds for 2s -- reportable once broken

        val moved = fix(37.001, -122.001, 10.0, 3_000L) // ~150m away, well outside cutoff
        val firstSegment = detector.accept(moved)
        assertNotNull(firstSegment)

        // The break point itself should now seed a new cluster, not be discarded.
        val stillNear = fix(37.001, -122.001, 10.0, 5_000L)
        val secondSegment = detector.accept(stillNear)
        // Only 2s held so far on the new cluster -- below the default-ish 1s minimum? It's exactly
        // at 2s >= 1s here, but this call doesn't break the cluster (stillNear is within cutoff of
        // moved), so nothing is finalized yet.
        assertNull(secondSegment)
    }

    @Test
    fun `flush finalizes a still-open cluster that never got broken`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 1_000)
        detector.accept(base)
        detector.accept(fix(37.0, -122.0, 10.0, 2_000L))

        val flushed = detector.flush()
        assertNotNull(flushed)
        assertEquals(2, flushed!!.epochCount)
    }

    @Test
    fun `flush with no points is a no-op`() {
        val detector = StaticSegmentDetector()
        assertNull(detector.flush())
    }

    @Test
    fun `transient non-RTK dropout is tolerated while sustained dropout finalizes cluster`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 1_000, rtkFixOnly = true, maxNonRtkDropoutEpochs = 2)
        detector.accept(base.copy(fixQuality = 4, timestampMs = 0L))
        detector.accept(base.copy(fixQuality = 4, timestampMs = 2_000L))

        // Live static segment is available
        val current = detector.currentSegment()
        assertNotNull(current)
        assertEquals(2, current!!.epochCount)
        assertEquals(2.0, current.durationSec, 1e-6)

        // 1st transient float fix -> ignored (dropout 1/2)
        val drop1 = detector.accept(base.copy(fixQuality = 5, timestampMs = 3_000L))
        assertNull(drop1)
        assertNotNull(detector.currentSegment())

        // 2nd transient float fix -> ignored (dropout 2/2)
        val drop2 = detector.accept(base.copy(fixQuality = 5, timestampMs = 4_000L))
        assertNull(drop2)
        assertNotNull(detector.currentSegment())

        // RTK fix recovers!
        detector.accept(base.copy(fixQuality = 4, timestampMs = 5_000L))
        assertEquals(3, detector.currentSegment()!!.epochCount)
        assertEquals(5.0, detector.currentSegment()!!.durationSec, 1e-6)

        // Sustained float dropouts (> 2 epochs) -> 1st, 2nd ignored, 3rd breaks and finalizes
        assertNull(detector.accept(base.copy(fixQuality = 5, timestampMs = 6_000L)))
        assertNull(detector.accept(base.copy(fixQuality = 5, timestampMs = 7_000L)))
        val finalized = detector.accept(base.copy(fixQuality = 5, timestampMs = 8_000L))
        assertNotNull(finalized)
        assertEquals(3, finalized!!.epochCount)

        // Cluster is now cleared
        assertNull(detector.currentSegment())
        assertNull(detector.flush())
    }

    @Test
    fun `calculates precise NEU standard deviations and duration`() {
        val detector = StaticSegmentDetector(distanceCutoffM = 0.05, minDurationMs = 1_000)
        // 3 fixes with slight North, East, and Altitude variation
        detector.accept(fix(37.000000000, -122.000000000, 10.0000, 0L))
        detector.accept(fix(37.000000100, -122.000000100, 10.0100, 1_000L))
        detector.accept(fix(37.000000200, -122.000000200, 10.0200, 2_000L))

        val seg = detector.flush()
        assertNotNull(seg)
        assertEquals(37.0000001, seg!!.meanLatDeg, 1e-9)
        assertEquals(-122.0000001, seg.meanLonDeg, 1e-9)
        assertEquals(10.01, seg.meanAltM, 1e-4)
        assertEquals(2.0, seg.durationSec, 1e-3)
        assertTrue(seg.stdDevNorthM > 0.0)
        assertTrue(seg.stdDevEastM > 0.0)
        assertTrue(seg.stdDevUpM > 0.0)
        assertTrue(seg.stdDev2dM > 0.0)
        assertTrue(seg.stdDev3dM >= seg.stdDev2dM)
    }
}
