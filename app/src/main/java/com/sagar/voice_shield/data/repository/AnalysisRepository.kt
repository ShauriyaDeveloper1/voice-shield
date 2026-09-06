package com.sagar.voice_shield.data.repository

import com.sagar.voice_shield.data.remote.VoiceShieldApi
import com.sagar.voice_shield.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

import com.sagar.voice_shield.data.local.room.CallHistoryDao
import com.sagar.voice_shield.data.local.room.CallHistoryEntity
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val api: VoiceShieldApi,
    private val callHistoryDao: CallHistoryDao
) {

    // --- Local DB ---
    fun getLocalCallHistory(): Flow<List<CallHistoryEntity>> {
        return callHistoryDao.getAllCalls()
    }

    suspend fun saveCallToLocal(call: CallHistoryEntity) {
        callHistoryDao.insertCall(call)
    }
    suspend fun analyzeCall(callId: String, features: AnalysisRequest): Result<AnalysisResponse> {
        return try {
            Result.success(api.analyzeCall(callId, features))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAudioFile(file: File): Result<AnalysisResponse> {
        return try {
            val requestBody = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            Result.success(api.uploadAudio(part))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCall(callerId: String, receiverId: String): Result<CallResponse> {
        return try {
            Result.success(api.createCall(CreateCallRequest(callerId, receiverId, userId = callerId)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlerts(userId: String): Result<List<AlertResponse>> {
        return try {
            Result.success(api.getUserAlerts(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getContacts(userId: String): Result<List<ContactResponse>> {
        return try {
            Result.success(api.getContacts(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
