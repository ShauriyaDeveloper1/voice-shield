package com.sagar.voice_shield.ml

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Risk Engine matching the backend's weighted scoring model.
 * Computes a 0-100 risk score from multiple signal dimensions.
 */
class RiskEngine {

    data class RiskSignals(
        val deepfakeScore: Double = 0.0,
        val speakerSimilarity: Double = 1.0,
        val prosodyScore: Double = 0.0,
        val contextScore: Double = 0.0
    )

    data class RiskResult(
        val score: Double,
        val severity: String,
        val explanations: List<String>
    )

    fun calculateRisk(signals: RiskSignals): RiskResult {
        val deepfake = bounded(signals.deepfakeScore)
        val speakerMismatch = 1.0 - bounded(signals.speakerSimilarity)
        val prosody = bounded(signals.prosodyScore)
        val context = bounded(signals.contextScore)

        val score = (
            0.40 * deepfake +
            0.25 * speakerMismatch +
            0.15 * prosody +
            0.20 * context
        ) * 100.0

        val roundedScore = round(score * 100) / 100
        val severity = severity(roundedScore)

        val explanations = mutableListOf<String>()
        if (deepfake > 0.5) explanations.add("Possible synthetic voice detected (${(deepfake * 100).toInt()}%)")
        if (speakerMismatch > 0.5) explanations.add("Low speaker similarity (${((1 - speakerMismatch) * 100).toInt()}% match)")
        if (prosody > 0.5) explanations.add("Unusual speech characteristics detected")
        if (context > 0.5) explanations.add("Suspicious conversation patterns identified")
        if (explanations.isEmpty()) explanations.add("No significant risk signals detected")

        return RiskResult(roundedScore, severity, explanations)
    }

    fun severity(score: Double): String {
        return when {
            score < 34 -> "LOW"
            score < 67 -> "MEDIUM"
            else -> "HIGH"
        }
    }

    private fun bounded(value: Double): Double {
        return max(0.0, min(1.0, value))
    }
}
