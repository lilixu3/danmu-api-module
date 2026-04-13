package com.danmuapi.manager.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.danmuapi.manager.core.model.CoreUpdateInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "danmu_manager_prefs")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LOG_CLEAN_INTERVAL_DAYS = intPreferencesKey("log_clean_interval_days")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_PATH = stringPreferencesKey("webdav_path")
        val CONSOLE_LOG_LIMIT = intPreferencesKey("console_log_limit")
        val CORE_UPDATE_FOREGROUND_ENABLED = booleanPreferencesKey("core_update_foreground_enabled")
        val CORE_UPDATE_BACKGROUND_ENABLED = booleanPreferencesKey("core_update_background_enabled")
        val CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES = intPreferencesKey("core_update_background_interval_minutes")
        val LAST_SILENT_CORE_UPDATE_CHECK_AT = longPreferencesKey("last_silent_core_update_check_at")
        val CACHED_CORE_UPDATE_INFO = stringPreferencesKey("cached_core_update_info")
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

    val themeMode: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.THEME_MODE] ?: 0).coerceIn(0, 2)
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: true
    }

    val consoleLogLimit: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.CONSOLE_LOG_LIMIT] ?: 300).coerceIn(50, 5_000)
    }

    val coreUpdateForegroundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CORE_UPDATE_FOREGROUND_ENABLED] ?: true
    }

    val coreUpdateBackgroundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CORE_UPDATE_BACKGROUND_ENABLED] ?: true
    }

    val coreUpdateBackgroundIntervalMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        normalizeSilentCoreUpdateBackgroundIntervalMinutes(
            prefs[Keys.CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES]
                ?: DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES,
        )
    }

    val silentCoreUpdateSettings: Flow<SilentCoreUpdateSettings> = context.dataStore.data.map { prefs ->
        SilentCoreUpdateSettings(
            foregroundEnabled = prefs[Keys.CORE_UPDATE_FOREGROUND_ENABLED] ?: true,
            backgroundEnabled = prefs[Keys.CORE_UPDATE_BACKGROUND_ENABLED] ?: true,
            backgroundIntervalMinutes = normalizeSilentCoreUpdateBackgroundIntervalMinutes(
                prefs[Keys.CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES]
                    ?: DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES,
            ),
            lastSilentCheckAtMillis = (prefs[Keys.LAST_SILENT_CORE_UPDATE_CHECK_AT] ?: 0L).coerceAtLeast(0L),
        )
    }

    suspend fun setLogCleanIntervalDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOG_CLEAN_INTERVAL_DAYS] = days.coerceAtLeast(0)
        }
    }

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GITHUB_TOKEN] = token.trim()
        }
    }

    suspend fun setWebDavUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEBDAV_URL] = url.trim()
        }
    }

    suspend fun setWebDavUsername(username: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEBDAV_USERNAME] = username
        }
    }

    suspend fun setWebDavPassword(password: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEBDAV_PASSWORD] = password
        }
    }

    suspend fun setWebDavPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEBDAV_PATH] = path.trim()
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.coerceIn(0, 2)
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setConsoleLogLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONSOLE_LOG_LIMIT] = limit.coerceIn(50, 5_000)
        }
    }

    suspend fun getGithubTokenValue(): String {
        return githubToken.first()
    }

    suspend fun getSilentCoreUpdateSettings(): SilentCoreUpdateSettings {
        return silentCoreUpdateSettings.first()
    }

    suspend fun setCoreUpdateForegroundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CORE_UPDATE_FOREGROUND_ENABLED] = enabled
        }
    }

    suspend fun setCoreUpdateBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CORE_UPDATE_BACKGROUND_ENABLED] = enabled
        }
    }

    suspend fun setCoreUpdateBackgroundIntervalMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES] =
                normalizeSilentCoreUpdateBackgroundIntervalMinutes(minutes)
        }
    }

    suspend fun setLastSilentCoreUpdateCheckAt(timestampMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SILENT_CORE_UPDATE_CHECK_AT] = timestampMillis.coerceAtLeast(0L)
        }
    }

    suspend fun getCachedCoreUpdateInfo(): Map<String, CoreUpdateInfo> {
        val prefs = context.dataStore.data.first()
        return decodeCoreUpdateInfoCache(prefs[Keys.CACHED_CORE_UPDATE_INFO])
    }

    suspend fun setCachedCoreUpdateInfo(updateInfo: Map<String, CoreUpdateInfo>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_CORE_UPDATE_INFO] = encodeCoreUpdateInfoCache(updateInfo)
        }
    }
}
