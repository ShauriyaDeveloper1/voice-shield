package com.sagar.voice_shield.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Auth DTOs ──
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val message: String,
    @SerializedName("access_token") val accessToken: String,
    val user: UserDto
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

data class RegisterResponse(
    val message: String,
    @SerializedName("supabase_auth") val supabaseAuth: Boolean = false,
    val profile: ProfileDto? = null
)

data class VerificationRequest(
    val email: String,
    val name: String? = null,
    val phone: String? = null,
    @SerializedName("redirect_to") val redirectTo: String? = null
)

data class ConfirmProfileRequest(
    val id: String,
    val email: String,
    val name: String? = null,
    val phone: String? = null
)

data class ConfirmProfileResponse(
    val message: String,
    val profile: ProfileDto? = null
)

data class GoogleAuthRequest(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("id_token") val idToken: String? = null,
    @SerializedName("provider_token") val providerToken: String? = null
)

data class GoogleAuthResponse(
    val message: String,
    val user: UserDto
)

// ── User DTOs ──
data class UserDto(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?
)

data class ProfileDto(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?,
    val role: String? = null
)

data class UpdateProfileRequest(
    val name: String,
    val email: String,
    val phone: String?
)

data class ProfileResponse(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?
)

// ── Call DTOs ──
data class CreateCallRequest(
    @SerializedName("caller_id") val callerId: String,
    @SerializedName("receiver_id") val receiverId: String,
    val status: String = "started",
    @SerializedName("user_id") val userId: String? = null
)

data class CallResponse(
    val id: String,
    @SerializedName("caller_id") val callerId: String? = null,
    @SerializedName("receiver_id") val receiverId: String? = null,
    val status: String? = null,
    @SerializedName("user_id") val userId: String? = null
)

// ── Analysis DTOs ──
data class AnalysisRequest(
    @SerializedName("call_id") val callId: String? = null,
    @SerializedName("deepfake_score") val deepfakeScore: Double = 0.15,
    @SerializedName("speaker_similarity") val speakerSimilarity: Double = 0.90,
    @SerializedName("prosody_score") val prosodyScore: Double = 0.12,
    @SerializedName("context_score") val contextScore: Double = 0.20
)

data class AnalysisResponse(
    @SerializedName("deepfake_score") val deepfakeScore: Double = 0.0,
    @SerializedName("speaker_score") val speakerScore: Double? = null,
    @SerializedName("speaker_similarity") val speakerSimilarity: Double? = null,
    @SerializedName("prosody_score") val prosodyScore: Double = 0.0,
    @SerializedName("context_score") val contextScore: Double = 0.0,
    @SerializedName("risk_score") val riskScore: Double = 0.0,
    val severity: String = "LOW",
    val error: String? = null
)

data class RiskResponse(
    @SerializedName("call_id") val callId: String,
    @SerializedName("risk_score") val riskScore: Double,
    val severity: String
)

// ── Alert DTOs ──
data class AlertResponse(
    val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("call_id") val callId: String? = null,
    val severity: String,
    val message: String,
    val recommendation: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Contact DTOs ──
data class ContactResponse(
    val id: String,
    val name: String,
    val phone: String,
    val relation: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AddContactRequest(
    val name: String,
    val phone: String,
    val relation: String? = null
)

// ── Generic DTOs ──
data class HealthResponse(
    val status: String,
    val service: String? = null
)

data class MessageResponse(
    val message: String,
    val email: String? = null
)

// ── Spam Reporting DTOs ──
data class ReportNumberRequest(
    @SerializedName("reporter_user_id") val reporterUserId: String,
    @SerializedName("reported_phone") val reportedPhone: String,
    val reason: String? = null
)

data class ReportNumberResponse(
    val message: String,
    @SerializedName("report_count") val reportCount: Int = 0,
    @SerializedName("is_spam") val isSpam: Boolean = false
)

data class SpamCheckResponse(
    val phone: String,
    @SerializedName("report_count") val reportCount: Int = 0,
    @SerializedName("is_spam") val isSpam: Boolean = false
)

// ── Phone OTP DTOs ──
data class SendOtpRequest(
    val phone: String
)

data class SendOtpResponse(
    val message: String,
    val phone: String? = null,
    val reqId: String? = null,
    @SerializedName("dev_mode") val devMode: Boolean = false
)

data class VerifyOtpRequest(
    val phone: String,
    val otp: String
)

data class VerifyOtpResponse(
    val message: String,
    val token: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    val user: UserDto? = null
)
