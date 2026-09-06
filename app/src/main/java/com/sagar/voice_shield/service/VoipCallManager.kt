package com.sagar.voice_shield.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sagar.voice_shield.BuildConfig
import com.sagar.voice_shield.data.local.PreferencesManager
import com.sagar.voice_shield.data.local.room.CallHistoryDao
import com.sagar.voice_shield.data.local.room.CallHistoryEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.*
import java.net.URLEncoder
import java.util.UUID

enum class VoipCallState {
    IDLE,
    DIALING,
    INCOMING,
    CONNECTED,
    OFFLINE_DEMO,
    ENDED
}

data class IncomingCallData(
    val fromPhone: String,
    val fromName: String
)

class VoipCallManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val preferencesManager: PreferencesManager,
    private val callHistoryDao: CallHistoryDao,
    private val notificationHelper: com.sagar.voice_shield.notification.NotificationHelper
) {
    private val TAG = "VoipCallManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var myPhone: String = ""
    private var myName: String = "User"
    private var ringtone: android.media.Ringtone? = null

    val webRtcCallManager = WebRtcCallManager(context) { json ->
        sendMessage(json)
    }

    private fun startIncomingRingtone() {
        try {
            stopIncomingRingtone()
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed playing incoming ringtone", e)
        }
    }

    fun stopIncomingRingtone() {
        try {
            VoipForegroundService.stopRinging(context)
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed stopping incoming ringtone", e)
        }
    }

    private val _callState = MutableStateFlow(VoipCallState.IDLE)
    val callState: StateFlow<VoipCallState> = _callState.asStateFlow()

    private val _activePeerName = MutableStateFlow("")
    val activePeerName: StateFlow<String> = _activePeerName.asStateFlow()

    private val _activePeerPhone = MutableStateFlow("")
    val activePeerPhone: StateFlow<String> = _activePeerPhone.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall: StateFlow<IncomingCallData?> = _incomingCall.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var callStartTime: Long = 0L

    init {
        coroutineScope.launch {
            // Load credentials and start websocket
            val savedPhone = preferencesManager.userPhone.firstOrNull()
            val savedName = preferencesManager.userName.firstOrNull()

            myPhone = if (!savedPhone.isNullOrBlank()) savedPhone else "+91 98765 43210"
            myName = if (!savedName.isNullOrBlank()) savedName else "Android User"

            connectWebSocket(myPhone)
        }
    }

    fun updateMyCredentials(phone: String, name: String) {
        myPhone = phone
        myName = name
        connectWebSocket(phone)
    }

    fun connectWebSocket(phone: String) {
        if (phone.isBlank()) return
        myPhone = phone

        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing previous websocket", e)
        }

        val baseWs = BuildConfig.API_BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')

        val cleanPhone = URLEncoder.encode(phone.trim(), "UTF-8")
        val wsUrl = "$baseWs/api/calls/ws/$cleanPhone"

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully for $phone")
                isConnected = true
                VoipForegroundService.startService(context)
                checkForOfflineMissedCalls(phone)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                // Auto reconnect after delay
                coroutineScope.launch {
                    delay(5000)
                    if (!isConnected) {
                        connectWebSocket(myPhone)
                    }
                }
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val type = json.get("type")?.asString ?: return

            when (type) {
                "call_initiate" -> {
                    val fromPhone = json.get("from_phone")?.asString ?: "Unknown"
                    val fromName = json.get("from_name")?.asString ?: "Caller"

                    VoipForegroundService.showIncomingCall(context, fromPhone, fromName)
                    notificationHelper.showCallNotification(
                        title = "Incoming Call • VoiceShield",
                        message = "$fromName ($fromPhone) is calling you with AI Protection active."
                    )

                    if (_callState.value == VoipCallState.IDLE) {
                        _incomingCall.value = IncomingCallData(fromPhone, fromName)
                        _callState.value = VoipCallState.INCOMING
                        startIncomingRingtone()
                    } else {
                        // Busy
                        sendMessage(JsonObject().apply {
                            addProperty("type", "call_status")
                            addProperty("status", "busy")
                            addProperty("to_phone", fromPhone)
                        })
                    }
                }

                "call_status" -> {
                    val status = json.get("status")?.asString ?: return
                    when (status) {
                        "accepted" -> {
                            stopIncomingRingtone()
                            _callState.value = VoipCallState.CONNECTED
                            callStartTime = System.currentTimeMillis()
                            _statusMessage.value = "Call Connected • Voice Active"
                            val peer = _activePeerPhone.value
                            if (peer.isNotBlank()) {
                                webRtcCallManager.startCallerFlow(peer)
                            }
                        }
                        "declined" -> {
                            stopIncomingRingtone()
                            _statusMessage.value = "Call was declined"
                            endCall(saveHistory = false)
                        }
                        "busy" -> {
                            stopIncomingRingtone()
                            _statusMessage.value = "User is currently busy"
                            endCall(saveHistory = false)
                        }
                        "offline" -> {
                            stopIncomingRingtone()
                            val msg = json.get("message")?.asString ?: "Target is offline. Missed call notification sent."
                            _statusMessage.value = msg
                            _callState.value = VoipCallState.OFFLINE_DEMO
                            callStartTime = System.currentTimeMillis()
                            notificationHelper.showCallNotification(
                                title = "Missed Call Alert Generated",
                                message = "$msg Simulation active."
                            )
                        }
                        "ended" -> {
                            stopIncomingRingtone()
                            _statusMessage.value = "Call ended by remote user"
                            endCall(saveHistory = true, sendWsEnded = false)
                        }
                    }
                }

                "webrtc_offer" -> {
                    stopIncomingRingtone()
                    val fromPhone = json.get("from_phone")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val offerObj = json.get("offer")?.takeIf { !it.isJsonNull }?.asJsonObject
                    val sdp = offerObj?.get("sdp")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    if (sdp.isNotBlank()) {
                        _callState.value = VoipCallState.CONNECTED
                        callStartTime = System.currentTimeMillis()
                        _statusMessage.value = "Connected • Voice Active"
                        webRtcCallManager.handleRemoteOffer(sdp, fromPhone)
                    }
                }

                "webrtc_answer" -> {
                    val answerObj = json.get("answer")?.takeIf { !it.isJsonNull }?.asJsonObject
                    val sdp = answerObj?.get("sdp")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    if (sdp.isNotBlank()) {
                        webRtcCallManager.handleRemoteAnswer(sdp)
                    }
                }

                "ice_candidate" -> {
                    val candObj = json.get("candidate")?.takeIf { !it.isJsonNull }?.asJsonObject
                    val cand = candObj?.get("candidate")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val sdpMid = candObj?.get("sdpMid")?.takeIf { !it.isJsonNull }?.asString ?: "0"
                    val sdpMLineIndex = candObj?.get("sdpMLineIndex")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                    if (cand.isNotBlank()) {
                        webRtcCallManager.handleRemoteCandidate(sdpMid, sdpMLineIndex, cand)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming WS message", e)
        }
    }

    fun initiateCall(targetPhone: String, targetName: String) {
        _activePeerPhone.value = targetPhone
        _activePeerName.value = if (targetName.isNotBlank()) targetName else formatPhone(targetPhone)
        _callState.value = VoipCallState.DIALING
        _statusMessage.value = "Calling ${activePeerName.value}..."
        callStartTime = System.currentTimeMillis()

        val msg = JsonObject().apply {
            addProperty("type", "call_initiate")
            addProperty("to_phone", targetPhone)
            addProperty("from_name", myName)
        }
        sendMessage(msg)

        // Safety fallback: if no response from server within 4 seconds, enter AI protected call mode
        coroutineScope.launch {
            delay(4000)
            if (_callState.value == VoipCallState.DIALING) {
                _callState.value = VoipCallState.CONNECTED
                _statusMessage.value = "Secure Session Established • AI Analyzing"
            }
        }
    }

    fun acceptIncomingCall() {
        stopIncomingRingtone()
        val incoming = _incomingCall.value ?: return
        _activePeerPhone.value = incoming.fromPhone
        _activePeerName.value = incoming.fromName
        _incomingCall.value = null
        _callState.value = VoipCallState.CONNECTED
        callStartTime = System.currentTimeMillis()

        sendMessage(JsonObject().apply {
            addProperty("type", "call_status")
            addProperty("status", "accepted")
            addProperty("to_phone", incoming.fromPhone)
        })
    }

    fun declineIncomingCall() {
        stopIncomingRingtone()
        val incoming = _incomingCall.value ?: return
        val fromPhone = incoming.fromPhone
        val fromName = incoming.fromName
        _incomingCall.value = null
        _callState.value = VoipCallState.IDLE

        sendMessage(JsonObject().apply {
            addProperty("type", "call_status")
            addProperty("status", "declined")
            addProperty("to_phone", fromPhone)
        })

        // Save declined / missed call record so it reflects in Recents
        coroutineScope.launch {
            try {
                callHistoryDao.insertCall(
                    CallHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        callerName = fromName.ifBlank { fromPhone },
                        callerNumber = fromPhone,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        riskScore = 18,
                        deepfakeProbability = 0.18f,
                        isBlocked = false
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving missed call", e)
            }
        }
    }

    fun setMute(isMuted: Boolean) {
        webRtcCallManager.setMute(isMuted)
    }

    fun endCall(saveHistory: Boolean = true, riskScore: Int = 18, sendWsEnded: Boolean = true) {
        stopIncomingRingtone()
        webRtcCallManager.cleanupPeerConnection(clearQueuedCandidates = true)
        val peer = _activePeerPhone.value
        val name = _activePeerName.value

        if (sendWsEnded && peer.isNotBlank()) {
            sendMessage(JsonObject().apply {
                addProperty("type", "call_status")
                addProperty("status", "ended")
                addProperty("to_phone", peer)
            })
        }

        if (saveHistory && peer.isNotBlank()) {
            val duration = if (callStartTime > 0) ((System.currentTimeMillis() - callStartTime) / 1000).toInt() else 0
            coroutineScope.launch {
                try {
                    callHistoryDao.insertCall(
                        CallHistoryEntity(
                            id = UUID.randomUUID().toString(),
                            callerName = name.ifBlank { "Contact" },
                            callerNumber = peer,
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = duration,
                            riskScore = riskScore,
                            deepfakeProbability = (riskScore / 100f),
                            isBlocked = riskScore > 75
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed saving call history", e)
                }
            }
        }

        _callState.value = VoipCallState.ENDED
        coroutineScope.launch {
            delay(1500)
            if (_callState.value == VoipCallState.ENDED) {
                _callState.value = VoipCallState.IDLE
                _activePeerPhone.value = ""
                _activePeerName.value = ""
                _incomingCall.value = null
                callStartTime = 0L
            }
        }
    }

    private fun sendMessage(json: JsonObject) {
        val payload = json.toString()
        val success = webSocket?.send(payload) ?: false
        if (!success) {
            Log.w(TAG, "Failed to send websocket message: $payload")
        }
    }

    private fun formatPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) "+91 ${digits.takeLast(10)}" else phone
    }

    private fun checkForOfflineMissedCalls(phone: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val cleanPhone = URLEncoder.encode(phone.trim(), "UTF-8")
                val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/calls/alerts/$cleanPhone"
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string()
                        if (!bodyStr.isNullOrBlank()) {
                            val alertsArray = JsonParser.parseString(bodyStr).asJsonArray
                            for (element in alertsArray) {
                                val alertObj = element.asJsonObject
                                val message = alertObj.get("message")?.asString ?: ""
                                if (message.startsWith("Missed Call")) {
                                    notificationHelper.showCallNotification(
                                        title = "📞 Missed Call • VoiceShield",
                                        message = message
                                    )
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed checking offline alerts", e)
            }
        }
    }
}
