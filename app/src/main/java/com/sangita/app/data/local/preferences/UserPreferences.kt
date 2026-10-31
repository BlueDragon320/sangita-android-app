package com.sangita.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID

open class UserPreferences(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences("sangita_prefs", Context.MODE_PRIVATE)

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

    private val _savedPlaylistIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val savedPlaylistIdsFlow: StateFlow<Set<String>> = _savedPlaylistIdsFlow.asStateFlow()

    private var _defaultAccountMigrated: Boolean = false

    init {
        _defaultAccountMigrated = prefs?.getBoolean(KEY_DEFAULT_ACCOUNT_MIGRATED, false) ?: false
        prefs?.getStringSet(KEY_SAVED_PLAYLISTS, null)?.let {
            _savedPlaylistIdsFlow.value = it.toSet()
        }
    }

    open fun getServerUrl(): String {
        return prefs?.getString(KEY_SERVER_URL, null) ?: _serverUrlFlow?.value ?: ""
    }

    open fun setServerUrl(url: String): String {
        val normalized = normalizeUrl(url)
        prefs?.edit()?.putString(KEY_SERVER_URL, normalized)?.apply()
        _serverUrlFlow.value = normalized
        return normalized
    }

    open fun getToken(): String? {
        return prefs?.getString(KEY_TOKEN, null) ?: _tokenFlow?.value
    }

    open fun setToken(token: String?) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
        _tokenFlow.value = token
    }

    open fun getUsername(): String? {
        return prefs?.getString(KEY_USERNAME, null) ?: _usernameFlow?.value
    }

    open fun setUsername(username: String?) {
        prefs?.edit()?.putString(KEY_USERNAME, username)?.apply()
        _usernameFlow.value = username
    }

    open fun getAccountKey(): String {
        val server = getServerUrl().trimEnd('/')
        val user = getUsername()?.trim()?.lowercase().orEmpty()
        return if (server.isNotBlank() && user.isNotBlank()) "$server|$user" else "default_account"
    }

    open fun observeAccountKey(): Flow<String> = combine(serverUrlFlow, usernameFlow) { server, user ->
        val cleanServer = server.trimEnd('/')
        val cleanUser = user?.trim()?.lowercase().orEmpty()
        if (cleanServer.isNotBlank() && cleanUser.isNotBlank()) "$cleanServer|$cleanUser" else "default_account"
    }.distinctUntilChanged()

    open fun getRole(): String {
        return prefs?.getString(KEY_ROLE, "user") ?: "user"
    }

    open fun setRole(role: String) {
        prefs?.edit()?.putString(KEY_ROLE, role)?.apply()
    }

    open fun getTheme(): String {
        return prefs?.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    open fun setTheme(theme: String) {
        prefs?.edit()?.putString(KEY_THEME, theme)?.apply()
        _themeFlow.value = theme
    }

    open fun getLyricsTheme(): String {
        return prefs?.getString(KEY_LYRICS_THEME, "black") ?: "black"
    }

    open fun setLyricsTheme(theme: String) {
        prefs?.edit()?.putString(KEY_LYRICS_THEME, theme)?.apply()
        _lyricsThemeFlow.value = theme
    }

    open fun getSavedPlaylistIds(): Set<String> {
        return prefs?.getStringSet(KEY_SAVED_PLAYLISTS, emptySet()) ?: _savedPlaylistIdsFlow.value
    }

    open fun setPlaylistSaved(playlistId: String, saved: Boolean) {
        val current = getSavedPlaylistIds().toMutableSet()
        if (saved) current.add(playlistId) else current.remove(playlistId)
        prefs?.edit()?.putStringSet(KEY_SAVED_PLAYLISTS, current)?.apply()
        _savedPlaylistIdsFlow.value = current
    }

    open fun isPlaylistSaved(playlistId: String): Boolean {
        return getSavedPlaylistIds().contains(playlistId)
    }

    open fun observeSavedPlaylistIds(): Flow<Set<String>> = _savedPlaylistIdsFlow.asStateFlow()

    open fun getOrCreateDeviceId(): String {
        var id = prefs?.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs?.edit()?.putString(KEY_DEVICE_ID, id)?.apply()
        }
        return id
    }

    open fun isInitialDefaultAccountMigrated(): Boolean {
        return prefs?.getBoolean(KEY_DEFAULT_ACCOUNT_MIGRATED, false) ?: _defaultAccountMigrated
    }

    open fun setInitialDefaultAccountMigrated(migrated: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEFAULT_ACCOUNT_MIGRATED, migrated)?.apply()
        _defaultAccountMigrated = migrated
    }

    open fun clearAuth() {
        prefs?.edit()
            ?.remove(KEY_TOKEN)
            ?.remove(KEY_USERNAME)
            ?.remove(KEY_ROLE)
            ?.remove(KEY_SAVED_PLAYLISTS)
            ?.apply()
        _tokenFlow.value = null
        _usernameFlow.value = null
        _savedPlaylistIdsFlow.value = emptySet()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "user_role"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_THEME = "app_theme"
        private const val KEY_LYRICS_THEME = "lyrics_theme"
        private const val KEY_SAVED_PLAYLISTS = "saved_playlist_ids"
        private const val KEY_DEFAULT_ACCOUNT_MIGRATED = "default_account_migrated"

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
