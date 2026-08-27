package com.geodnet.ntrip.location

import kotlin.math.cos
import kotlin.math.sqrt

/** A period during which the rover held still, as detected by [StaticSegmentDetector]. */
data class StaticSegment(
    val meanLatDeg: Double,
    val meanLonDeg: Double,
    val meanAltM: Double,
    val stdDevNorthM: Double,
    val stdDevEastM: Double,
    val stdDevUpM: Double,
    val stdDev2dM: Double,
    val stdDev3dM: Double,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val epochCount: Int,
    val durationSec: Double = ((endTimeMs - startTimeMs) / 1000.0).coerceAtLeast(0.0),
    val stdDevM: Double = stdDev2dM,
)

/**
 * Detects periods where consecutive fixes stay within [distanceCutoffM] of the cluster's running
 * mean position for at least [minDurationMs] -- e.g. a surveyor holding a stakeout point, per
 * readme.md's "Static segment auto-detection" (default 5cm / 5s).
 *
 * Optimized for high sample rates (>5Hz, 10Hz, 20Hz) with O(1) centroid tracking and sample-rate
 * independent time-based dropout tolerance.
 *
 * Computes centroid coordinates to 9 decimal places, height to 4 decimal places, and full
 * standard deviations in NEU (North, East, Up) in meters to 4 decimal places.
 */
class StaticSegmentDetector(
    private val distanceCutoffM: Double = 0.05,
    private val minDurationMs: Long = 5_000,
    private val rtkFixOnly: Boolean = true,
    private val maxNonRtkDropoutMs: Long = 3_000,
    private val maxNonRtkDropoutEpochs: Int = 15,
) {
    private val cluster = mutableListOf<PositionFix>()
    private var sumLat = 0.0
    private var sumLon = 0.0
    private var sumAlt = 0.0
    private var nonRtkDropoutCount = 0
    private var nonRtkDropoutStartMs: Long? = null

    /** Feeds one new fix in. Returns the finalized [StaticSegment] if this fix's distance from
     * the running cluster mean broke the cluster (and the broken cluster was long enough to
     * count), else null. The fix that broke the cluster always starts a fresh candidate cluster
     * of its own -- it doesn't just get dropped. */
    fun accept(fix: PositionFix): StaticSegment? {
        if (rtkFixOnly && fix.fixQuality != 4) {
            nonRtkDropoutCount++
            val startMs = nonRtkDropoutStartMs ?: fix.timestampMs.also { nonRtkDropoutStartMs = it }
            val dropoutDurationMs = fix.timestampMs - startMs

            // Allow brief transient float/single drops without destroying the active static cluster.
            // If the dropout persists beyond time limit (default 3s) or epoch limit, finalize and clear.
            if (dropoutDurationMs > maxNonRtkDropoutMs || nonRtkDropoutCount > maxNonRtkDropoutEpochs) {
                val finished = finalizeClusterIfLongEnough()
                clearCluster()
                nonRtkDropoutCount = 0
                nonRtkDropoutStartMs = null
                return finished
            }
            return null
        }

        // RTK fix received -> reset dropout counter
        nonRtkDropoutCount = 0
        nonRtkDropoutStartMs = null

        if (cluster.isEmpty()) {
            addFix(fix)
            return null
        }

        val meanLat = sumLat / cluster.size
        val meanLon = sumLon / cluster.size
        val meanAlt = sumAlt / cluster.size
        val meanFix = cluster.last().copy(latitude = meanLat, longitude = meanLon, altitudeM = meanAlt)

        if (horizontalDistanceMeters(meanFix, fix) <= distanceCutoffM) {
            addFix(fix)
            return null
        }

        val finished = finalizeClusterIfLongEnough()
        clearCluster()
        addFix(fix)
        return finished
    }

    private fun addFix(fix: PositionFix) {
        cluster.add(fix)
        sumLat += fix.latitude
        sumLon += fix.longitude
        sumAlt += fix.altitudeM
    }

    private fun clearCluster() {
        cluster.clear()
        sumLat = 0.0
        sumLon = 0.0
        sumAlt = 0.0
    }

    /** Returns the currently active static segment if holding still for >= minDurationMs,
     * allowing real-time detection without needing to break the cluster first. */
    fun currentSegment(): StaticSegment? {
        if (cluster.size < 2) return null
        val start = cluster.first().timestampMs
        val end = cluster.last().timestampMs
        if (end - start < minDurationMs) return null
        return buildSegment(start, end)
    }

    /** Finalizes whatever cluster is currently open (if it's long enough), for callers that want
     * a segment still in progress when the session ends -- otherwise it's simply never reported. */
    fun flush(): StaticSegment? {
        val finished = finalizeClusterIfLongEnough()
        clearCluster()
        nonRtkDropoutCount = 0
        nonRtkDropoutStartMs = null
        return finished
    }

    private fun finalizeClusterIfLongEnough(): StaticSegment? {
        if (cluster.size < 2) return null
        val start = cluster.first().timestampMs
        val end = cluster.last().timestampMs
        if (end - start < minDurationMs) return null
        return buildSegment(start, end)
    }

    private fun buildSegment(start: Long, end: Long): StaticSegment {
        val n = cluster.size.toDouble()
        val meanLat = sumLat / n
        val meanLon = sumLon / n
        val meanAlt = sumAlt / n
        val latMeanRad = Math.toRadians(meanLat)

        var sumSqNorth = 0.0
        var sumSqEast = 0.0
        var sumSqUp = 0.0

        for (pt in cluster) {
            val dNorth = Math.toRadians(pt.latitude - meanLat) * EARTH_RADIUS_M
            val dEast = Math.toRadians(pt.longitude - meanLon) * cos(latMeanRad) * EARTH_RADIUS_M
            val dUp = pt.altitudeM - meanAlt

            sumSqNorth += dNorth * dNorth
            sumSqEast += dEast * dEast
            sumSqUp += dUp * dUp
        }

        val stdNorth = sqrt(sumSqNorth / n)
        val stdEast = sqrt(sumSqEast / n)
        val stdUp = sqrt(sumSqUp / n)
        val std2d = sqrt(stdNorth * stdNorth + stdEast * stdEast)
        val std3d = sqrt(stdNorth * stdNorth + stdEast * stdEast + stdUp * stdUp)

        return StaticSegment(
            meanLatDeg = meanLat,
            meanLonDeg = meanLon,
            meanAltM = meanAlt,
            stdDevNorthM = stdNorth,
            stdDevEastM = stdEast,
            stdDevUpM = stdUp,
            stdDev2dM = std2d,
            stdDev3dM = std3d,
            startTimeMs = start,
            endTimeMs = end,
            epochCount = cluster.size,
            durationSec = ((end - start) / 1000.0).coerceAtLeast(0.0),
            stdDevM = std2d,
        )
    }

    private fun horizontalDistanceMeters(a: PositionFix, b: PositionFix): Double {
        val latAvgRad = Math.toRadians((a.latitude + b.latitude) / 2)
        val dx = Math.toRadians(b.longitude - a.longitude) * cos(latAvgRad) * EARTH_RADIUS_M
        val dy = Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS_M
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
    }
}
