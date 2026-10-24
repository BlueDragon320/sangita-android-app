package com.sangita.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sangita_prefs", Context.MODE_PRIVATE)

    private val _serverUrlFlow = MutableStateFlow(getServerUrl())
    val serverUrlFlow: StateFlow<String> = _serverUrlFlow.asStateFlow()

    private val _tokenFlow = MutableStateFlow(getToken())
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    private val _usernameFlow = MutableStateFlow(getUsername())
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(getTheme())
    val themeFlow: StateFlow<String> = _themeFlow.asStateFlow()

    private val _lyricsThemeFlow = MutableStateFlow(getLyricsTheme())
    val lyricsThemeFlow: StateFlow<String> = _lyricsThemeFlow.asStateFlow()

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, "") ?: ""
    }

    fun setServerUrl(url: String): String {
        val normalized = normalizeUrl(url)
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        _serverUrlFlow.value = normalized
        return normalized
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun setToken(token: String?) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _tokenFlow.value = token
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun setUsername(username: String?) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
        _usernameFlow.value = username
    }

    fun getRole(): String {
        return prefs.getString(KEY_ROLE, "user") ?: "user"
    }

    fun setRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _themeFlow.value = theme
    }

    fun getLyricsTheme(): String {
        return prefs.getString(KEY_LYRICS_THEME, "combo1") ?: "combo1"
    }

    fun setLyricsTheme(theme: String) {
        prefs.edit().putString(KEY_LYRICS_THEME, theme).apply()
        _lyricsThemeFlow.value = theme
    }

    fun getOrCreateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .apply()
        _tokenFlow.value = null
        _usernameFlow.value = null
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "user_role"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_THEME = "app_theme"
        private const val KEY_LYRICS_THEME = "lyrics_theme"

        const val THEME_AMOLED = "amoled"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        /**
         * Intelligently normalizes domain names and URLs entered by the user.
         * Examples:
         * - "music.sangita.com" -> "https://music.sangita.com"
         * - "sangita.app/music/" -> "https://sangita.app/music"
         * - "http://192.168.1.50:5000" -> "http://192.168.1.50:5000"
         * - "192.168.29.254:5000" -> "http://192.168.29.254:5000"
         */
        fun normalizeUrl(input: String): String {
            var trimmed = input.trim().removeSuffix("/")
            if (trimmed.isEmpty()) return ""

            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                // If the user entered an IP address or localhost, default to http://
                val isLocalIp = trimmed.startsWith("10.") ||
                        trimmed.startsWith("192.168.") ||
                        trimmed.startsWith("172.") ||
                        trimmed.startsWith("127.0.0.1") ||
                        trimmed.startsWith("localhost")

                trimmed = if (isLocalIp) {
                    "http://$trimmed"
                } else {
                    // For public domains (e.g. music.mysite.com, sangita.domain.net), default to HTTPS
                    "https://$trimmed"
                }
            }
            return trimmed
        }
    }
}
