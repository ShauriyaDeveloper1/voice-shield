"""
Centralized Risk Engine for VoiceShield.
Evaluates multi-modal inputs combining:
1. Call-time Voice Identity Match Score (Cosine similarity against enrolled biometric embedding)
2. Continuous AI Deepfake Detection Probability (AASIST ZeroGPU & acoustic models)
3. Blockchain Identity Status (Tamper-resistant commitment validity & active flag)
"""

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


def evaluate_multimodal_call_risk(
    voice_match_score: float,
    deepfake_probability: float,
    voice_identity_active: bool = True,
    blockchain_identity_valid: bool = True
) -> dict:
    """Centralized multi-factor evaluation layer.
    
    Critical Rule: If voice verification matches (e.g. 95%) but deepfake probability
    is high (e.g. 85%), it flags HIGH RISK (Possible Voice Clone).
    """
    v_match = _bounded(voice_match_score)
    df_prob = _bounded(deepfake_probability)
    
    # 1. Voice Clone / AI Impersonation Attack
    if df_prob >= 0.70:
        if v_match >= 0.70:
            severity = "HIGH"
            verdict = "HIGH RISK — Possible Voice Clone / AI Impersonation"
            explanation = "Caller's biometric acoustics match enrolled profile, but acoustic artifacts indicate synthetic AI voice generation."
            risk_score = round(85.0 + (df_prob * 15.0), 1)
        else:
            severity = "HIGH"
            verdict = "HIGH RISK — Synthetic / Deepfake Audio Detected"
            explanation = "Strong deepfake voice artifacts detected. Caller voice does not match genuine biometric baseline."
            risk_score = round(80.0 + (df_prob * 20.0), 1)
            
    # 2. Speaker Identity Mismatch (Impostor / Wrong Speaker)
    elif v_match < 0.50:
        if df_prob >= 0.45:
            severity = "HIGH"
            verdict = "HIGH RISK — Unrecognized Speaker & Anomaly"
            explanation = "Voice characteristics differ significantly from enrolled identity, combined with acoustic speech anomalies."
            risk_score = round(70.0 + (df_prob * 20.0), 1)
        else:
            severity = "MEDIUM"
            verdict = "IDENTITY MISMATCH — Unrecognized Speaker"
            explanation = "Speaker voice does not match enrolled Voice Identity baseline. Verify caller identity independently."
            risk_score = round(45.0 + ((1.0 - v_match) * 20.0), 1)
            
    # 3. Biometric Anomaly / Suspicious Audio
    elif df_prob >= 0.40 or not voice_identity_active or not blockchain_identity_valid:
        severity = "MEDIUM"
        if not blockchain_identity_valid or not voice_identity_active:
            verdict = "SUSPICIOUS — Blockchain Identity Commitment Revoked/Invalid"
            explanation = "On-chain voice identity commitment is inactive or revoked."
            risk_score = 65.0
        else:
            verdict = "SUSPICIOUS — Biometric Acoustic Anomaly"
            explanation = "Moderate probability of acoustic manipulation or poor transmission quality."
            risk_score = round(40.0 + (df_prob * 25.0), 1)
            
    # 4. Genuine, Verified Safe Call
    else:
        severity = "LOW"
        verdict = "VERIFIED SAFE — Genuine Biometric Voice Match"
        explanation = "Voice acoustics match enrolled biometric profile with verified on-chain commitment and zero deepfake indicators."
        risk_score = round(df_prob * 25.0, 1)

    return {
        "risk_score": min(100.0, max(0.0, risk_score)),
        "severity": severity,
        "verdict": verdict,
        "explanation": explanation,
        "voice_match_score": round(v_match, 2),
        "voice_match_percent": int(v_match * 100),
        "deepfake_probability": round(df_prob, 2),
        "deepfake_percent": int(df_prob * 100),
        "voice_identity_active": voice_identity_active,
        "blockchain_identity_valid": blockchain_identity_valid
    }


def _bounded(value: object) -> float:
    try:
        return max(0.0, min(1.0, float(value)))
    except (ValueError, TypeError):
        return 0.0
