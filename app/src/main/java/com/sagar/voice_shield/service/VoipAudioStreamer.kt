package com.sagar.voice_shield.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance VoIP audio capture and playback engine.
 * Streams G.711 mu-law compressed audio chunks over WebSocket (port 443 WSS)
 * guaranteeing 100% reliable cross-network voice calling across mobile CGNAT (Jio, Airtel, Vi),
 * cellular to Wi-Fi, and symmetric NAT environments where WebRTC direct P2P fails.
 */
class VoipAudioStreamer(
    private val context: Context,
    private val sendSignalingMessage: (JsonObject) -> Unit
) {
    private val TAG = "VoipAudioStreamer"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val isRunning = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isAlertPlaying = AtomicBoolean(false)
    private var activePeerPhone: String = ""

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    private val userSpeakerChoice = AtomicBoolean(false)
    private var commDeviceListener: AudioManager.OnCommunicationDeviceChangedListener? = null

    val sampleRate = 16000
    // 100ms chunk = 1600 samples at 16kHz
    private val frameSamples = 1600

    var onPeerAudioDecoded: ((pcmBytes: ByteArray, sampleRate: Int) -> Unit)? = null

    fun setAlertPlaying(playing: Boolean) {
        isAlertPlaying.set(playing)
        Log.d(TAG, "Local warning alert playing state: $playing (mic outbound suppressed=$playing)")
    }

    fun applyAudioTrackDevice(speaker: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val targetDevice = if (speaker) {
                    outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                }
                if (targetDevice != null) {
                    val res = audioTrack?.setPreferredDevice(targetDevice)
                    Log.d(TAG, "audioTrack.setPreferredDevice(${targetDevice.type}): $res")
                } else if (speaker) {
                    audioTrack?.setPreferredDevice(null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting AudioTrack preferred device", e)
            }
        }
    }

    fun enforceAudioRouting(speaker: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val available = audioManager.availableCommunicationDevices
                if (speaker) {
                    val speakerDevice = available.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        val success = audioManager.setCommunicationDevice(speakerDevice)
                        Log.d(TAG, "enforceAudioRouting setCommunicationDevice SPEAKER: $success")
                    }
                } else {
                    val earpieceDevice = available.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    } ?: available.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (earpieceDevice != null) {
                        val success = audioManager.setCommunicationDevice(earpieceDevice)
                        Log.d(TAG, "enforceAudioRouting setCommunicationDevice EARPIECE: $success")
                    } else {
                        Log.w(TAG, "enforceAudioRouting: No earpiece found among ${available.map { it.type }}")
                    }
                }
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speaker

            applyAudioTrackDevice(speaker)
        } catch (e: Exception) {
            Log.w(TAG, "Error enforcing audio routing (speaker=$speaker)", e)
        }
    }

    private fun registerDeviceListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterDeviceListener()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                    val wantsSpeaker = userSpeakerChoice.get()
                    val isSpeaker = (device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    Log.d(TAG, "OnCommunicationDeviceChanged: type=${device?.type}, userWantsSpeaker=$wantsSpeaker")
                    // If the user did NOT choose speaker, but Android/HAL auto-switched to speaker, force back to EARPIECE!
                    if (!wantsSpeaker && isSpeaker) {
                        Log.w(TAG, "Auto-switch to SPEAKER detected! Immediately forcing back to EARPIECE.")
                        scope.launch {
                            delay(50)
                            enforceAudioRouting(false)
                        }
                    }
                }
                commDeviceListener = listener
                audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
                Log.d(TAG, "Registered OnCommunicationDeviceChangedListener")
            } catch (e: Exception) {
                Log.w(TAG, "Failed registering communication device listener", e)
            }
        }
    }

    private fun unregisterDeviceListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            commDeviceListener?.let { listener ->
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.removeOnCommunicationDeviceChangedListener(listener)
                    Log.d(TAG, "Unregistered OnCommunicationDeviceChangedListener")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed unregistering communication device listener", e)
                }
            }
            commDeviceListener = null
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        userSpeakerChoice.set(enabled)
        _isSpeakerOn.value = enabled
        enforceAudioRouting(enabled)
        Log.d(TAG, "User toggled speakerphone: $enabled (earpiece=${!enabled})")
    }

    @SuppressLint("MissingPermission")
    fun start(peerPhone: String) {
        if (isRunning.get() && activePeerPhone == peerPhone) {
            Log.d(TAG, "VoipAudioStreamer already active for $peerPhone")
            return
        }
        stop()
        activePeerPhone = peerPhone
        isRunning.set(true)
        userSpeakerChoice.set(false)
        _isSpeakerOn.value = false

        configureAudioManager()
        initAudioTrack()
        startMicrophoneCapture()
    }

    private fun configureAudioManager() {
        try {
            // DEFAULT: EARPIECE routing locked down
            enforceAudioRouting(false)
            registerDeviceListener()
            Log.d(TAG, "Audio routed to MODE_IN_COMMUNICATION with EARPIECE default & device listener active")
        } catch (e: Exception) {
            Log.w(TAG, "Failed configuring AudioManager", e)
        }
    }

    private fun initAudioTrack() {
        try {
            val minTrackBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufSize = maxOf(minTrackBuf, frameSamples * 4)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                trackBufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                applyAudioTrackDevice(_isSpeakerOn.value)
                Log.d(TAG, "AudioTrack initialized and playing in STREAM mode (earpiece default)")
            } else {
                Log.e(TAG, "AudioTrack failed to initialize (state: ${audioTrack?.state})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioTrack", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneCapture() {
        scope.launch {
            try {
                val minRecBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val recBufSize = maxOf(minRecBuf, frameSamples * 4)

                // Prefer VOICE_COMMUNICATION for automatic hardware echo suppression & beamforming
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        recBufSize
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VOICE_COMMUNICATION source failed, falling back to MIC", e)
                }

                if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        recBufSize
                    )
                }

                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    try {
                        if (AcousticEchoCanceler.isAvailable()) {
                            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d(TAG, "Hardware AcousticEchoCanceler attached: ${echoCanceler?.enabled}")
                        }
                        if (NoiseSuppressor.isAvailable()) {
                            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d(TAG, "Hardware NoiseSuppressor attached: ${noiseSuppressor?.enabled}")
                        }
                    } catch (fxErr: Exception) {
                        Log.w(TAG, "Could not attach audio effects", fxErr)
                    }
                }

                audioRecord?.startRecording()
                Log.d(TAG, "AudioRecord started recording at $sampleRate Hz")

                val shortBuffer = ShortArray(frameSamples)
                var sequenceNum = 0L

                while (isRunning.get()) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0 && !isMuted.get() && activePeerPhone.isNotBlank()) {
                        // If warning alert ("Be aware") is playing locally to user,
                        // transmit silence frame so the remote caller cannot hear the warning!
                        val bufferToSend = if (isAlertPlaying.get()) {
                            ShortArray(read)
                        } else {
                            shortBuffer
                        }
                        // Compress 16-bit linear PCM to 8-bit G.711 mu-law
                        val ulawData = G711Codec.encode(bufferToSend, read)
                        val base64 = Base64.encodeToString(ulawData, Base64.NO_WRAP)

                        val chunkMsg = JsonObject().apply {
                            addProperty("type", "audio_chunk")
                            addProperty("to_phone", activePeerPhone)
                            addProperty("audio", base64)
                            addProperty("sr", sampleRate)
                            addProperty("codec", "ulaw")
                            addProperty("seq", sequenceNum++)
                        }
                        sendSignalingMessage(chunkMsg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in microphone capture loop", e)
            } finally {
                cleanupMicrophone()
            }
        }
    }

    /**
     * Receives encoded audio chunk from remote peer via WebSocket and plays it to speaker.
     */
    fun handleIncomingAudioChunk(audioBase64: String, codec: String = "ulaw") {
        if (!isRunning.get() || audioTrack == null) return

        try {
            val encodedBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
            if (encodedBytes.isEmpty()) return

            val pcmBytes: ByteArray = if (codec == "ulaw") {
                G711Codec.decodeToPcmBytes(encodedBytes)
            } else {
                encodedBytes
            }

            // 1. Play directly to speaker with low latency
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(pcmBytes, 0, pcmBytes.size, AudioTrack.WRITE_NON_BLOCKING)
                }
            }

            // 2. Feed decoded PCM into real-time deepfake & prosody analysis pipeline
            onPeerAudioDecoded?.invoke(pcmBytes, sampleRate)
        } catch (e: Exception) {
            Log.w(TAG, "Error handling incoming audio chunk", e)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        Log.d(TAG, "VoipAudioStreamer mute state: $muted")
    }

    @Synchronized
    private fun cleanupMicrophone() {
        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
            Log.d(TAG, "Microphone cleaned up")
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up microphone", e)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.d(TAG, "Stopping VoipAudioStreamer...")
        activePeerPhone = ""

        cleanupMicrophone()

        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            audioTrack = null
            Log.d(TAG, "AudioTrack released")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }

        isAlertPlaying.set(false)
        _isSpeakerOn.value = false

        unregisterDeviceListener()

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting AudioManager mode", e)
        }
    }
}
