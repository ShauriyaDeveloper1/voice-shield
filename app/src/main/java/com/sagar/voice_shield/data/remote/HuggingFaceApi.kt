package com.sagar.voice_shield.data.remote

import com.sagar.voice_shield.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * Retrofit interface for the VoiceShield HuggingFace Space API.
 * Space URL: https://shauriya24-voiceshield.hf.space/
 *
 * Provides real AI model inference:
 * - AASIST (Audio Anti-Spoofing) for deepfake detection
 * - XLM-RoBERTa for social engineering text analysis
 */
interface HuggingFaceApi {

    /**
     * Analyze audio for deepfake detection using AASIST model.
     * Accepts multipart WAV/MP3/FLAC files.
     */
    @Multipart
    @POST("analyze_audio")
    suspend fun analyzeAudio(@Part file: MultipartBody.Part): HfAudioAnalysisResponse

    /**
     * Analyze text for social engineering detection using XLM-RoBERTa.
     */
    @POST("analyze_text")
    suspend fun analyzeText(@Body request: HfTextAnalysisRequest): HfTextAnalysisResponse

    /**
     * Health check for the HuggingFace Space.
     */
    @GET("gradio_api/info")
    suspend fun healthCheck(): HfHealthResponse
}
