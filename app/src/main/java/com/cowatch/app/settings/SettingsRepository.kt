package com.cowatch.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cowatch_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val displayName = stringPreferencesKey("display_name")
        val signalingHost = stringPreferencesKey("signaling_host")
        val stunUrl = stringPreferencesKey("stun_url")
        val turnUrl = stringPreferencesKey("turn_url")
        val turnUser = stringPreferencesKey("turn_user")
        val turnPassword = stringPreferencesKey("turn_password")
        fun feature(f: Feature) = booleanPreferencesKey(f.key)
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        features = Feature.entries.associateWith { this[Keys.feature(it)] ?: it.defaultOn },
        displayName = this[Keys.displayName] ?: "",
        signalingHost = this[Keys.signalingHost] ?: AppSettings.DEFAULT_SIGNALING_HOST,
        stunUrl = this[Keys.stunUrl] ?: AppSettings.DEFAULT_STUN,
        turnUrl = this[Keys.turnUrl] ?: "",
        turnUser = this[Keys.turnUser] ?: "",
        turnPassword = this[Keys.turnPassword] ?: "",
    )

    suspend fun setFeature(feature: Feature, on: Boolean) {
        context.dataStore.edit { it[Keys.feature(feature)] = on }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.displayName] = next.displayName.trim()
            prefs[Keys.signalingHost] = next.signalingHost.trim().ifEmpty { AppSettings.DEFAULT_SIGNALING_HOST }
            prefs[Keys.stunUrl] = next.stunUrl.trim()
            prefs[Keys.turnUrl] = next.turnUrl.trim()
            prefs[Keys.turnUser] = next.turnUser.trim()
            prefs[Keys.turnPassword] = next.turnPassword
            next.features.forEach { (f, on) -> prefs[Keys.feature(f)] = on }
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
