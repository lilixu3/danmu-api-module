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

        // WebDAV settings for config import/export
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_PATH = stringPreferencesKey("webdav_path")
    }

    val logCleanIntervalDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOG_CLEAN_INTERVAL_DAYS] ?: 0
    }

    val githubToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GITHUB_TOKEN] ?: ""
    }

    val webDavUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEBDAV_URL] ?: ""
    }

    val webDavUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEBDAV_USERNAME] ?: ""
    }

    val webDavPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEBDAV_PASSWORD] ?: ""
    }

    val webDavPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEBDAV_PATH] ?: ""
    }

    suspend fun setLogCleanIntervalDays(days: Int) {
        val safe = days.coerceAtLeast(0)
        context.dataStore.edit { it[Keys.LOG_CLEAN_INTERVAL_DAYS] = safe }
    }

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }

    suspend fun setWebDavUrl(url: String) {
        context.dataStore.edit { it[Keys.WEBDAV_URL] = url.trim() }
    }

    suspend fun setWebDavUsername(username: String) {
        context.dataStore.edit { it[Keys.WEBDAV_USERNAME] = username }
    }

    suspend fun setWebDavPassword(password: String) {
        context.dataStore.edit { it[Keys.WEBDAV_PASSWORD] = password }
    }

    suspend fun setWebDavPath(path: String) {
        context.dataStore.edit { it[Keys.WEBDAV_PATH] = path.trim() }
    }
}
