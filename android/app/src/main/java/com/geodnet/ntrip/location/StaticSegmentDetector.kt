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
 * Computes centroid coordinates to 9 decimal places, height to 4 decimal places, and full
 * standard deviations in NEU (North, East, Up) in meters to 4 decimal places.
 */
class StaticSegmentDetector(
    private val distanceCutoffM: Double = 0.05,
    private val minDurationMs: Long = 5_000,
    private val rtkFixOnly: Boolean = true,
    private val maxNonRtkDropoutEpochs: Int = 3,
) {
    private val cluster = mutableListOf<PositionFix>()
    private var nonRtkDropoutCount = 0

    /** Feeds one new fix in. Returns the finalized [StaticSegment] if this fix's distance from
     * the running cluster mean broke the cluster (and the broken cluster was long enough to
     * count), else null. The fix that broke the cluster always starts a fresh candidate cluster
     * of its own -- it doesn't just get dropped. */
    fun accept(fix: PositionFix): StaticSegment? {
        if (rtkFixOnly && fix.fixQuality != 4) {
            nonRtkDropoutCount++
            // Allow brief transient float/single drops without destroying the active static cluster.
            // If the dropout persists beyond maxNonRtkDropoutEpochs, finalize and clear.
            if (nonRtkDropoutCount > maxNonRtkDropoutEpochs) {
                val finished = finalizeClusterIfLongEnough()
                cluster.clear()
                nonRtkDropoutCount = 0
                return finished
            }
            return null
        }

        // RTK fix received -> reset dropout counter
        nonRtkDropoutCount = 0

        if (cluster.isEmpty()) {
            cluster += fix
            return null
        }
        if (horizontalDistanceMeters(clusterMean(), fix) <= distanceCutoffM) {
            cluster += fix
            return null
        }
        val finished = finalizeClusterIfLongEnough()
        cluster.clear()
        cluster += fix
        return finished
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
        cluster.clear()
        nonRtkDropoutCount = 0
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
        val mean = clusterMean()
        val latMeanRad = Math.toRadians(mean.latitude)

        var sumSqNorth = 0.0
        var sumSqEast = 0.0
        var sumSqUp = 0.0
        val n = cluster.size.toDouble()

        for (pt in cluster) {
            val dNorth = Math.toRadians(pt.latitude - mean.latitude) * EARTH_RADIUS_M
            val dEast = Math.toRadians(pt.longitude - mean.longitude) * cos(latMeanRad) * EARTH_RADIUS_M
            val dUp = pt.altitudeM - mean.altitudeM

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
            meanLatDeg = mean.latitude,
            meanLonDeg = mean.longitude,
            meanAltM = mean.altitudeM,
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

    private fun clusterMean(): PositionFix {
        val lat = cluster.map { it.latitude }.average()
        val lon = cluster.map { it.longitude }.average()
        val alt = cluster.map { it.altitudeM }.average()
        return cluster.last().copy(latitude = lat, longitude = lon, altitudeM = alt)
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
