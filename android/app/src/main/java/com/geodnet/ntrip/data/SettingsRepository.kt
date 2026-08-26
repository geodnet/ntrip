package com.geodnet.ntrip.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geodnet.ntrip.ntrip.NtripConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ntrip_settings")

/**
 * Persists [NtripConfig] across app restarts (host/port/mountpoint/credentials/position).
 *
 * NOTE: the password is currently stored in plaintext Preferences DataStore. That's adequate for
 * this first milestone but should move to EncryptedSharedPreferences / Android Keystore-backed
 * storage before this is used with real credentials day-to-day.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val MOUNTPOINT = stringPreferencesKey("mountpoint")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val ALTITUDE = doublePreferencesKey("altitude")
        val GGA_INTERVAL_MS = longPreferencesKey("gga_interval_ms")
        val MOCK_LOCATION_ENABLED = booleanPreferencesKey("mock_location_enabled")
        val NMEA_SERVER_ENABLED = booleanPreferencesKey("nmea_server_enabled")
        val RTCM_SERVER_ENABLED = booleanPreferencesKey("rtcm_server_enabled")
        val SHOW_BASE_STATION = booleanPreferencesKey("show_base_station")
        val FILTER_EPHEMERIS_FOR_BLE = booleanPreferencesKey("filter_ephemeris_for_ble")
        val RAW_LOGGING_ENABLED = booleanPreferencesKey("raw_logging_enabled")
        val GNSS_RAW_LOGGING_ENABLED = booleanPreferencesKey("gnss_raw_logging_enabled")
    }

    val configFlow: Flow<NtripConfig> = context.dataStore.data.map { prefs ->
        val defaults = NtripConfig()
        NtripConfig(
            host = prefs[Keys.HOST] ?: defaults.host,
            port = prefs[Keys.PORT] ?: defaults.port,
            mountpoint = prefs[Keys.MOUNTPOINT] ?: defaults.mountpoint,
            username = prefs[Keys.USERNAME] ?: defaults.username,
            password = prefs[Keys.PASSWORD] ?: defaults.password,
            latitude = prefs[Keys.LATITUDE] ?: defaults.latitude,
            longitude = prefs[Keys.LONGITUDE] ?: defaults.longitude,
            altitude = prefs[Keys.ALTITUDE] ?: defaults.altitude,
            ggaIntervalMs = prefs[Keys.GGA_INTERVAL_MS] ?: defaults.ggaIntervalMs,
        )
    }

    suspend fun save(config: NtripConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.port
            prefs[Keys.MOUNTPOINT] = config.mountpoint
            prefs[Keys.USERNAME] = config.username
            prefs[Keys.PASSWORD] = config.password
            prefs[Keys.LATITUDE] = config.latitude
            prefs[Keys.LONGITUDE] = config.longitude
            prefs[Keys.ALTITUDE] = config.altitude
            prefs[Keys.GGA_INTERVAL_MS] = config.ggaIntervalMs
        }
    }

    val outputSettingsFlow: Flow<OutputSettings> = context.dataStore.data.map { prefs ->
        OutputSettings(
            mockLocationEnabled = prefs[Keys.MOCK_LOCATION_ENABLED] ?: false,
            nmeaServerEnabled = prefs[Keys.NMEA_SERVER_ENABLED] ?: false,
            rtcmServerEnabled = prefs[Keys.RTCM_SERVER_ENABLED] ?: false,
            showBaseStation = prefs[Keys.SHOW_BASE_STATION] ?: false,
            filterEphemerisForBle = prefs[Keys.FILTER_EPHEMERIS_FOR_BLE] ?: false,
            rawLoggingEnabled = prefs[Keys.RAW_LOGGING_ENABLED] ?: false,
            gnssRawLoggingEnabled = prefs[Keys.GNSS_RAW_LOGGING_ENABLED] ?: false,
        )
    }

    suspend fun saveMockLocationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOCK_LOCATION_ENABLED] = enabled }
    }

    suspend fun saveNmeaServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NMEA_SERVER_ENABLED] = enabled }
    }

    suspend fun saveRtcmServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RTCM_SERVER_ENABLED] = enabled }
    }

    suspend fun saveShowBaseStation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_BASE_STATION] = enabled }
    }

    suspend fun saveFilterEphemerisForBle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FILTER_EPHEMERIS_FOR_BLE] = enabled }
    }

    suspend fun saveRawLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RAW_LOGGING_ENABLED] = enabled }
    }

    suspend fun saveGnssRawLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GNSS_RAW_LOGGING_ENABLED] = enabled }
    }
}
