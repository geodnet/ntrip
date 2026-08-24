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
)
