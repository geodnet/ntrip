package com.geodnet.ntrip.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripProfile
import com.geodnet.ntrip.ntrip.NtripProfileJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.profilesDataStore by preferencesDataStore(name = "ntrip_profiles")

/**
 * Named, saveable Ntrip connection profiles (add/load/update/delete) -- separate from the single
 * "current working config" `NtripConfig`/`SettingsRepository` already manage (that's what's
 * actually used to connect; a profile is a named snapshot a user can load back into it, matching
 * `NtripViewModel.loadProfile()`/`updateConfig()`).
 *
 * Serialization itself lives in `ntrip/NtripProfileJson.kt` (pure Kotlin, unit-tested) since this
 * class's Context dependency makes it hard to unit-test directly. The whole profile list is
 * stored as one JSON-array string under a single preference key -- Preferences DataStore has no
 * native list/object support. A separate DataStore file (`ntrip_profiles`, not
 * `SettingsRepository`'s `ntrip_settings`) since Android only allows one active DataStore
 * instance per file name per process.
 *
 * **Passwords are stored in plaintext here too** -- same known gap as `SettingsRepository`'s
 * `NtripConfig` persistence, not made any worse by this.
 */
class NtripProfileRepository(private val context: Context) {

    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val SELECTED_ID = stringPreferencesKey("selected_profile_id")
    }

    val profilesFlow: Flow<List<NtripProfile>> = context.profilesDataStore.data.map { prefs ->
        NtripProfileJson.parse(prefs[Keys.PROFILES_JSON])
    }

    val selectedProfileIdFlow: Flow<String?> = context.profilesDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_ID]
    }

    /** Adds a new profile and marks it selected. */
    suspend fun addProfile(name: String, config: NtripConfig): NtripProfile {
        val profile = NtripProfile(id = UUID.randomUUID().toString(), name = name, config = config)
        context.profilesDataStore.edit { prefs ->
            val current = NtripProfileJson.parse(prefs[Keys.PROFILES_JSON])
            prefs[Keys.PROFILES_JSON] = NtripProfileJson.serialize(current + profile)
            prefs[Keys.SELECTED_ID] = profile.id
        }
        return profile
    }

    suspend fun updateProfile(id: String, name: String, config: NtripConfig) {
        context.profilesDataStore.edit { prefs ->
            val current = NtripProfileJson.parse(prefs[Keys.PROFILES_JSON])
            val updated = current.map { if (it.id == id) it.copy(name = name, config = config) else it }
            prefs[Keys.PROFILES_JSON] = NtripProfileJson.serialize(updated)
        }
    }

    suspend fun deleteProfile(id: String) {
        context.profilesDataStore.edit { prefs ->
            val current = NtripProfileJson.parse(prefs[Keys.PROFILES_JSON])
            prefs[Keys.PROFILES_JSON] = NtripProfileJson.serialize(current.filterNot { it.id == id })
            if (prefs[Keys.SELECTED_ID] == id) prefs.remove(Keys.SELECTED_ID)
        }
    }

    suspend fun setSelectedProfileId(id: String) {
        context.profilesDataStore.edit { prefs -> prefs[Keys.SELECTED_ID] = id }
    }
}
