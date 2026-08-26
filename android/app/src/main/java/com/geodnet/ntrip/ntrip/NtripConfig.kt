package com.geodnet.ntrip.ntrip

/** Connection + position settings for [NtripClient], mirroring the Node.js client's config. */
data class NtripConfig(
    val host: String = "rtk.geodnet.com",
    val port: Int = 2101,
    val mountpoint: String = "AUTO",
    val username: String = "",
    val password: String = "",
    val latitude: Double = 37.398583184829945,
    val longitude: Double = -121.97869617705001,
    val altitude: Double = 100.0,
    val numSatellites: Int = 20,
    val hdop: Double = 1.0,
    val ggaIntervalMs: Long = 5000L,
    val useLiveLocation: Boolean = true,
)

/** Overrides [NtripConfig]'s static lat/lon/alt/numSatellites/hdop for one GGA upload with a live
 * position -- see [NtripClient]'s `livePosition` param and readme.md's "Smart Phone Location GGA
 * Fallback" (the BLE-fix-else-phone-fallback logic itself lives in
 * `location.LocationFixAggregator`; this is just the shape NtripClient needs it in, kept in the
 * `ntrip` package so NtripClient doesn't have to depend on the `location` package's `PositionFix`). */
data class GgaPositionOverride(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double,
    val numSatellites: Int,
    val hdop: Double,
)
