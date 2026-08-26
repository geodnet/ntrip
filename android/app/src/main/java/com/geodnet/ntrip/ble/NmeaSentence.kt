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
        /** Age of differential corrections, seconds (field 13) -- 0.0 if absent/not applicable
         * (e.g. no differential fix). */
        val diffAgeSec: Double = 0.0,
        /** Differential reference station ID (field 14) -- 0 if absent. Many receivers report 0
         * here even with a valid differential fix, so treat 0 as "unknown", not "station #0". */
        val diffStationId: Int = 0,
        val rawSentence: String = "",
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

    /** GSA carries PDOP/HDOP/VDOP together -- GGA only has HDOP, so this is the only source of
     * the other two (readme.md explicitly calls out all three). */
    data class Gsa(
        val fixType: Int, // 1 = no fix, 2 = 2D, 3 = 3D
        val pdop: Double,
        val hdop: Double,
        val vdop: Double,
    ) : NmeaSentence()
}
