package com.geodnet.ntrip.ble

/**
 * Parses $--GGA / $--RMC / $--GST NMEA sentences from a BLE RTK receiver (talker ID is ignored --
 * matches GN/GP/GL/GA/GB and any other 2-letter prefix, not just the "GN" the readme calls out,
 * since which talker ID a receiver uses depends on its firmware/constellation config).
 */
object NmeaParser {

    /** Parses one line (with or without the trailing checksum/CRLF). Returns null if the line
     * isn't a `$...` sentence, fails checksum validation (when a checksum is present), or isn't
     * one of the sentence types we understand. */
    fun parse(rawLine: String): NmeaSentence? {
        val line = rawLine.trim()
        if (!line.startsWith("$") || line.length < 6) return null

        val body: String
        val checksumIdx = line.indexOf('*')
        if (checksumIdx >= 0) {
            val expected = line.substring(checksumIdx + 1).trim().takeWhile { it.isLetterOrDigit() }
            val actual = checksum(line.substring(1, checksumIdx))
            if (!expected.equals(actual.toString(16).uppercase().padStart(2, '0'), ignoreCase = true)) {
                return null
            }
            body = line.substring(1, checksumIdx)
        } else {
            body = line.substring(1)
        }

        val fields = body.split(",")
        val sentenceId = fields[0]
        if (sentenceId.length < 5) return null
        val type = sentenceId.substring(sentenceId.length - 3) // last 3 chars, ignoring the talker ID

        return try {
            when (type) {
                "GGA" -> parseGga(fields)
                "RMC" -> parseRmc(fields)
                "GST" -> parseGst(fields)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun checksum(s: String): Int {
        var cs = 0
        for (c in s) cs = cs xor c.code
        return cs
    }

    /** ddmm.mmmm (lat) / dddmm.mmmm (lon) -> signed decimal degrees. Same arithmetic either way:
     * the last two digits before the decimal point are always minutes, everything before that
     * is degrees. */
    private fun toDecimalDegrees(field: String, dir: String): Double {
        if (field.isEmpty()) return 0.0
        val value = field.toDouble()
        val degrees = Math.floor(value / 100.0)
        val minutes = value - degrees * 100.0
        val decimal = degrees + minutes / 60.0
        return if (dir == "S" || dir == "W") -decimal else decimal
    }

    private fun field(fields: List<String>, i: Int): String = fields.getOrElse(i) { "" }

    private fun parseGga(f: List<String>): NmeaSentence.Gga? {
        if (field(f, 2).isEmpty() || field(f, 4).isEmpty()) return null
        return NmeaSentence.Gga(
            utcTime = field(f, 1),
            latitude = toDecimalDegrees(field(f, 2), field(f, 3)),
            longitude = toDecimalDegrees(field(f, 4), field(f, 5)),
            fixQuality = field(f, 6).toIntOrNull() ?: 0,
            numSatellites = field(f, 7).toIntOrNull() ?: 0,
            hdop = field(f, 8).toDoubleOrNull() ?: 0.0,
            altitudeM = field(f, 9).toDoubleOrNull() ?: 0.0,
            geoidSeparationM = field(f, 11).toDoubleOrNull() ?: 0.0,
        )
    }

    private fun parseRmc(f: List<String>): NmeaSentence.Rmc? {
        val status = field(f, 2).firstOrNull() ?: return null
        if (status != 'A' && status != 'V') return null
        return NmeaSentence.Rmc(
            utcTime = field(f, 1),
            status = status,
            latitude = if (field(f, 3).isNotEmpty()) toDecimalDegrees(field(f, 3), field(f, 4)) else 0.0,
            longitude = if (field(f, 5).isNotEmpty()) toDecimalDegrees(field(f, 5), field(f, 6)) else 0.0,
            speedKnots = field(f, 7).toDoubleOrNull() ?: 0.0,
            courseDeg = field(f, 8).toDoubleOrNull() ?: 0.0,
            date = field(f, 9),
        )
    }

    private fun parseGst(f: List<String>): NmeaSentence.Gst {
        return NmeaSentence.Gst(
            utcTime = field(f, 1),
            rmsResidualM = field(f, 2).toDoubleOrNull() ?: 0.0,
            semiMajorStdDevM = field(f, 3).toDoubleOrNull() ?: 0.0,
            semiMinorStdDevM = field(f, 4).toDoubleOrNull() ?: 0.0,
            orientationDeg = field(f, 5).toDoubleOrNull() ?: 0.0,
            latStdDevM = field(f, 6).toDoubleOrNull() ?: 0.0,
            lonStdDevM = field(f, 7).toDoubleOrNull() ?: 0.0,
            altStdDevM = field(f, 8).toDoubleOrNull() ?: 0.0,
        )
    }
}
