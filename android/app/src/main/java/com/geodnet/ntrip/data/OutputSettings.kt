package com.geodnet.ntrip.data

/** Persisted enabled/disabled state for the mock location provider and the two TCP servers --
 * separate from [com.geodnet.ntrip.ntrip.NtripConfig] since these toggle app-level output
 * features rather than caster connection settings. */
data class OutputSettings(
    val mockLocationEnabled: Boolean = false,
    val nmeaServerEnabled: Boolean = false,
    val rtcmServerEnabled: Boolean = false,
    /** Map screen's "Base Station & Baseline Vector Toggle" (readme.md section 4) -- off by
     * default: hide the base marker/baseline and auto-follow the rover. */
    val showBaseStation: Boolean = false,
    /** readme.md's "GNSS Ephemeris Filtering" -- drop 1019/1020/1041/1042/1044/1045/1046 frames
     * before forwarding RTCM to the BLE receiver, to save serial bandwidth. Off by default so
     * enabling BLE doesn't silently start dropping messages a receiver might actually want. */
    val filterEphemerisForBle: Boolean = false,
    /** Raw Binary Stream Logger (readme.md section 6.1): logs the caster's raw RTCM bytes and the
     * BLE receiver's raw incoming bytes to `logs/yyyy-MM-dd/...-base.log` /
     * `...-rove.log`. Off by default -- it writes files to disk, so it's opt-in like the other
     * output toggles. */
    val rawLoggingEnabled: Boolean = false,
    /** Android GNSS Raw Measurement/Ephemeris/IMU Logger (readme.md section 6.2). Independent of
     * rawLoggingEnabled -- this logs the phone's own GNSS chipset, not the BLE receiver/caster. */
    val gnssRawLoggingEnabled: Boolean = false,
)
