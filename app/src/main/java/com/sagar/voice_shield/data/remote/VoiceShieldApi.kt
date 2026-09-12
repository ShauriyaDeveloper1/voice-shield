package com.sagar.voice_shield.data.remote

import com.sagar.voice_shield.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface VoiceShieldApi {

    // ── Health ──
    @GET("health")
    suspend fun healthCheck(): HealthResponse

    // ── Auth ──
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/auth/send-verification")
    suspend fun sendVerification(@Body request: VerificationRequest): MessageResponse

    @POST("api/auth/confirm-profile")
    suspend fun confirmProfile(@Body request: ConfirmProfileRequest): ConfirmProfileResponse

    @POST("api/auth/google")
    suspend fun googleAuth(@Body request: GoogleAuthRequest): GoogleAuthResponse

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): SendOtpResponse

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): VerifyOtpResponse

    // ── Calls ──
    @POST("api/calls/")
    suspend fun createCall(@Body request: CreateCallRequest): CallResponse

    @GET("api/calls/{callId}")
    suspend fun getCall(@Path("callId") callId: String): CallResponse

    @POST("api/calls/{callId}/analysis")
    suspend fun analyzeCall(
        @Path("callId") callId: String,
        @Body request: AnalysisRequest
    ): AnalysisResponse

    @GET("api/calls/{callId}/analysis")
    suspend fun getCallAnalysis(@Path("callId") callId: String): List<AnalysisResponse>

    @GET("api/calls/{callId}/risk")
    suspend fun getCallRisk(@Path("callId") callId: String): RiskResponse

    // ── Analysis ──
    @POST("api/analysis/")
    suspend fun analyzeAudio(@Body request: AnalysisRequest): AnalysisResponse

    @Multipart
    @POST("api/analysis/upload")
    suspend fun uploadAudio(@Part file: MultipartBody.Part): AnalysisResponse

    // ── Users ──
    @PUT("api/users/{userId}")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body request: UpdateProfileRequest
    ): ProfileResponse

    @GET("api/users/{userId}/alerts")
    suspend fun getUserAlerts(@Path("userId") userId: String): List<AlertResponse>

    // ── Contacts ──
    @GET("api/contacts/")
    suspend fun getContacts(@Query("user_id") userId: String): List<ContactResponse>

    @POST("api/contacts/")
    suspend fun addContact(
        @Query("user_id") userId: String,
        @Body request: AddContactRequest
    ): ContactResponse

    @DELETE("api/contacts/{contactId}")
    suspend fun deleteContact(@Path("contactId") contactId: String): MessageResponse

    // ── Spam Reporting ──
    @POST("api/reports/report")
    suspend fun reportNumber(@Body request: ReportNumberRequest): ReportNumberResponse

    @GET("api/reports/check/{phone}")
    suspend fun checkSpam(@Path("phone") phone: String): SpamCheckResponse

    // ── Voice Identity & Blockchain ──
    @Multipart
    @POST("api/voice/register")
    suspend fun registerVoiceIdentity(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: okhttp3.RequestBody
    ): VoiceRegistrationResponse

    @GET("api/voice/status")
    suspend fun getVoiceIdentityStatus(
        @Query("user_id") userId: String
    ): VoiceStatusResponse

    @Multipart
    @POST("api/voice/verify")
    suspend fun verifyCallVoiceChunk(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: okhttp3.RequestBody,
        @Part("deepfake_prob") deepfakeProb: okhttp3.RequestBody? = null
    ): VoiceVerifyResponse

    @FormUrlEncoded
    @POST("api/voice/revoke")
    suspend fun revokeVoiceIdentity(
        @Field("user_id") userId: String
    ): MessageResponse
}
