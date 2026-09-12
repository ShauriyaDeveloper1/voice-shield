package com.sagar.voice_shield.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VoiceRegistrationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("voiceIdentityRegistered") val voiceIdentityRegistered: Boolean = false,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("voiceHash") val voiceHash: String? = null,
    @SerializedName("transactionHash") val transactionHash: String? = null,
    @SerializedName("blockchainNetwork") val blockchainNetwork: String? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    @SerializedName("status") val status: String? = null
)

data class VoiceStatusResponse(
    @SerializedName("registered") val registered: Boolean,
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("voiceHash") val voiceHash: String? = null,
    @SerializedName("transactionHash") val transactionHash: String? = null,
    @SerializedName("blockchainNetwork") val blockchainNetwork: String? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    @SerializedName("blockchainVerified") val blockchainVerified: Boolean = false,
    @SerializedName("modelName") val modelName: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class VoiceVerifyResponse(
    @SerializedName("risk_score") val riskScore: Double = 0.0,
    @SerializedName("severity") val severity: String = "LOW",
    @SerializedName("verdict") val verdict: String = "",
    @SerializedName("explanation") val explanation: String = "",
    @SerializedName("voice_match_score") val voiceMatchScore: Double = 0.0,
    @SerializedName("voice_match_percent") val voiceMatchPercent: Int = 0,
    @SerializedName("deepfake_probability") val deepfakeProbability: Double = 0.0,
    @SerializedName("deepfake_percent") val deepfakePercent: Int = 0,
    @SerializedName("voice_identity_active") val voiceIdentityActive: Boolean = true,
    @SerializedName("blockchain_identity_valid") val blockchainIdentityValid: Boolean = true
)
