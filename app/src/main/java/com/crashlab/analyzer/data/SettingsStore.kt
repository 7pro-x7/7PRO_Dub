package com.crashlab.analyzer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("crashlab_settings")

data class AppSettings(
    val acceptedDisclaimer: Boolean = false,
    val windowSize: Int = 100,
    val platformFilter: String = "all",
    val sessionLimitMinutes: Int = 60,
    val realityCheckEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val ACCEPTED = booleanPreferencesKey("accepted_disclaimer")
        val WINDOW = intPreferencesKey("window_size")
        val PLATFORM = stringPreferencesKey("platform_filter")
        val SESSION_LIMIT = intPreferencesKey("session_limit")
        val REALITY_CHECK = booleanPreferencesKey("reality_check")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            acceptedDisclaimer = p[Keys.ACCEPTED] ?: false,
            windowSize = p[Keys.WINDOW] ?: 100,
            platformFilter = p[Keys.PLATFORM] ?: "all",
            sessionLimitMinutes = p[Keys.SESSION_LIMIT] ?: 60,
            realityCheckEnabled = p[Keys.REALITY_CHECK] ?: true
        )
    }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val current = AppSettings(
                acceptedDisclaimer = p[Keys.ACCEPTED] ?: false,
                windowSize = p[Keys.WINDOW] ?: 100,
                platformFilter = p[Keys.PLATFORM] ?: "all",
                sessionLimitMinutes = p[Keys.SESSION_LIMIT] ?: 60,
                realityCheckEnabled = p[Keys.REALITY_CHECK] ?: true
            )
            val next = block(current)
            p[Keys.ACCEPTED] = next.acceptedDisclaimer
            p[Keys.WINDOW] = next.windowSize
            p[Keys.PLATFORM] = next.platformFilter
            p[Keys.SESSION_LIMIT] = next.sessionLimitMinutes
            p[Keys.REALITY_CHECK] = next.realityCheckEnabled
        }
    }
}
