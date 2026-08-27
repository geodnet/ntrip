package com.geodnet.ntrip.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.geodnet.ntrip.ble.NmeaSentence
import com.geodnet.ntrip.ntrip.GgaGenerator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Combines the BLE RTK receiver's fix (when valid) with the phone's own GPS as a fallback
 * into a single "best fix" stream -- this is what drives [MockLocationProvider], the NMEA TCP
 * server, and the live GGA upload to the NTRIP Caster.
 *
 * Priority rules:
 * 1. If the BLE receiver is connected AND sending a valid GGA (fixQuality > 0 and coordinates != 0.0),
 *    the BLE fix is used.
 * 2. If the BLE receiver is not connected, OR is searching for satellites (fixQuality == 0 / 0.0 coordinates),
 *    OR stops sending updates, it automatically falls back to the phone's GPS position.
 */
class LocationFixAggregator(private val context: Context) {

    private val _fix = MutableStateFlow<PositionFix?>(null)
    val fix: StateFlow<PositionFix?> = _fix.asStateFlow()

    /** NMEA lines to forward to the NMEA TCP server: BLE lines verbatim when valid, else a
     * synthesized $GPGGA built from the phone fallback fix. */
    private val _nmeaLine = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val nmeaLine: SharedFlow<String> = _nmeaLine.asSharedFlow()

    private var bleConnected = false
    private var phoneListening = false
    private var latestBleFix: PositionFix? = null
    private var latestPhoneLocation: Location? = null
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val phoneListener = LocationListener { location -> onPhoneLocation(location) }

    init {
        // Start phone GPS in standby immediately so coordinates are available right from launch
        startPhoneUpdates()
    }

    /** Helper to check if a BLE fix has an active valid solution. */
    private fun isBleFixValid(fix: PositionFix?): Boolean {
        if (fix == null) return false
        if (fix.fixQuality <= 0) return false
        if (fix.latitude == 0.0 && fix.longitude == 0.0) return false
        // Stale if no update within 6 seconds
        if (System.currentTimeMillis() - fix.timestampMs > 6000L) return false
        return true
    }

    /** Call whenever the BLE receiver connects/disconnects. */
    fun onBleConnectionChanged(connected: Boolean) {
        bleConnected = connected
        if (!connected) {
            latestBleFix = null
            fallbackToPhoneFix()
        }
        startPhoneUpdates()
    }

    /** Call with every new GGA sentence parsed from the BLE receiver. */
    fun onBleFix(sentence: NmeaSentence.Gga) {
        if (!bleConnected) return

        val isValidGga = sentence.fixQuality > 0 && (sentence.latitude != 0.0 || sentence.longitude != 0.0)

        if (isValidGga) {
            val bleFix = PositionFix(
                source = FixSource.BLE,
                latitude = sentence.latitude,
                longitude = sentence.longitude,
                altitudeM = sentence.altitudeM,
                fixQuality = sentence.fixQuality,
                numSatellites = sentence.numSatellites,
                hdop = sentence.hdop,
                timestampMs = System.currentTimeMillis(),
                utcTime = sentence.utcTime,
                diffAgeSec = sentence.diffAgeSec,
                diffStationId = sentence.diffStationId,
                geoidSeparationM = sentence.geoidSeparationM,
                rawNmeaGga = sentence.rawSentence,
            )
            latestBleFix = bleFix
            _fix.value = bleFix
        } else {
            // No valid GGA from BLE receiver -> fallback to phone position
            latestBleFix = null
            fallbackToPhoneFix()
        }
    }

    /** Fallback to phone position if available. */
    private fun fallbackToPhoneFix() {
        val phoneLoc = latestPhoneLocation ?: fetchLastKnownPhoneLocation()
        if (phoneLoc != null) {
            latestPhoneLocation = phoneLoc
            val phoneFix = buildPhonePositionFix(phoneLoc)
            _fix.value = phoneFix
            _nmeaLine.tryEmit(
                GgaGenerator.generate(phoneFix.latitude, phoneFix.longitude, phoneFix.altitudeM, phoneFix.numSatellites, phoneFix.hdop)
            )
        } else if (_fix.value?.source == FixSource.BLE) {
            _fix.value = null
        }
    }

    /** Call with every raw NMEA line received from the BLE receiver. */
    fun onBleRawLine(line: String) {
        if (bleConnected && isBleFixValid(latestBleFix)) {
            _nmeaLine.tryEmit(line)
        }
    }

    /** Starts listening for the phone's own GPS/network location as the fallback. */
    fun startPhoneUpdates() {
        if (phoneListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val provider = when {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true -> LocationManager.GPS_PROVIDER
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return
        try {
            val lastLoc = fetchLastKnownPhoneLocation()
            if (lastLoc != null) {
                latestPhoneLocation = lastLoc
                if (!bleConnected || !isBleFixValid(latestBleFix)) {
                    fallbackToPhoneFix()
                }
            }
            locationManager?.requestLocationUpdates(provider, PHONE_UPDATE_INTERVAL_MS, 0f, phoneListener)
            phoneListening = true
        } catch (_: SecurityException) {
            // permission revoked between check and call
        }
    }

    private fun fetchLastKnownPhoneLocation(): Location? {
        return try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun buildPhonePositionFix(location: Location): PositionFix {
        val rawSats = if (location.extras?.containsKey("satellites") == true) {
            location.extras!!.getInt("satellites")
        } else {
            0
        }
        val satellites = if (rawSats > 0) rawSats else DEFAULT_PHONE_SATELLITES
        val hdop = if (location.hasAccuracy() && location.accuracy > 0f) {
            (location.accuracy / 4.0).coerceIn(0.8, 2.5)
        } else {
            1.0
        }
        return PositionFix(
            source = FixSource.PHONE,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = location.altitude,
            fixQuality = 1,
            numSatellites = satellites,
            hdop = hdop,
            timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    fun stopPhoneUpdates() {
        if (!phoneListening) return
        locationManager?.removeUpdates(phoneListener)
        phoneListening = false
    }

    private fun onPhoneLocation(location: Location) {
        latestPhoneLocation = location
        // If BLE currently has a valid fix, keep phone location in standby
        if (bleConnected && isBleFixValid(latestBleFix)) {
            return
        }
        // Otherwise, use phone position as the active fix
        val fix = buildPhonePositionFix(location)
        _fix.value = fix
        _nmeaLine.tryEmit(
            GgaGenerator.generate(fix.latitude, fix.longitude, fix.altitudeM, fix.numSatellites, fix.hdop),
        )
    }

    companion object {
        private const val PHONE_UPDATE_INTERVAL_MS = 1000L
        private const val DEFAULT_PHONE_SATELLITES = 12
    }
}
