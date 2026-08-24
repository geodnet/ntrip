package com.geodnet.ntrip.rtcm

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor

/**
 * Time-system epochs/offsets used to turn ephemeris week+TOE into a UTC timestamp (ms), ported
 * from node/ntrip_client.js. Leap-second offsets are approximate and only meant for display --
 * GPS-UTC has been 18s since the 2016-12-31 leap second (none since, as of early 2026), and
 * BDT-UTC = GPS-UTC - GPS-BDT(14s) = 4s.
 */
object EphemerisTime {
    val GPS_EPOCH_MS = utcMillis(1980, 1, 6)
    private val BDT_EPOCH_MS = utcMillis(2006, 1, 1)
    private const val GPS_UTC_LEAP_SEC = 18L
    private const val BDT_UTC_LEAP_SEC = 4L
    private const val WEEK_MS = 7L * 86400L * 1000L

    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, 0, 0, 0)
        return cal.timeInMillis
    }

    /** Resolve a truncated (mod 2^bits) week number to a full week count, by picking the
     * candidate closest to the week implied by [refMs]. */
    fun resolveWeek(rawWeek: Int, bits: Int, epochMs: Long, refMs: Long): Long {
        val modulus = 1L shl bits
        val approxWeek = floor((refMs - epochMs).toDouble() / WEEK_MS).toLong()
        val rolloverBase = floor(approxWeek.toDouble() / modulus).toLong() * modulus
        var best = rolloverBase + rawWeek
        for (cand in longArrayOf(best - modulus, best, best + modulus)) {
            if (abs(cand - approxWeek) < abs(best - approxWeek)) {
                best = cand
            }
        }
        return best
    }

    fun toeFromGpsWeek(week: Long, toesSec: Double): Long =
        GPS_EPOCH_MS + week * WEEK_MS + (toesSec * 1000).toLong() - GPS_UTC_LEAP_SEC * 1000

    fun toeFromBdtWeek(week: Long, toesSec: Double): Long =
        BDT_EPOCH_MS + week * WEEK_MS + (toesSec * 1000).toLong() - BDT_UTC_LEAP_SEC * 1000

    /** GLONASS has no week/TOE; tb is a 15-minute-interval index (0-96) within the current day
     * in Moscow time (UTC+3). Bracket against refMs's UTC day since the message carries no date. */
    fun toeFromGlonassTb(tb: Int, refMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = refMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStartUtcMs = cal.timeInMillis

        var candidateMs = dayStartUtcMs + (tb * 900L - 3 * 3600L) * 1000L
        val halfDayMs = 12L * 3600L * 1000L
        val diff = candidateMs - refMs
        if (diff < -halfDayMs) {
            candidateMs += 86400000L
        } else if (diff > halfDayMs) {
            candidateMs -= 86400000L
        }
        return candidateMs
    }
}
