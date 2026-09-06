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
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: VoiceShieldApi = retrofit.create(VoiceShieldApi::class.java)

    // Local Data
    val preferencesManager = PreferencesManager(context)
    val database = com.sagar.voice_shield.data.local.room.VoiceShieldDatabase.getDatabase(context)
    val callHistoryDao = database.callHistoryDao()

    // Repositories
    val authRepository = AuthRepository(api, preferencesManager)
    val analysisRepository = AnalysisRepository(api, callHistoryDao)

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

    // Audio Call Engine (Ringtone, Sound, Mic Analysis)
    val audioCallEngine = com.sagar.voice_shield.service.AudioCallEngine(
        context = context,
        prosodyAnalyzer = prosodyAnalyzer,
        riskEngine = riskEngine
    )
}
