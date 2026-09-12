package com.sagar.voice_shield.data.repository

import android.util.Log
import com.sagar.voice_shield.data.local.PreferencesManager
import com.sagar.voice_shield.data.remote.VoiceShieldApi
import com.sagar.voice_shield.data.remote.dto.VoiceRegistrationResponse
import com.sagar.voice_shield.data.remote.dto.VoiceStatusResponse
import com.sagar.voice_shield.data.remote.dto.VoiceVerifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceIdentityRepository(
    private val api: VoiceShieldApi,
    private val preferencesManager: PreferencesManager
) {
    private val TAG = "VoiceIdentityRepo"

    suspend fun registerVoice(userId: String, wavBytes: ByteArray): Result<VoiceRegistrationResponse> = withContext(Dispatchers.IO) {
        try {
            val audioRequestBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", "enrollment_voice.wav", audioRequestBody)
            val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.registerVoiceIdentity(filePart, userIdBody)
            if (response.success && response.voiceIdentityRegistered) {
                preferencesManager.saveVoiceIdentity(
                    hash = response.voiceHash ?: "",
                    txHash = response.transactionHash,
                    version = response.version
                )
            }
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering voice identity", e)
            Result.failure(e)
        }
    }

    suspend fun getVoiceStatus(userId: String): Result<VoiceStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getVoiceIdentityStatus(userId)
            if (response.registered) {
                preferencesManager.saveVoiceIdentity(
                    hash = response.voiceHash ?: "",
                    txHash = response.transactionHash,
                    version = response.version
                )
            }
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching voice identity status", e)
            Result.failure(e)
        }
    }

    suspend fun verifyCallVoiceChunk(
        userId: String,
        chunkWavBytes: ByteArray,
        deepfakeProb: Double? = null
    ): Result<VoiceVerifyResponse> = withContext(Dispatchers.IO) {
        try {
            val audioRequestBody = chunkWavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", "chunk.wav", audioRequestBody)
            val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val dfBody = deepfakeProb?.let { it.toString().toRequestBody("text/plain".toMediaTypeOrNull()) }

            val response = api.verifyCallVoiceChunk(filePart, userIdBody, dfBody)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Voice chunk verification failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun revokeVoice(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            api.revokeVoiceIdentity(userId)
            preferencesManager.clearVoiceIdentity()
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed revoking voice identity", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Helper to wrap raw 16-bit linear PCM into a valid WAV format byte array.
         */
        fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
            val totalAudioLen = pcmData.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channels * 2

            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray())
            buffer.putInt(totalDataLen)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16) // Subchunk1Size for PCM
            buffer.putShort(1)  // AudioFormat 1 = PCM
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort((channels * 2).toShort()) // BlockAlign
            buffer.putShort(16) // BitsPerSample
            buffer.put("data".toByteArray())
            buffer.putInt(totalAudioLen)

            val out = ByteArrayOutputStream(44 + pcmData.size)
            out.write(header)
            out.write(pcmData)
            return out.toByteArray()
        }
    }
}
