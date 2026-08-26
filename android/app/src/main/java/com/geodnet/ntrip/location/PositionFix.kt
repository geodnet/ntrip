package com.geodnet.ntrip.location

/** Where a [PositionFix] came from -- determines priority in [LocationFixAggregator]. */
enum class FixSource { BLE, PHONE }

/** A single "best available position" sample, regardless of whether it came from the BLE RTK
 * receiver or the phone's own GPS. Feeds both [MockLocationProvider] and the NMEA TCP server. */
data class PositionFix(
    val source: FixSource,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double,
    val fixQuality: Int,
    val numSatellites: Int,
    val hdop: Double,
    val timestampMs: Long,
    /** GGA field 13 (age of differential corrections, seconds) -- 0.0 for a [FixSource.PHONE] fix
     * (no differential concept there) or when the BLE receiver didn't report it. */
    val diffAgeSec: Double = 0.0,
    /** GGA field 14 (differential reference station ID) -- 0 for [FixSource.PHONE] or when
     * unreported; many receivers report 0 even with a valid fix, so treat 0 as "unknown". */
    val diffStationId: Int = 0,
)
