package com.geodnet.ntrip.location

import kotlin.math.cos
import kotlin.math.sqrt

/** A period during which the rover held still, as detected by [StaticSegmentDetector]. */
data class StaticSegment(
    val meanLatDeg: Double,
    val meanLonDeg: Double,
    val meanAltM: Double,
    val stdDevM: Double,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val epochCount: Int,
)

/**
 * Detects periods where consecutive fixes stay within [distanceCutoffM] of the cluster's running
 * mean position for at least [minDurationMs] -- e.g. a surveyor holding a stakeout point, per
 * readme.md's "Static segment auto-detection" (default 5cm / 5s).
 *
 * Not Android-dependent (pure math over [PositionFix]s), so it's unit-testable without
 * Robolectric/an emulator. Distance is a flat-earth (equirectangular) approximation, which at the
 * sub-meter clustering scale this operates at is indistinguishable from a proper geodesic
 * calculation and much cheaper.
 */
class StaticSegmentDetector(
    private val distanceCutoffM: Double = 0.15,
    private val minDurationMs: Long = 5_000,
) {
    private val cluster = mutableListOf<PositionFix>()

    /** Feeds one new fix in. Returns the finalized [StaticSegment] if this fix's distance from
     * the running cluster mean broke the cluster (and the broken cluster was long enough to
     * count), else null. The fix that broke the cluster always starts a fresh candidate cluster
     * of its own -- it doesn't just get dropped. */
    fun accept(fix: PositionFix): StaticSegment? {
        val effectiveCutoff = when (fix.fixQuality) {
            4 -> distanceCutoffM // RTK Fixed: high precision
            5 -> distanceCutoffM * 2.5 // RTK Float: medium precision
            else -> (distanceCutoffM * 6.0).coerceAtLeast(1.0) // Autonomous/Phone fallback
        }.coerceAtLeast(distanceCutoffM)

        if (cluster.isEmpty()) {
            cluster += fix
            return null
        }
        if (horizontalDistanceMeters(clusterMean(), fix) <= effectiveCutoff) {
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
        val variance = cluster.map { val d = horizontalDistanceMeters(mean, it); d * d }.average()
        return StaticSegment(
            meanLatDeg = mean.latitude,
            meanLonDeg = mean.longitude,
            meanAltM = mean.altitudeM,
            stdDevM = sqrt(variance),
            startTimeMs = start,
            endTimeMs = end,
            epochCount = cluster.size,
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
