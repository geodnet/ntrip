package com.geodnet.ntrip.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Injects [PositionFix]es into the Android OS as mock GPS/network locations via
 * `LocationManager`'s test-provider API, so every location-consuming app (Google Maps, OsmAnd,
 * SW Maps, ...) sees the RTK-corrected fix instead of the phone's own GPS.
 *
 * There is no runtime permission dialog for this -- on API 23+ it's gated entirely by the device
 * being set up with this app as the "mock location app" in Developer Options (Settings > System >
 * Developer options > Select mock location app). [enable] surfaces that failure via [state]
 * (a `SecurityException` from `addTestProvider`/`setTestProviderLocation`) rather than throwing.
 */
class MockLocationProvider(context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow(MockLocationState())
    val state: StateFlow<MockLocationState> = _state.asStateFlow()
    private val activeProviders = mutableListOf<String>()

    fun enable() {
        if (_state.value.enabled) return
        try {
            for (provider in PROVIDERS) {
                addTestProvider(provider)
                locationManager?.setTestProviderEnabled(provider, true)
                activeProviders += provider
            }
            _state.value = MockLocationState(enabled = true)
        } catch (e: SecurityException) {
            cleanup()
            _state.value = MockLocationState(
                enabled = false,
                errorMessage = "Not set as the mock location app " +
                    "(Settings > Developer options > Select mock location app)",
            )
        }
    }

    fun disable() {
        cleanup()
        _state.value = MockLocationState(enabled = false)
    }

    fun update(fix: PositionFix?) {
        if (fix == null || !_state.value.enabled || activeProviders.isEmpty()) return
        try {
            for (provider in activeProviders) {
                locationManager?.setTestProviderLocation(provider, buildLocation(provider, fix))
            }
            _state.update { it.copy(lastFix = fix, updateCount = it.updateCount + 1, errorMessage = null) }
        } catch (e: SecurityException) {
            cleanup()
            _state.value = MockLocationState(enabled = false, errorMessage = "Lost mock location permission")
        }
    }

    private fun buildLocation(provider: String, fix: PositionFix): Location =
        Location(provider).apply {
            latitude = fix.latitude
            longitude = fix.longitude
            altitude = fix.altitudeM
            accuracy = accuracyFor(fix)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy
                bearingAccuracyDegrees = 180f
                speedAccuracyMetersPerSecond = 1f
            }
        }

    /** RTK-fixed (NMEA quality 4) gets survey-grade accuracy; float (5) is coarser; anything else
     * (single/no RTK, or an unqualified phone fix) reports a conservative 5m. */
    private fun accuracyFor(fix: PositionFix): Float = when (fix.fixQuality) {
        4 -> 0.02f
        5 -> 0.5f
        else -> 5f
    }

    @SuppressLint("NewApi")
    private fun addTestProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager?.addTestProvider(
                provider,
                android.location.provider.ProviderProperties.Builder()
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            locationManager?.addTestProvider(
                provider,
                /* requiresNetwork = */ false,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE,
            )
        }
    }

    private fun cleanup() {
        for (provider in activeProviders) {
            try {
                locationManager?.removeTestProvider(provider)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
                // never actually got added -- nothing to remove
            }
        }
        activeProviders.clear()
    }

    companion object {
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
