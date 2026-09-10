package com.sagar.voice_shield.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── HuggingFace Audio Analysis Response ──
data class HfAudioAnalysisResponse(
    val status: String = "",
    val label: String = "",
    @SerializedName("is_deepfake") val isDeepfake: Boolean = false,
    @SerializedName("deepfake_score") val deepfakeScore: Double = 0.0,
    @SerializedName("bonafide_score") val bonafideScore: Double = 0.0,
    @SerializedName("speaker_similarity") val speakerSimilarity: Double = 0.0,
    @SerializedName("prosody_score") val prosodyScore: Double = 0.0,
    @SerializedName("context_score") val contextScore: Double = 0.0,
    @SerializedName("risk_score") val riskScore: Double = 0.0,
    val severity: String = "LOW",
    val error: String? = null
)

// ── HuggingFace Text Analysis Request ──
data class HfTextAnalysisRequest(
    val text: String
)

// ── HuggingFace Text Analysis Response ──
data class HfTextAnalysisResponse(
    val status: String = "",
    val prediction: String = "",
    val confidence: Double = 0.0,
    @SerializedName("context_score") val contextScore: Double = 0.0,
    val error: String? = null
)

// ── HuggingFace Health Response ──
data class HfHealthResponse(
    val status: String = ""
)
