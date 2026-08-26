package com.geodnet.ntrip.logging

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class RawLoggerState(
    val active: Boolean = false,
    val baseFilePath: String? = null,
    val roveFilePath: String? = null,
    val baseBytesWritten: Long = 0,
    val roveBytesWritten: Long = 0,
)

/**
 * readme.md's "Raw Binary Stream Logger": logs the caster's raw RTCM byte stream and the BLE
 * receiver's raw incoming byte stream to separate files under a GPS-date-named folder --
 * `logs/yyyy-MM-dd/yyyy-MM-dd-HH-mm-ss-<mountpoint>-base.log` /
 * `...-<roverId>-rove.log`.
 *
 * Filenames are fixed at [start] time from whatever mountpoint/roverId are known *then* --
 * reconnecting mid-session to a different mountpoint, or a BLE receiver connecting only after
 * logging already started, does not reopen new files. That's a deliberate simplification for a
 * first pass (see android/CLAUDE.md), not an attempt at seamless mid-session file rotation.
 */
class RawBinaryLogger(private val context: Context) {

    private var baseOut: FileOutputStream? = null
    private var roveOut: FileOutputStream? = null
    private val _state = MutableStateFlow(RawLoggerState())
    val state: StateFlow<RawLoggerState> = _state.asStateFlow()

    fun start(mountpoint: String, roverId: String) {
        stop()
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs/${LogPaths.dateFolder()}")
        dir.mkdirs()
        val prefix = LogPaths.timestampPrefix()
        val baseFile = File(dir, "$prefix-${LogPaths.sanitizeForFilename(mountpoint)}-base.log")
        val roveFile = File(dir, "$prefix-${LogPaths.sanitizeForFilename(roverId)}-rove.log")
        try {
            baseOut = FileOutputStream(baseFile, true)
            roveOut = FileOutputStream(roveFile, true)
            _state.value = RawLoggerState(active = true, baseFilePath = baseFile.path, roveFilePath = roveFile.path)
        } catch (_: IOException) {
            stop()
        }
    }

    /** Raw RTCM bytes as received from the caster, framed in the GEODNET `$GEOD,<timestampMs>,<len>,<data>\r\n` pattern. */
    fun logBaseBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val now = System.currentTimeMillis()
            val header = "\$GEOD,$now,${bytes.size},".toByteArray(Charsets.US_ASCII)
            val trailer = "\r\n".toByteArray(Charsets.US_ASCII)
            baseOut?.apply {
                write(header)
                write(bytes)
                write(trailer)
                flush()
            }
            val totalWritten = header.size + bytes.size + trailer.size
            _state.update { it.copy(baseBytesWritten = it.baseBytesWritten + totalWritten) }
        } catch (_: IOException) {
        }
    }

    /** Raw bytes as received from the BLE RTK receiver, framed in the GEODNET `$GEOD,<timestampMs>,<len>,<data>\r\n` pattern. */
    fun logRoveBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val now = System.currentTimeMillis()
            val header = "\$GEOD,$now,${bytes.size},".toByteArray(Charsets.US_ASCII)
            val trailer = "\r\n".toByteArray(Charsets.US_ASCII)
            roveOut?.apply {
                write(header)
                write(bytes)
                write(trailer)
                flush()
            }
            val totalWritten = header.size + bytes.size + trailer.size
            _state.update { it.copy(roveBytesWritten = it.roveBytesWritten + totalWritten) }
        } catch (_: IOException) {
        }
    }

    fun stop() {
        try {
            baseOut?.close()
        } catch (_: IOException) {
        }
        try {
            roveOut?.close()
        } catch (_: IOException) {
        }
        baseOut = null
        roveOut = null
        _state.value = RawLoggerState()
    }
}
