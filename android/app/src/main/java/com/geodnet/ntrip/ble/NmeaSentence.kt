package com.geodnet.ntrip.ble

/** Parsed fields from the NMEA sentences the BLE RTK receiver integration cares about. */
sealed class NmeaSentence {
    data class Gga(
        val utcTime: String,
        val latitude: Double,
        val longitude: Double,
        val fixQuality: Int,
        val numSatellites: Int,
        val hdop: Double,
        val altitudeM: Double,
        val geoidSeparationM: Double,
    ) : NmeaSentence()

    data class Rmc(
        val utcTime: String,
        val status: Char, // 'A' = active/valid, 'V' = void
        val latitude: Double,
        val longitude: Double,
        val speedKnots: Double,
        val courseDeg: Double,
        val date: String, // ddmmyy
    ) : NmeaSentence()

    data class Gst(
        val utcTime: String,
        val rmsResidualM: Double,
        val semiMajorStdDevM: Double,
        val semiMinorStdDevM: Double,
        val orientationDeg: Double,
        val latStdDevM: Double,
        val lonStdDevM: Double,
        val altStdDevM: Double,
    ) : NmeaSentence()
}
