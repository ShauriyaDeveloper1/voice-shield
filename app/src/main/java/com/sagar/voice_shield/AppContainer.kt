package com.sagar.voice_shield

import android.content.Context
import com.sagar.voice_shield.data.local.PreferencesManager
import com.sagar.voice_shield.data.remote.VoiceShieldApi
import com.sagar.voice_shield.data.repository.AnalysisRepository
import com.sagar.voice_shield.data.repository.AuthRepository
import com.sagar.voice_shield.ml.ProsodyAnalyzer
import com.sagar.voice_shield.ml.RiskEngine
import com.sagar.voice_shield.notification.NotificationHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection container.
 * Replaces Hilt until it supports AGP 9.
 */
class AppContainer(context: Context) {

    // Network
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: VoiceShieldApi = retrofit.create(VoiceShieldApi::class.java)

    // HuggingFace Space API (longer timeouts for ZeroGPU cold-start)
    private val hfOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val hfRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.HF_SPACE_URL)
        .client(hfOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val huggingFaceApi: com.sagar.voice_shield.data.remote.HuggingFaceApi =
        hfRetrofit.create(com.sagar.voice_shield.data.remote.HuggingFaceApi::class.java)

    val huggingFaceGradioClient = com.sagar.voice_shield.data.remote.HuggingFaceGradioClient(
        client = hfOkHttpClient,
        baseUrl = BuildConfig.HF_SPACE_URL,
        backendApi = api
    )

    // Local Data
    val preferencesManager = PreferencesManager(context)
    val database = com.sagar.voice_shield.data.local.room.VoiceShieldDatabase.getDatabase(context)
    val callHistoryDao = database.callHistoryDao()
    val trustedContactDao = database.trustedContactDao()
    val contactsSyncManager = com.sagar.voice_shield.data.local.ContactsSyncManager(context, trustedContactDao)

    // Repositories
    val authRepository = AuthRepository(api, huggingFaceApi, preferencesManager)
    val analysisRepository = AnalysisRepository(api, callHistoryDao)
    val voiceIdentityRepository = com.sagar.voice_shield.data.repository.VoiceIdentityRepository(api, preferencesManager)

    // ML
    val riskEngine = RiskEngine()
    val prosodyAnalyzer = ProsodyAnalyzer()

    // Notification
    val notificationHelper = NotificationHelper(context)

    // VOIP Calling Service
    val voipCallManager = com.sagar.voice_shield.service.VoipCallManager(
        context = context,
        okHttpClient = okHttpClient,
        preferencesManager = preferencesManager,
        callHistoryDao = callHistoryDao,
        notificationHelper = notificationHelper
    )

    // Audio Call Engine (Ringtone, Sound, Mic Analysis + AI Model Pipeline)
    val audioCallEngine = com.sagar.voice_shield.service.AudioCallEngine(
        context = context,
        prosodyAnalyzer = prosodyAnalyzer,
        riskEngine = riskEngine,
        hfApi = huggingFaceApi,
        backendApi = api,
        hfGradioClient = huggingFaceGradioClient
    )

    init {
        // Wire WebRTC audio capture directly to AudioCallEngine for real-time AI deepfake analysis
        voipCallManager.webRtcCallManager.onAudioChunkCaptured = { pcmData, sampleRate ->
            audioCallEngine.feedAudioChunk(pcmData, sampleRate)
        }

        // Wire incoming live VoIP audio stream directly to AudioCallEngine for real-time AI deepfake analysis
        voipCallManager.audioStreamer.onPeerAudioDecoded = { pcmData, sampleRate ->
            audioCallEngine.feedAudioChunk(pcmData, sampleRate)
        }
    }
}
