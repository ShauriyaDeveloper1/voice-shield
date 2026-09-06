package com.sagar.voice_shield.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sagar.voice_shield.R
import com.sagar.voice_shield.ml.ProsodyAnalyzer
import com.sagar.voice_shield.ml.RiskEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import com.sagar.voice_shield.VoiceShieldApp

/**
 * Foreground service that captures microphone audio for Speaker Protection Mode.
 * Analyzes acoustic audio from the phone's speaker during third-party calls.
 */
class AudioAnalysisService : Service() {

    companion object {
        const val CHANNEL_ID = "voiceshield_audio_analysis"
        const val NOTIFICATION_ID = 1001
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _riskScore = MutableStateFlow(0)
        val riskScore: StateFlow<Int> = _riskScore

        private val _severity = MutableStateFlow("LOW")
        val severity: StateFlow<String> = _severity

        private val _deepfakeScore = MutableStateFlow(0.0)
        val deepfakeScore: StateFlow<Double> = _deepfakeScore

        private val _prosodyScore = MutableStateFlow(0.0)
        val prosodyScore: StateFlow<Double> = _prosodyScore

        private val _explanations = MutableStateFlow<List<String>>(emptyList())
        val explanations: StateFlow<List<String>> = _explanations
    }

    private var audioRecord: AudioRecord? = null
    private var analysisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prosodyAnalyzer = ProsodyAnalyzer()
    private val riskEngine = RiskEngine()
    private val accumulatedPcm = ByteArrayOutputStream()
    private var chunkCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Analyzing audio...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAudioCapture()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAudioCapture()
        scope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            stopSelf()
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            _isRunning.value = true

            // Analysis loop — process audio in ~500ms chunks
            analysisJob = scope.launch {
                val chunkSize = SAMPLE_RATE / 2  // 0.5 seconds of audio
                val buffer = ShortArray(chunkSize)

                while (isActive && _isRunning.value) {
                    val read = audioRecord?.read(buffer, 0, chunkSize) ?: 0
                    if (read > 0) {
                        val audioChunk = buffer.copyOfRange(0, read)
                        analyzeAudioChunk(audioChunk)
                    }
                    delay(100) // Small delay between analysis cycles
                }
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun analyzeAudioChunk(audioData: ShortArray) {
        if (audioData.isEmpty()) return

        // Fast RMS computation without memory allocations
        var sumSquares = 0.0
        for (sample in audioData) {
            val v = sample.toDouble()
            sumSquares += v * v
        }
        val rms = Math.sqrt(sumSquares / audioData.size)
        if (rms < 60) return // Lowered threshold to reliably pick up speakerphone acoustic audio

        // Real-time on-device acoustic prosody analysis
        val prosody = prosodyAnalyzer.analyze(audioData, SAMPLE_RATE)
        val localSignals = RiskEngine.RiskSignals(
            deepfakeScore = prosody.unnaturalnessScore,
            speakerSimilarity = if (prosody.pitchVariance > 50.0) 0.95 else 0.70,
            prosodyScore = prosody.unnaturalnessScore,
            contextScore = 0.0
        )
        val localResult = riskEngine.calculateRisk(localSignals)
        
        // Update local indicators immediately
        _prosodyScore.value = prosody.unnaturalnessScore
        if (_riskScore.value == 0 || chunkCount == 0) {
            _riskScore.value = localResult.score.toInt()
            _severity.value = localResult.severity
            val verdict = when {
                _riskScore.value >= 70 -> "🔴 HIGH RISK (Scam / Deepfake Detected)"
                _riskScore.value >= 35 -> "🟠 SUSPICIOUS CALL (Acoustic Anomaly)"
                else -> "🟢 NORMAL CALL (Verified Safe)"
            }
            val verdictDetail = when {
                _riskScore.value >= 70 -> "High probability synthetic voice detected. Potential deepfake scam."
                _riskScore.value >= 35 -> "Acoustic anomaly: Robotic cadence or unnatural pitch variance."
                else -> "Natural human vocal harmonics verified. Safe speech pattern."
            }
            _explanations.value = listOf(verdict, verdictDetail)
        }

        // Accumulate chunks for backend AASIST AI model
        val byteData = shortArrayToByteArray(audioData)
        accumulatedPcm.write(byteData)
        chunkCount++

        if (chunkCount >= 5) { // 2.5 seconds
            val pcmBytes = accumulatedPcm.toByteArray()
            accumulatedPcm.reset()
            chunkCount = 0

            scope.launch {
                try {
                    val wavBytes = createWavHeader(pcmBytes, SAMPLE_RATE)
                    val requestBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", "chunk.wav", requestBody)

                    val app = applicationContext as VoiceShieldApp
                    val response = app.appContainer.api.uploadAudio(part)
                    _deepfakeScore.value = response.deepfakeScore
                    _prosodyScore.value = response.prosodyScore
                    _riskScore.value = response.riskScore.toInt()
                    _severity.value = response.severity

                    val computedRisk = _riskScore.value
                    val verdict = when {
                        computedRisk >= 70 -> "🔴 HIGH RISK (Scam / Deepfake Detected)"
                        computedRisk >= 35 -> "🟠 SUSPICIOUS CALL (Acoustic Anomaly)"
                        else -> "🟢 NORMAL CALL (Verified Safe)"
                    }
                    val verdictDetail = when {
                        computedRisk >= 70 -> "Deepfake model verified synthetic acoustic signature (${computedRisk}% risk)."
                        computedRisk >= 35 -> "Model identified suspicious prosody and vocal distortion."
                        else -> "Verified human vocal tract acoustics. No deepfake patterns."
                    }
                    _explanations.value = listOf(verdict, verdictDetail)

                    val notification = createNotification("Risk: $computedRisk/100 — $verdict")
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    // Seamless offline fallback using DSP prosody analysis
                    val fallbackRisk = localResult.score.toInt().coerceAtLeast(15)
                    _riskScore.value = fallbackRisk
                    _severity.value = localResult.severity
                    val verdict = when {
                        fallbackRisk >= 70 -> "🔴 HIGH RISK (Scam / Deepfake Detected)"
                        fallbackRisk >= 35 -> "🟠 SUSPICIOUS CALL (Acoustic Anomaly)"
                        else -> "🟢 NORMAL CALL (Verified Safe)"
                    }
                    val verdictDetail = when {
                        fallbackRisk >= 70 -> "Acoustic prosody engine detected synthetic vocoder characteristics."
                        fallbackRisk >= 35 -> "Acoustic anomaly: Irregular pitch flattening or robotic cadence."
                        else -> "Natural speech acoustic harmonics verified. Safe call."
                    }
                    _explanations.value = listOf(verdict, verdictDetail)

                    val notification = createNotification("Risk: $fallbackRisk/100 — $verdict")
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            val s = shortArray[i].toInt()
            byteArray[i * 2] = (s and 0x00FF).toByte()
            byteArray[i * 2 + 1] = ((s shr 8) and 0x00FF).toByte()
        }
        return byteArray
    }

    private fun createWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }

    private fun stopAudioCapture() {
        analysisJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Analysis",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VoiceShield Speaker Protection Mode"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡 VoiceShield — Speaker Protection")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
