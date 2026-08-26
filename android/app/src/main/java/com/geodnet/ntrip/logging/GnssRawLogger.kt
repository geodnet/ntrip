package com.geodnet.ntrip.logging

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.LocationManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

data class GnssRawLoggerState(
    val active: Boolean = false,
    val filePath: String? = null,
    val measurementEventCount: Long = 0,
    val navMessageCount: Long = 0,
    val errorMessage: String? = null,
)

/**
 * readme.md's "Android GNSS Raw Measurement, Ephemeris and IMU Logger": dumps the phone's own
 * GNSS chipset raw measurements (`GnssMeasurementsEvent`/`GnssClock`), navigation subframes
 * (`GnssNavigationMessage` -- the ephemeris bits), and uncalibrated accelerometer/gyroscope
 * samples to one text file, in the row-per-line "# Raw,..." / "Nav,..." / "UncalAccel,..." /
 * "UncalGyro,..." convention used by Google's reference GnssLogger app -- the format Google's GNSS
 * Analysis Tool, RTKLIB's `convbin`, and most other PPK tooling expect. Columns are taken directly
 * from the real `GnssMeasurement`/`GnssClock`/`GnssNavigationMessage`/`SensorEvent` API surface,
 * not guessed at.
 *
 * Independent of the Ntrip connection and the BLE receiver -- this logs the phone's own internal
 * GNSS chip (for later PPK against whatever base-station RTCM was logged in parallel by
 * [RawBinaryLogger]), so it runs whether or not either of those is active.
 */
class GnssRawLogger(private val context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private var out: OutputStream? = null
    private val _state = MutableStateFlow(GnssRawLoggerState())
    val state: StateFlow<GnssRawLoggerState> = _state.asStateFlow()

    private val measurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) = writeMeasurements(event)

        @Suppress("DEPRECATION")
        override fun onStatusChanged(status: Int) {
            if (status == GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED) {
                _state.update { it.copy(errorMessage = "GNSS raw measurements not supported on this device") }
            }
        }
    }

    private val navMessageCallback = object : GnssNavigationMessage.Callback() {
        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) = writeNavMessage(event)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = writeSensorEvent(event)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (_state.value.active) return
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs/${LogPaths.dateFolder()}")
        dir.mkdirs()
        val file = File(dir, "${LogPaths.timestampPrefix()}-raw-gnss.txt")
        val stream: FileOutputStream
        try {
            stream = FileOutputStream(file, true)
            writeLine(stream, HEADER)
        } catch (_: IOException) {
            _state.value = GnssRawLoggerState(errorMessage = "Failed to open $file")
            return
        }
        out = stream

        @Suppress("DEPRECATION")
        val measurementsRegistered = locationManager?.registerGnssMeasurementsCallback(measurementsCallback) ?: false
        @Suppress("DEPRECATION")
        locationManager?.registerGnssNavigationMessageCallback(navMessageCallback)

        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        val gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        accel?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }

        _state.value = GnssRawLoggerState(
            active = true,
            filePath = file.path,
            errorMessage = if (!measurementsRegistered) "GNSS measurements callback registration failed" else null,
        )
    }

    fun stop() {
        try {
            locationManager?.unregisterGnssMeasurementsCallback(measurementsCallback)
        } catch (_: Exception) {
        }
        try {
            @Suppress("DEPRECATION")
            locationManager?.unregisterGnssNavigationMessageCallback(navMessageCallback)
        } catch (_: Exception) {
        }
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {
        }
        try {
            out?.close()
        } catch (_: IOException) {
        }
        out = null
        _state.value = GnssRawLoggerState()
    }

    private fun writeMeasurements(event: GnssMeasurementsEvent) {
        val stream = out ?: return
        val clock = event.clock
        for (m in event.measurements) {
            writeLine(stream, rawRow(clock, m))
        }
        _state.update { it.copy(measurementEventCount = it.measurementEventCount + 1) }
    }

    private fun rawRow(clock: GnssClock, m: GnssMeasurement): String = listOf(
        "Raw",
        SystemClock.elapsedRealtime(),
        clock.timeNanos,
        if (clock.hasLeapSecond()) clock.leapSecond else "",
        if (clock.hasTimeUncertaintyNanos()) clock.timeUncertaintyNanos else "",
        if (clock.hasFullBiasNanos()) clock.fullBiasNanos else "",
        if (clock.hasBiasNanos()) clock.biasNanos else "",
        if (clock.hasBiasUncertaintyNanos()) clock.biasUncertaintyNanos else "",
        if (clock.hasDriftNanosPerSecond()) clock.driftNanosPerSecond else "",
        if (clock.hasDriftUncertaintyNanosPerSecond()) clock.driftUncertaintyNanosPerSecond else "",
        clock.hardwareClockDiscontinuityCount,
        m.svid,
        m.timeOffsetNanos,
        m.state,
        m.receivedSvTimeNanos,
        m.receivedSvTimeUncertaintyNanos,
        m.cn0DbHz,
        m.pseudorangeRateMetersPerSecond,
        m.pseudorangeRateUncertaintyMetersPerSecond,
        m.accumulatedDeltaRangeState,
        m.accumulatedDeltaRangeMeters,
        m.accumulatedDeltaRangeUncertaintyMeters,
        if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz else "",
        m.multipathIndicator,
        m.constellationType,
    ).joinToString(",")

    private fun writeNavMessage(event: GnssNavigationMessage) {
        val stream = out ?: return
        val dataHex = event.data.joinToString("") { "%02X".format(it) }
        val row = listOf("Nav", event.svid, event.type, event.messageId, event.submessageId, dataHex, event.status)
        writeLine(stream, row.joinToString(","))
        _state.update { it.copy(navMessageCount = it.navMessageCount + 1) }
    }

    private fun writeSensorEvent(event: SensorEvent) {
        val stream = out ?: return
        val label = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "UncalAccel"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "UncalGyro"
            else -> return
        }
        val v = event.values
        val row = listOf(
            label, System.currentTimeMillis(), event.timestamp,
            v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f },
            v.getOrElse(3) { 0f }, v.getOrElse(4) { 0f }, v.getOrElse(5) { 0f },
        )
        writeLine(stream, row.joinToString(","))
    }

    private fun writeLine(stream: OutputStream, line: String) {
        try {
            stream.write((line + "\n").toByteArray(Charsets.US_ASCII))
        } catch (_: IOException) {
        }
    }

    companion object {
        private val HEADER = listOf(
            "# Raw,ElapsedRealtimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,BiasNanos," +
                "BiasUncertaintyNanos,DriftNanosPerSecond,DriftUncertaintyNanosPerSecond," +
                "HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos," +
                "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond," +
                "PseudorangeRateUncertaintyMetersPerSecond,AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters," +
                "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,MultipathIndicator,ConstellationType",
            "# Nav,Svid,Type,MessageId,SubmessageId,DataHex,Status",
            "# UncalAccel,utcTimeMillis,elapsedRealtimeNanos,UncalAccelXMps2,UncalAccelYMps2,UncalAccelZMps2," +
                "BiasXMps2,BiasYMps2,BiasZMps2",
            "# UncalGyro,utcTimeMillis,elapsedRealtimeNanos,UncalGyroXRadPerSec,UncalGyroYRadPerSec," +
                "UncalGyroZRadPerSec,DriftXRadPerSec,DriftYRadPerSec,DriftZRadPerSec",
        ).joinToString("\n")
    }
}
