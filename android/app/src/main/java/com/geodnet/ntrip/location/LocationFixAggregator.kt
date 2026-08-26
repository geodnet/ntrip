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
 * Combines the BLE RTK receiver's fix (when connected) with the phone's own GPS as a fallback
 * into a single "best fix" stream -- this is what drives [MockLocationProvider] and the NMEA TCP
 * server. BLE always takes priority when connected (that's the whole point of this app); the
 * phone GPS only fills in when there's no receiver attached, mirroring the "Smart Phone Location
 * GGA Fallback" behavior described in readme.md (which describes the same fallback for GGA
 * upload to the caster -- this reuses the idea for the mock-location/NMEA-server outputs).
 *
 * Owned by NtripForegroundService so its outputs survive the app being backgrounded the same way
 * the caster connection does; fed BLE fixes/lines via [onBleConnectionChanged]/[onBleFix]/
 * [onBleRawLine], which NtripViewModel calls after collecting them from BleRtkReceiver (which is
 * itself Activity/ViewModel-scoped, not service-scoped -- see android/CLAUDE.md's BLE gap).
 */
class LocationFixAggregator(private val context: Context) {

    private val _fix = MutableStateFlow<PositionFix?>(null)
    val fix: StateFlow<PositionFix?> = _fix.asStateFlow()

    /** NMEA lines to forward to the NMEA TCP server: BLE lines verbatim when connected, else a
     * synthesized $GPGGA built from the phone fallback fix. */
    private val _nmeaLine = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val nmeaLine: SharedFlow<String> = _nmeaLine.asSharedFlow()

    private var bleConnected = false
    private var phoneListening = false
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val phoneListener = LocationListener { location -> onPhoneLocation(location) }

    /** Call whenever the BLE receiver connects/disconnects: switches the fallback phone-GPS
     * listener on/off and clears any stale BLE fix. */
    fun onBleConnectionChanged(connected: Boolean) {
        if (bleConnected == connected) return
        bleConnected = connected
        if (connected) {
            stopPhoneUpdates()
            _fix.value = null
        } else {
            startPhoneUpdates()
        }
    }

    /** Call with every new GGA sentence parsed from the BLE receiver. */
    fun onBleFix(sentence: NmeaSentence.Gga) {
        if (!bleConnected) return
        _fix.value = PositionFix(
            source = FixSource.BLE,
            latitude = sentence.latitude,
            longitude = sentence.longitude,
            altitudeM = sentence.altitudeM,
            fixQuality = sentence.fixQuality,
            numSatellites = sentence.numSatellites,
            hdop = sentence.hdop,
            timestampMs = System.currentTimeMillis(),
            diffAgeSec = sentence.diffAgeSec,
            diffStationId = sentence.diffStationId,
        )
    }

    /** Call with every raw NMEA line received from the BLE receiver, for verbatim forwarding to
     * the NMEA TCP server (preserves sentences this app doesn't itself parse, e.g. $GNRMC/$GNGST
     * beyond what feeds [onBleFix]). */
    fun onBleRawLine(line: String) {
        if (bleConnected) _nmeaLine.tryEmit(line)
    }

    /** Starts listening for the phone's own GPS/network location as the no-BLE fallback. No-ops
     * (and stays no-op until the next call) if location permission isn't granted -- the caller
     * doesn't need to check first. */
    fun startPhoneUpdates() {
        if (phoneListening || bleConnected) return
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
            // Deliver last known location immediately if available
            val lastLoc = locationManager?.getLastKnownLocation(provider)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (lastLoc != null && _fix.value == null) {
                onPhoneLocation(lastLoc)
            }
            locationManager?.requestLocationUpdates(provider, PHONE_UPDATE_INTERVAL_MS, 0f, phoneListener)
            phoneListening = true
        } catch (_: SecurityException) {
            // permission revoked between the check above and this call -- stay stopped
        }
    }

    fun stopPhoneUpdates() {
        if (!phoneListening) return
        locationManager?.removeUpdates(phoneListener)
        phoneListening = false
    }

    private fun onPhoneLocation(location: Location) {
        if (bleConnected) return
        val fix = PositionFix(
            source = FixSource.PHONE,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = location.altitude,
            fixQuality = 1,
            numSatellites = if (location.extras?.containsKey("satellites") == true) {
                location.extras!!.getInt("satellites")
            } else {
                0
            },
            hdop = 0.0,
            timestampMs = location.time,
        )
        _fix.value = fix
        _nmeaLine.tryEmit(
            GgaGenerator.generate(fix.latitude, fix.longitude, fix.altitudeM, fix.numSatellites, fix.hdop),
        )
    }

    companion object {
        private const val PHONE_UPDATE_INTERVAL_MS = 1000L
    }
}
