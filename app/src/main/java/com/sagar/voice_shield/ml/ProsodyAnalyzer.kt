package com.sagar.voice_shield.ml

import kotlin.math.*

/**
 * On-device prosody analysis using digital signal processing.
 * Extracts pitch (F0), jitter, shimmer, and speaking rate from raw audio.
 */
class ProsodyAnalyzer {

    data class ProsodyFeatures(
        val meanPitch: Double,
        val pitchVariance: Double,
        val jitter: Double,
        val shimmer: Double,
        val speakingRate: Double,
        val unnaturalnessScore: Double
    )

    fun analyze(audioData: ShortArray, sampleRate: Int = 16000): ProsodyFeatures {
        if (audioData.isEmpty()) return emptyFeatures()
        val floatData = audioData.map { it.toDouble() / Short.MAX_VALUE }.toDoubleArray()
        val pitchValues = extractPitch(floatData, sampleRate)
        val meanPitch = if (pitchValues.isNotEmpty()) pitchValues.average() else 0.0
        val pitchVariance = if (pitchValues.size > 1) pitchValues.map { (it - meanPitch).pow(2) }.average() else 0.0
        val jitter = computeJitter(pitchValues)
        val shimmer = computeShimmer(floatData, sampleRate)
        val speakingRate = estimateSpeakingRate(floatData, sampleRate)
        val unnaturalnessScore = computeUnnaturalness(meanPitch, pitchVariance, jitter, shimmer, speakingRate)
        return ProsodyFeatures(meanPitch, pitchVariance, jitter, shimmer, speakingRate, unnaturalnessScore)
    }

    private fun extractPitch(data: DoubleArray, sampleRate: Int): List<Double> {
        val pitchValues = mutableListOf<Double>()
        val frameSize = sampleRate / 10
        val hopSize = frameSize / 2
        val minLag = sampleRate / 500
        val maxLag = sampleRate / 60
        var offset = 0
        while (offset + frameSize <= data.size) {
            val frame = data.sliceArray(offset until offset + frameSize)
            val pitch = autocorrelationPitch(frame, sampleRate, minLag, maxLag)
            if (pitch > 0) pitchValues.add(pitch)
            offset += hopSize
        }
        return pitchValues
    }

    private fun autocorrelationPitch(frame: DoubleArray, sampleRate: Int, minLag: Int, maxLag: Int): Double {
        val n = frame.size
        if (maxLag >= n) return 0.0
        var bestLag = 0
        var bestCorr = -1.0
        for (lag in minLag..min(maxLag, n - 1)) {
            var correlation = 0.0; var norm1 = 0.0; var norm2 = 0.0
            for (i in 0 until n - lag) {
                correlation += frame[i] * frame[i + lag]
                norm1 += frame[i] * frame[i]
                norm2 += frame[i + lag] * frame[i + lag]
            }
            val normalizedCorr = if (norm1 > 0 && norm2 > 0) correlation / sqrt(norm1 * norm2) else 0.0
            if (normalizedCorr > bestCorr) { bestCorr = normalizedCorr; bestLag = lag }
        }
        return if (bestCorr > 0.3 && bestLag > 0) sampleRate.toDouble() / bestLag else 0.0
    }

    private fun computeJitter(pitchValues: List<Double>): Double {
        if (pitchValues.size < 2) return 0.0
        val periods = pitchValues.map { if (it > 0) 1.0 / it else 0.0 }
        val diffs = (1 until periods.size).map { abs(periods[it] - periods[it - 1]) }
        val meanPeriod = periods.average()
        return if (meanPeriod > 0) diffs.average() / meanPeriod else 0.0
    }

    private fun computeShimmer(data: DoubleArray, sampleRate: Int): Double {
        val frameSize = sampleRate / 20
        val amplitudes = mutableListOf<Double>()
        var offset = 0
        while (offset + frameSize <= data.size) {
            val rms = sqrt(data.sliceArray(offset until offset + frameSize).map { it * it }.average())
            if (rms > 0.01) amplitudes.add(rms)
            offset += frameSize
        }
        if (amplitudes.size < 2) return 0.0
        val diffs = (1 until amplitudes.size).map { abs(amplitudes[it] - amplitudes[it - 1]) }
        val meanAmp = amplitudes.average()
        return if (meanAmp > 0) diffs.average() / meanAmp else 0.0
    }

    private fun estimateSpeakingRate(data: DoubleArray, sampleRate: Int): Double {
        val frameSize = sampleRate / 50
        val energies = mutableListOf<Double>()
        var offset = 0
        while (offset + frameSize <= data.size) {
            energies.add(data.sliceArray(offset until offset + frameSize).map { it * it }.average())
            offset += frameSize
        }
        if (energies.isEmpty()) return 0.0
        val threshold = energies.average() * 1.5
        var peaks = 0; var wasAbove = false
        for (e in energies) {
            if (e > threshold && !wasAbove) { peaks++; wasAbove = true }
            else if (e < threshold) wasAbove = false
        }
        val durationSecs = data.size.toDouble() / sampleRate
        return if (durationSecs > 0) peaks / durationSecs else 0.0
    }

    private fun computeUnnaturalness(meanPitch: Double, pitchVariance: Double, jitter: Double, shimmer: Double, speakingRate: Double): Double {
        var score = 0.0
        if (pitchVariance < 100 && meanPitch > 0) score += 0.3
        if (jitter < 0.005 && jitter >= 0) score += 0.25
        if (shimmer < 0.02 && shimmer >= 0) score += 0.2
        if (speakingRate > 8 || (speakingRate > 0 && speakingRate < 1)) score += 0.15
        if (meanPitch > 0 && (meanPitch < 70 || meanPitch > 400)) score += 0.1
        return min(1.0, score)
    }

    private fun emptyFeatures() = ProsodyFeatures(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
}
