package com.geodnet.ntrip.location

import android.content.Context
import com.geodnet.ntrip.logging.LogPaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Appends each finalized [StaticSegment] to a CSV file under `logs/yyyy-MM-dd/` (GPS time),
 * matching the folder and filename convention of [com.geodnet.ntrip.logging.RawBinaryLogger]
 * and [com.geodnet.ntrip.logging.GnssRawLogger].
 */
class SegmentLogger(private val context: Context) {

    private var currentMountpoint: String = "AUTO"
    private var activeFile: File? = null
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun setMountpoint(mountpoint: String) {
        val sanitized = mountpoint.trim()
        if (sanitized.isNotEmpty() && sanitized != currentMountpoint) {
            currentMountpoint = sanitized
            activeFile = null // Rotate file when mountpoint changes
        }
    }

    @Synchronized
    fun append(segment: StaticSegment) {
        try {
            val file = getOrCreateFile()
            val isNewFile = !file.exists() || file.length() == 0L
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

    private fun getOrCreateFile(): File {
        activeFile?.let { return it }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs/${LogPaths.dateFolder()}")
        dir.mkdirs()
        val file = File(dir, "${LogPaths.timestampPrefix()}-${LogPaths.sanitizeForFilename(currentMountpoint)}-static.csv")
        activeFile = file
        return file
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
