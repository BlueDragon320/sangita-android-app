package com.sangita.app.sync

import android.os.Build
import android.util.Log
import com.sangita.app.data.local.preferences.UserPreferences
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SyncedDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "mobile",
    val isActive: Boolean = false,
    val isMe: Boolean = false
)

data class RemotePlaybackState(
    val activeDeviceId: String? = null,
    val trackId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val volume: Double = 0.8,
    val isLoop: Boolean = false,
    val isShuffle: Boolean = false,
    val isTransferHandoff: Boolean = false,
    val originDeviceId: String? = null,
    val updatedAt: Long = 0L
)

class DeviceSyncManager(
    private val userPreferences: UserPreferences,
    private val scope: CoroutineScope
) {
    private var socket: Socket? = null
    private var heartbeatJob: Job? = null

    private val _devices = MutableStateFlow<List<SyncedDevice>>(emptyList())
    val devices: StateFlow<List<SyncedDevice>> = _devices.asStateFlow()

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _remotePlaybackState = MutableStateFlow<RemotePlaybackState?>(null)
    val remotePlaybackState: StateFlow<RemotePlaybackState?> = _remotePlaybackState.asStateFlow()

    private val _isActiveDevice = MutableStateFlow(false)
    val isActiveDevice: StateFlow<Boolean> = _isActiveDevice.asStateFlow()

    var remoteCommandHandler: ((action: String, payload: JSONObject?) -> Unit)? = null
    var onOtherDeviceActiveCallback: ((activeDeviceId: String, state: RemotePlaybackState) -> Unit)? = null
    var onPlaybackStateReceived: ((state: RemotePlaybackState) -> Unit)? = null

    val myDeviceId: String get() = userPreferences.getOrCreateDeviceId()
    val myDeviceName: String get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android)"

    init {
        scope.launch {
            combine(
                userPreferences.serverUrlFlow,
                userPreferences.tokenFlow
            ) { url, token ->
                Pair(url, token)
            }.distinctUntilChanged().collectLatest { (url, token) ->
                if (url.isNotBlank() && !token.isNullOrBlank()) {
                    connect()
                } else {
                    disconnect()
                }
            }
        }
    }

    fun connect() {
        var serverUrl = userPreferences.getServerUrl().trim()
        val token = userPreferences.getToken()
        if (serverUrl.isBlank() || token.isNullOrBlank()) {
            Log.d(TAG, "Cannot connect: serverUrl or token is missing")
            return
        }

        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.dropLast(1)
        }

        try {
            disconnect()

            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                query = "token=$token"
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionAttempts = 50
                reconnectionDelay = 1500L
                reconnectionDelayMax = 5000L
                timeout = 20000L
            }

            val s = IO.socket(serverUrl, opts)
            socket = s

            s.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Connected to sync server")
                _isConnected.value = true

                val payload = JSONObject().apply {
                    put("deviceId", myDeviceId)
                    put("deviceName", myDeviceName)
                    put("deviceType", "mobile")
                }
                s.emit("REGISTER_DEVICE", payload)
                startHeartbeat()
            }

            s.on(Socket.EVENT_DISCONNECT) { args ->
                Log.d(TAG, "Disconnected from sync server: ${args.firstOrNull()}")
                _isConnected.value = false
                stopHeartbeat()
            }

            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "Connect error: ${args.firstOrNull()}")
                _isConnected.value = false
            }

            s.on("DEVICE_LIST_UPDATED") { args ->
                try {
                    val first = args.firstOrNull()
                    Log.d(TAG, "DEVICE_LIST_UPDATED received raw: $first")
                    val list = when (first) {
                        is JSONObject -> first.optJSONArray("devices") ?: JSONArray()
                        is JSONArray -> first
                        is String -> {
                            try {
                                val obj = JSONObject(first)
                                obj.optJSONArray("devices") ?: JSONArray()
                            } catch (_: Exception) {
                                try { JSONArray(first) } catch (_: Exception) { JSONArray() }
                            }
                        }
                        else -> JSONArray()
                    }
                    val currentActiveId = _activeDeviceId.value

                    val parsed = mutableListOf<SyncedDevice>()
                    for (i in 0 until list.length()) {
                        val obj = list.getJSONObject(i)
                        val id = obj.optString("deviceId", "")
                        if (id.isNotBlank()) {
                            val isMe = (id == myDeviceId)
                            val isActive = (currentActiveId != null && id == currentActiveId)
                            parsed.add(
                                SyncedDevice(
                                    deviceId = id,
                                    deviceName = obj.optString("deviceName", if (isMe) myDeviceName else "Web Browser"),
                                    deviceType = obj.optString("deviceType", if (isMe) "mobile" else "desktop"),
                                    isActive = isActive,
                                    isMe = isMe
                                )
                            )
                        }
                    }

                    // Keep this device always in the list
                    if (parsed.none { it.isMe }) {
                        parsed.add(
                            0,
                            SyncedDevice(
                                deviceId = myDeviceId,
                                deviceName = myDeviceName,
                                deviceType = "mobile",
                                isActive = (currentActiveId != null && currentActiveId == myDeviceId),
                                isMe = true
                            )
                        )
                    }

                    // If active device is remote, make sure it is never dropped from the device list
                    if (!currentActiveId.isNullOrBlank() && currentActiveId != myDeviceId && parsed.none { it.deviceId == currentActiveId }) {
                        parsed.add(
                            SyncedDevice(
                                deviceId = currentActiveId,
                                deviceName = "Web Browser",
                                deviceType = "desktop",
                                isActive = true,
                                isMe = false
                            )
                        )
                    }

                    val updatedList = parsed.map { d ->
                        d.copy(isActive = d.deviceId == _activeDeviceId.value)
                    }
                    _devices.value = updatedList
                    Log.d(TAG, "DEVICE_LIST_UPDATED total ${updatedList.size} devices: ${updatedList.map { "${it.deviceName} (${it.deviceId}, isMe=${it.isMe}, isActive=${it.isActive})" }}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing DEVICE_LIST_UPDATED", e)
                }
            }

            s.on("PLAYBACK_STATE_CHANGED") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val originId = data.optString("originDeviceId", "")
                    if (originId.isNotEmpty() && originId == myDeviceId) {
                        return@on
                    }

                    val stateObj = data.optJSONObject("state") ?: data

                    val activeId = if (stateObj.has("activeDeviceId") && !stateObj.isNull("activeDeviceId")) {
                        stateObj.optString("activeDeviceId", "")
                    } else {
                        null
                    }?.takeIf { it.isNotBlank() && it != "null" }

                    val trackId = if (stateObj.has("trackId") && !stateObj.isNull("trackId")) {
                        stateObj.optString("trackId", "")
                    } else {
                        null
                    }?.takeIf { it.isNotBlank() && it != "null" }

                    val positionMs = stateObj.optLong("positionMs", 0L)
                    val durationMs = stateObj.optLong("durationMs", 0L)
                    val isPlaying = stateObj.optBoolean("isPlaying", false)
                    val isBuffering = stateObj.optBoolean("isBuffering", false)
                    val volume = stateObj.optDouble("volume", 0.8)
                    val isLoop = stateObj.optBoolean("isLoop", false)
                    val isShuffle = stateObj.optBoolean("isShuffle", false)
                    val isTransferHandoff = stateObj.optBoolean("isTransferHandoff", false)
                    val rawUpdatedAt = stateObj.optLong("updatedAt", 0L)
                    val updatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else System.currentTimeMillis()

                    val remoteState = RemotePlaybackState(
                        activeDeviceId = activeId,
                        trackId = trackId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        volume = volume,
                        isLoop = isLoop,
                        isShuffle = isShuffle,
                        isTransferHandoff = isTransferHandoff,
                        originDeviceId = originId.ifEmpty { null },
                        updatedAt = updatedAt
                    )

                    _remotePlaybackState.value = remoteState
                    _activeDeviceId.value = activeId
                    val amActive = (activeId == myDeviceId)
                    _isActiveDevice.value = amActive

                    // Ensure remote active device is present in devices list
                    val currentDevs = _devices.value.toMutableList()
                    if (!activeId.isNullOrBlank() && activeId != myDeviceId && currentDevs.none { it.deviceId == activeId }) {
                        currentDevs.add(
                            SyncedDevice(
                                deviceId = activeId,
                                deviceName = "Web Browser",
                                deviceType = "desktop",
                                isActive = true,
                                isMe = false
                            )
                        )
                    }

                    _devices.value = currentDevs.map { d ->
                        d.copy(isActive = d.deviceId == activeId)
                    }

                    Log.d(TAG, "PLAYBACK_STATE_CHANGED: activeId=$activeId (amActive=$amActive), trackId=$trackId, isPlaying=$isPlaying, pos=$positionMs")

                    if (!activeId.isNullOrBlank() && activeId != myDeviceId) {
                        try {
                            onOtherDeviceActiveCallback?.invoke(activeId, remoteState)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onOtherDeviceActiveCallback", e)
                        }
                    }
                    try {
                        onPlaybackStateReceived?.invoke(remoteState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onPlaybackStateReceived", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing PLAYBACK_STATE_CHANGED", e)
                }
            }

            s.on("POSITION_SYNC") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val originId = data.optString("originDeviceId", "")
                    if (originId.isNotEmpty() && originId == myDeviceId) return@on

                    val posMs = data.optLong("positionMs", 0L)
                    val durMs = data.optLong("durationMs", 0L)
                    val rawUpdatedAt = data.optLong("updatedAt", 0L)
                    val updatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else System.currentTimeMillis()

                    if (!_isActiveDevice.value) {
                        val current = _remotePlaybackState.value
                        if (current != null) {
                            _remotePlaybackState.value = current.copy(
                                positionMs = posMs,
                                durationMs = if (durMs > 0L) durMs else current.durationMs,
                                updatedAt = updatedAt
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling POSITION_SYNC", e)
                }
            }

            val handleRemoteCommand: (Array<Any>) -> Unit = { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val targetId = data.optString("targetDeviceId", "")
                        val isForMe = targetId.isBlank() || targetId == myDeviceId
                        if (isForMe) {
                            val action = data.optString("action", "")
                            val payload = data.optJSONObject("payload")
                            if (action.isNotBlank()) {
                                if (_isActiveDevice.value || _activeDeviceId.value == myDeviceId) {
                                    try {
                                        remoteCommandHandler?.invoke(action, payload)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error executing remoteCommandHandler", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling remote command", e)
                }
            }

            s.on("REMOTE_COMMAND", handleRemoteCommand)
            s.on("REMOTE_CONTROL", handleRemoteCommand)

            s.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize socket", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
                try {
                    if (socket?.connected() == true) {
                        val payload = JSONObject().apply {
                            put("deviceId", myDeviceId)
                        }
                        socket?.emit("HEARTBEAT", payload)
                        Log.v(TAG, "Emitted HEARTBEAT for device $myDeviceId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat emit failed", e)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun broadcastPlaybackState(
        trackId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        isLoop: Boolean,
        isShuffle: Boolean,
        volume: Double = 1.0,
        isBuffering: Boolean = false,
        forceClaim: Boolean = false
    ) {
        if (socket?.connected() != true) return
        try {
            val payload = JSONObject().apply {
                put("activeDeviceId", myDeviceId)
                put("originDeviceId", myDeviceId)
                if (trackId != null) put("trackId", trackId) else put("trackId", JSONObject.NULL)
                put("positionMs", positionMs)
                put("durationMs", durationMs)
                put("isPlaying", isPlaying)
                put("isBuffering", isBuffering)
                put("volume", volume)
                put("isLoop", isLoop)
                put("isShuffle", isShuffle)
                put("forceClaim", forceClaim)
                put("updatedAt", System.currentTimeMillis())
            }
            socket?.emit("PLAYBACK_STATE_CHANGED", payload)

            _activeDeviceId.value = myDeviceId
            _isActiveDevice.value = true
            _remotePlaybackState.value = RemotePlaybackState(
                activeDeviceId = myDeviceId,
                trackId = trackId,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                volume = volume,
                isLoop = isLoop,
                isShuffle = isShuffle,
                originDeviceId = myDeviceId,
                updatedAt = System.currentTimeMillis()
            )
            _devices.value = _devices.value.map { d ->
                d.copy(isActive = d.deviceId == myDeviceId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting playback state", e)
        }
    }

    fun reportPosition(positionMs: Long, durationMs: Long) {
        if (socket?.connected() != true || !_isActiveDevice.value) return
        try {
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                put("durationMs", durationMs)
                put("originDeviceId", myDeviceId)
            }
            socket?.emit("POSITION_REPORT", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting POSITION_REPORT", e)
        }
    }

    fun transferPlayback(targetDeviceId: String, positionMs: Long? = null, durationMs: Long? = null) {
        try {
            val payload = JSONObject().apply {
                put("targetDeviceId", targetDeviceId)
                put("originDeviceId", myDeviceId)
                put("playImmediately", true)
                if (positionMs != null && positionMs >= 0) put("positionMs", positionMs)
                if (durationMs != null && durationMs > 0) put("durationMs", durationMs)
            }
            socket?.emit("TRANSFER_PLAYBACK", payload)

            val isBecomingActive = (targetDeviceId == myDeviceId)
            _activeDeviceId.value = targetDeviceId
            _isActiveDevice.value = isBecomingActive
            _devices.value = _devices.value.map { d ->
                d.copy(isActive = d.deviceId == targetDeviceId)
            }

            val current = _remotePlaybackState.value
            val newState = current?.copy(
                activeDeviceId = targetDeviceId,
                positionMs = positionMs ?: current.positionMs,
                durationMs = durationMs ?: current.durationMs,
                updatedAt = System.currentTimeMillis()
            ) ?: RemotePlaybackState(
                activeDeviceId = targetDeviceId,
                positionMs = positionMs ?: 0L,
                durationMs = durationMs ?: 0L,
                updatedAt = System.currentTimeMillis()
            )
            _remotePlaybackState.value = newState

            if (!isBecomingActive) {
                try {
                    onOtherDeviceActiveCallback?.invoke(targetDeviceId, newState)
                    onPlaybackStateReceived?.invoke(newState)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying callbacks on transferPlayback", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transferring playback", e)
        }
    }

    fun claimActiveDevice(positionMs: Long? = null, durationMs: Long? = null) {
        transferPlayback(myDeviceId, positionMs, durationMs)
    }

    fun sendRemoteControl(action: String, payload: JSONObject? = null, targetDeviceId: String? = null) {
        try {
            val obj = JSONObject().apply {
                put("action", action)
                put("originDeviceId", myDeviceId)
                val target = targetDeviceId ?: _activeDeviceId.value
                if (!target.isNullOrEmpty()) {
                    put("targetDeviceId", target)
                }
                if (payload != null) {
                    put("payload", payload)
                }
            }
            Log.d(TAG, "Sending REMOTE_CONTROL to server: $obj")
            socket?.emit("REMOTE_CONTROL", obj)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending remote control", e)
        }
    }

    fun refreshDevices() {
        if (socket?.connected() != true) {
            connect()
        } else {
            try {
                val payload = JSONObject().apply {
                    put("deviceId", myDeviceId)
                    put("deviceName", myDeviceName)
                    put("deviceType", "mobile")
                }
                socket?.emit("REGISTER_DEVICE", payload)
                Log.d(TAG, "Emitted REGISTER_DEVICE to refresh device list")
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting refresh REGISTER_DEVICE", e)
            }
        }
    }

    fun disconnect() {
        stopHeartbeat()
        try {
            socket?.disconnect()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting socket", e)
        }
        socket = null
        _isConnected.value = false
    }

    companion object {
        private const val TAG = "DeviceSyncManager"
    }
}
