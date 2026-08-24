package com.geodnet.ntrip.data

import android.content.Context
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
}
