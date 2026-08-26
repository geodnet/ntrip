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
            time = if (fix.timestampMs > 0) fix.timestampMs else System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy
                bearingAccuracyDegrees = 360f
                speedAccuracyMetersPerSecond = 5f
            }

            val fixTypeString = when (fix.fixQuality) {
                4 -> "RTK FIXED"
                5 -> "RTK FLOAT"
                2 -> "DGPS"
                1 -> "SINGLE"
                else -> "NONE"
            }
            val fixTypeShort = when (fix.fixQuality) {
                4 -> "RTK"
                5 -> "FLOAT"
                2 -> "DGPS"
                1 -> "SINGLE"
                else -> "NONE"
            }
            val fixTypeDisplay = when (fix.fixQuality) {
                4 -> "RTK Fix"
                5 -> "RTK Float"
                2 -> "DGPS"
                1 -> "Single"
                else -> "No Fix"
            }

            val ggaLine = fix.rawNmeaGga.ifBlank {
                com.geodnet.ntrip.ntrip.GgaGenerator.generate(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    altitude = fix.altitudeM,
                    numSatellites = fix.numSatellites,
                    hdop = fix.hdop,
                    staId = fix.diffStationId,
                    age = fix.diffAgeSec
                )
            }

            extras = android.os.Bundle().apply {
                // Satellites (int & string variants used across SW Maps / Lefebure / QField)
                putInt("satellites", fix.numSatellites)
                putInt("Satellites", fix.numSatellites)
                putInt("satellites_in_use", fix.numSatellites)
                putInt("satellites_in_view", fix.numSatellites)
                putInt("satellitesUsed", fix.numSatellites)
                putInt("satellitesVisible", fix.numSatellites)
                putInt("num_satellites", fix.numSatellites)
                putInt("numSatellites", fix.numSatellites)
                putInt("sv_used", fix.numSatellites)
                putInt("svs", fix.numSatellites)
                putInt("SATS", fix.numSatellites)
                putString("satellites_str", fix.numSatellites.toString())

                // Fix Quality / Status / Type (int & string variants)
                putInt("fixType", fix.fixQuality)
                putInt("FixType", fix.fixQuality)
                putInt("fix_type", fix.fixQuality)
                putInt("quality", fix.fixQuality)
                putInt("Quality", fix.fixQuality)
                putString("fix_type_str", fixTypeString)
                putString("fixType_str", fixTypeString)
                putString("FixType_str", fixTypeString)
                putString("status", fixTypeDisplay)
                putString("Status", fixTypeDisplay)
                putString("rtk_status", if (fix.fixQuality == 4) "FIXED" else if (fix.fixQuality == 5) "FLOAT" else "NONE")
                putString("rtk", if (fix.fixQuality == 4) "Fix" else if (fix.fixQuality == 5) "Float" else "None")
                putString("mode", fixTypeShort)
                putString("position_type", if (fix.fixQuality == 4) "RTK_FIXED" else if (fix.fixQuality == 5) "RTK_FLOAT" else "SINGLE")
                putBoolean("is_rtk", fix.fixQuality == 4 || fix.fixQuality == 5)
                putBoolean("is_rtk_fixed", fix.fixQuality == 4)

                // Differential Age & Station ID
                putFloat("diffAge", fix.diffAgeSec.toFloat())
                putFloat("diff_age", fix.diffAgeSec.toFloat())
                putFloat("age", fix.diffAgeSec.toFloat())
                putFloat("Age", fix.diffAgeSec.toFloat())
                putFloat("age_of_diff", fix.diffAgeSec.toFloat())
                putInt("diffStationId", fix.diffStationId)
                putInt("diff_station_id", fix.diffStationId)
                putInt("station_id", fix.diffStationId)
                putInt("stationId", fix.diffStationId)
                putInt("base_station_id", fix.diffStationId)

                // DOPs & Accuracies
                putFloat("hdop", fix.hdop.toFloat())
                putFloat("HDOP", fix.hdop.toFloat())
                putFloat("accuracy", accuracy)
                putFloat("hAcc", accuracy)
                putFloat("vAcc", accuracy)
                putFloat("rms", accuracy)

                // Geoid Separation & Altitudes
                putFloat("geoid_height", fix.geoidSeparationM.toFloat())
                putFloat("geoidSeparation", fix.geoidSeparationM.toFloat())
                putFloat("undulation", fix.geoidSeparationM.toFloat())
                putDouble("msl_altitude", fix.altitudeM)

                // Raw NMEA String (for SW Maps and GIS apps with NMEA string parsers)
                putString("NMEA", ggaLine)
                putString("nmea", ggaLine)
                putString("raw_nmea", ggaLine)
                putString("GGA", ggaLine)
                putString("gga", ggaLine)
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
        private val PROVIDERS = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add("fused")
            }
        }
    }
}
