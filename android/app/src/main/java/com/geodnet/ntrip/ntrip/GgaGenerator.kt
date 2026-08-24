package com.geodnet.ntrip.ntrip

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.floor

/**
 * Builds an NMEA $GPGGA sentence (with checksum) from the given position, mirroring the Node.js
 * client's generateGGA(). The degrees-to-minutes conversion must multiply the fractional degree
 * by 60 (not 100) -- the original Node client had this bug, since fixed; see node/CLAUDE.md.
 */
object GgaGenerator {

    fun generate(config: NtripConfig, staId: Int = 0, age: Double = 0.0): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val time = "%02d%02d%02d.000".format(now.hour, now.minute, now.second)

        val latDir = if (config.latitude >= 0) "N" else "S"
        val lonDir = if (config.longitude >= 0) "E" else "W"

        val latitude = abs(config.latitude)
        val longitude = abs(config.longitude)

        val latDeg = floor(latitude).toInt()
        val lonDeg = floor(longitude).toInt()

        val latMin = (latitude - latDeg) * 60.0
        val lonMin = (longitude - lonDeg) * 60.0

        val latStr = "%02d%07.4f".format(latDeg, latMin)
        val lonStr = "%03d%07.4f".format(lonDeg, lonMin)

        val gga = "\$GPGGA,$time,$latStr,$latDir,$lonStr,$lonDir,1," +
            "${config.numSatellites},%.2f,%.2f,M,0.0,M,%.2f,$staId".format(
                config.hdop, config.altitude, age
            )
        val checksum = checksum(gga)
        return "$gga*${checksum.toString(16).uppercase().padStart(2, '0')}"
    }

    private fun checksum(sentence: String): Int {
        var cs = 0
        for (i in 1 until sentence.length) {
            cs = cs xor sentence[i].code
        }
        return cs
    }
}
