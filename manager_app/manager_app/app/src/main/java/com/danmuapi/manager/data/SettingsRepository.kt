package com.danmuapi.manager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "danmu_manager_prefs")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LOG_CLEAN_INTERVAL_DAYS = intPreferencesKey("log_clean_interval_days")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }

    val logCleanIntervalDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOG_CLEAN_INTERVAL_DAYS] ?: 0
    }

    val githubToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GITHUB_TOKEN] ?: ""
    }

    suspend fun setLogCleanIntervalDays(days: Int) {
        val safe = days.coerceAtLeast(0)
        context.dataStore.edit { it[Keys.LOG_CLEAN_INTERVAL_DAYS] = safe }
    }

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }
}
