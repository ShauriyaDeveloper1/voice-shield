"""Deterministic first-pass risk scoring for call analysis."""


def calculate_risk_score(audio_features: dict) -> float:
    """Calculate a 0-100 score using the initial weighted model."""
    deepfake = _bounded(audio_features.get("deepfake_score", 0.0))
    speaker_similarity = _bounded(audio_features.get("speaker_similarity", 1.0))
    prosody = _bounded(audio_features.get("prosody_score", 0.0))
    context = _bounded(audio_features.get("context_score", 0.0))
    speaker_mismatch = 1.0 - speaker_similarity

    score = (
        0.40 * deepfake
        + 0.25 * speaker_mismatch
        + 0.15 * prosody
        + 0.20 * context
    ) * 100
    return round(score, 2)


def risk_severity(score: float) -> str:
    if score < 34:
        return "LOW"
    if score < 67:
        return "MEDIUM"
    return "HIGH"


def _bounded(value: object) -> float:
    return max(0.0, min(1.0, float(value)))
