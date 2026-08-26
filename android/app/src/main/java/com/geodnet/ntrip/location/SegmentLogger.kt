package com.geodnet.ntrip.location

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Appends each finalized [StaticSegment] to a CSV file under app-private storage, so readme.md's
 * "save the auto-detected segments" means something durable rather than an in-memory list that's
 * gone the moment the app process dies. Deliberately just a flat CSV, not the structured
 * per-day/per-mountpoint folder layout readme.md describes for the (not yet built) Dual Data
 * Logger -- that's a separate, larger feature; this is the minimal "save" this one asks for.
 */
class SegmentLogger(context: Context) {

    private val file = File(context.getExternalFilesDir(null) ?: context.filesDir, "static_segments.csv")
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun append(segment: StaticSegment) {
        try {
            val isNewFile = !file.exists()
            FileOutputStream(file, true).use { out ->
                if (isNewFile) {
                    out.write(HEADER.toByteArray())
                }
                out.write(rowFor(segment).toByteArray())
            }
        } catch (_: IOException) {
            // best-effort logging -- a write failure shouldn't take down the detector/UI
        }
    }

    private fun rowFor(segment: StaticSegment): String =
        "${isoFormat.format(Date(segment.startTimeMs))}," +
            "${isoFormat.format(Date(segment.endTimeMs))}," +
            "${segment.epochCount}," +
            "${segment.meanLatDeg},${segment.meanLonDeg},${segment.meanAltM}," +
            "${segment.stdDevM}\n"

    companion object {
        private const val HEADER = "start_utc,end_utc,epoch_count,mean_lat_deg,mean_lon_deg,mean_alt_m,std_dev_m\n"
    }
}
