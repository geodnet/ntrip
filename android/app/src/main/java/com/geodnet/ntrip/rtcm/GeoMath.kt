package com.geodnet.ntrip.rtcm

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Llh(val latDeg: Double, val lonDeg: Double, val heightM: Double)
data class Ecef(val x: Double, val y: Double, val z: Double)

/** WGS84 ellipsoid conversions, ported from node/ntrip_client.js's ecefToLlh()/llhToEcef(). */
object GeoMath {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val E2 = F * (2 - F)

    fun ecefToLlh(x: Double, y: Double, z: Double): Llh {
        val lon = atan2(y, x)
        val p = sqrt(x * x + y * y)
        var lat = atan2(z, p * (1 - E2))
        var height = 0.0
        repeat(5) {
            val sinLat = sin(lat)
            val n = A / sqrt(1 - E2 * sinLat * sinLat)
            height = p / cos(lat) - n
            lat = atan2(z, p * (1 - (E2 * n) / (n + height)))
        }
        return Llh(Math.toDegrees(lat), Math.toDegrees(lon), height)
    }

    fun llhToEcef(latDeg: Double, lonDeg: Double, heightM: Double): Ecef {
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)
        val sinLat = sin(latRad)
        val n = A / sqrt(1 - E2 * sinLat * sinLat)
        return Ecef(
            x = (n + heightM) * cos(latRad) * cos(lonRad),
            y = (n + heightM) * cos(latRad) * sin(lonRad),
            z = (n * (1 - E2) + heightM) * sinLat,
        )
    }
}

/**
 * Utility for parsing NMEA UTC time tags and computing exact transmission/epoch latency
 * between Rover NMEA GGA time tag and Base Station RTCM observation time tag.
 */
object TimeTagMath {

    /**
     * Parses NMEA GGA utcTime (e.g. "123456.00" or "123456.789") to UTC seconds of the day (0.0..86399.999).
     */
    fun parseGgaUtcTimeToSecondsOfDay(utcTime: String?): Double? {
        if (utcTime.isNullOrBlank()) return null
        val trimmed = utcTime.trim()
        if (trimmed.length < 6) return null
        val hours = trimmed.substring(0, 2).toDoubleOrNull() ?: return null
        val minutes = trimmed.substring(2, 4).toDoubleOrNull() ?: return null
        val seconds = trimmed.substring(4).toDoubleOrNull() ?: return null
        return hours * 3600.0 + minutes * 60.0 + seconds
    }

    /**
     * Calculates latency in seconds between Rover NMEA GGA time tag and Base Station RTCM observation time tag:
     * Latency = Rover UTC Time Tag (sec of day) - Base Station RTCM Time Tag (sec of day).
     * Handles 24-hour midnight wrap-around safely.
     */
    fun calculateLatencySec(roverGgaUtcTime: String?, baseTimeTagUtcSec: Double?): Double? {
        if (roverGgaUtcTime.isNullOrBlank() || baseTimeTagUtcSec == null) return null
        val roverUtcSec = parseGgaUtcTimeToSecondsOfDay(roverGgaUtcTime) ?: return null
        var diff = roverUtcSec - baseTimeTagUtcSec
        // Handle midnight boundary wrapping (+/- 12 hours)
        if (diff < -43200.0) diff += 86400.0
        else if (diff > 43200.0) diff -= 86400.0
        return diff.coerceAtLeast(0.0)
    }
}
