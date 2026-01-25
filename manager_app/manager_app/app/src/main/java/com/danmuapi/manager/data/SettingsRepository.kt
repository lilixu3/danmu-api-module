package com.danmuapi.manager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

        // UI preferences
        // 0 = follow system, 1 = light, 2 = dark
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        // WebDAV settings for config import/export
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_PATH = stringPreferencesKey("webdav_path")

        // Console preferences
        // 0 = always ask, 1 = ask once per app run, 2 = never ask
        val ADMIN_TOKEN_PROMPT_MODE = intPreferencesKey("admin_token_prompt_mode")

        // Console log viewer: max lines/items to render (avoid jank on low-end devices)
        val CONSOLE_LOG_LIMIT = intPreferencesKey("console_log_limit")
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

    /**
     * Admin token prompt mode for “控制台 / 系统”页:
     * 0 = every time entering, 1 = once per app run, 2 = never ask
     */
    val adminTokenPromptMode: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.ADMIN_TOKEN_PROMPT_MODE] ?: 1).coerceIn(0, 2)
    }

    /** Max log lines/items shown in the console log viewers. */
    val consoleLogLimit: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.CONSOLE_LOG_LIMIT] ?: 300).coerceIn(50, 5000)
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

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAdminTokenPromptMode(mode: Int) {
        context.dataStore.edit { it[Keys.ADMIN_TOKEN_PROMPT_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setConsoleLogLimit(limit: Int) {
        context.dataStore.edit { it[Keys.CONSOLE_LOG_LIMIT] = limit.coerceIn(50, 5000) }
    }
}
