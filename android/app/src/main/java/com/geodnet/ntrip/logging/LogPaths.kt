package com.geodnet.ntrip.logging

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Shared GPS-time date-folder/filename-prefix logic for [RawBinaryLogger] and [GnssRawLogger],
 * per readme.md's `logs/yyyy-MM-dd/` (GPS time) folder structure.
 *
 * GPS time lags UTC by a whole number of leap seconds -- 18s has been correct since the last leap
 * second was inserted (2016-12-31 23:59:60 UTC introduced the 19th total). That's hardcoded here
 * rather than looked up from a live leap-second table, which is a real limitation if a new leap
 * second is ever announced/inserted -- see android/CLAUDE.md.
 */
object LogPaths {
    private const val GPS_UTC_OFFSET_SECONDS = 18L
    private val dateFolderFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    private val timestampPrefixFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC)

    fun gpsNow(): Instant = Instant.now().plusSeconds(GPS_UTC_OFFSET_SECONDS)

    fun dateFolder(instant: Instant = gpsNow()): String = dateFolderFormat.format(instant)

    fun timestampPrefix(instant: Instant = gpsNow()): String = timestampPrefixFormat.format(instant)

    /** Filenames embed user/config-derived strings (mountpoint, BLE device id) -- strip anything
     * that isn't filesystem-safe rather than trusting them verbatim. */
    fun sanitizeForFilename(s: String): String {
        val cleaned = s.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return cleaned.ifEmpty { "unknown" }
    }
}
