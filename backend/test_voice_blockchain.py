"""
Scratch unit test for Voice Embedding, Cryptography, Blockchain & Risk Engine.
"""
import sys
import os
from pathlib import Path
import numpy as np

backend_path = Path(__file__).resolve().parent.parent / "backend"
sys.path.insert(0, str(backend_path))

from app.services.voice_service import (
    encrypt_embedding,
    decrypt_embedding,
    compute_voice_hash,
    calculate_speaker_similarity
)
from app.services.blockchain_service import blockchain_service, get_user_ref_id
from app.services.risk_engine import evaluate_multimodal_call_risk

def run_tests():
    print("--- 1. Testing Voice Embedding Encryption & Decryption ---")
    np.random.seed(101)
    original_vec = [round(float(x), 6) for x in (np.random.randn(192) / 10.0).tolist()]
    
    enc = encrypt_embedding(original_vec)
    dec = decrypt_embedding(enc)
    
    assert len(dec) == 192, f"Decrypted length mismatch: {len(dec)}"
    assert np.allclose(original_vec, dec, atol=1e-5), "Decrypted embedding values mismatch!"
    print("PASS: AES-256-GCM Round-trip integrity verified.")

    print("\n--- 2. Testing Deterministic Blockchain Hashing ---")
    h1 = compute_voice_hash(original_vec)
    h2 = compute_voice_hash(original_vec)
    assert h1 == h2, f"Hash not deterministic! {h1} vs {h2}"
    assert h1.startswith("0x"), f"Hash does not start with 0x: {h1}"
    print(f"PASS: Deterministic hash: {h1}")

    print("\n--- 3. Testing Blockchain Service Registration & Verification ---")
    user_id = "test-user-uuid-12345"
    reg = blockchain_service.register_voice_on_chain(user_id, h1)
    assert reg["success"] is True
    print(f"PASS: Registered on blockchain! TxHash: {reg['transaction_hash']}")

    ver = blockchain_service.verify_voice_on_chain(user_id, h1)
    assert ver["is_valid"] is True, f"Verification failed: {ver}"
    print(f"PASS: On-chain verification valid: {ver['is_valid']}, version: {ver['version']}")

    # Mismatched hash verification
    fake_hash = "0x" + "0" * 64
    ver_fake = blockchain_service.verify_voice_on_chain(user_id, fake_hash)
    assert ver_fake["is_valid"] is False
    print("PASS: Tampered/mismatched hash rejected on-chain as expected.")

    print("\n--- 4. Testing Risk Engine Matrix ---")
    # Case A: Genuine Voice (High Match, Low Deepfake)
    case_a = evaluate_multimodal_call_risk(voice_match_score=0.95, deepfake_probability=0.08)
    assert case_a["severity"] == "LOW", f"Expected LOW, got {case_a['severity']}"
    print(f"PASS: Genuine Voice -> {case_a['verdict']} (Risk: {case_a['risk_score']})")

    # Case B: AI Voice Clone (High Match, HIGH Deepfake) -> Must detect voice clone!
    case_b = evaluate_multimodal_call_risk(voice_match_score=0.94, deepfake_probability=0.88)
    assert case_b["severity"] == "HIGH", f"Expected HIGH, got {case_b['severity']}"
    assert "Voice Clone" in case_b["verdict"], f"Expected Voice Clone alert, got {case_b['verdict']}"
    print(f"PASS: AI Voice Clone -> {case_b['verdict']} (Risk: {case_b['risk_score']})")

    # Case C: Identity Mismatch (Low Match, Low Deepfake)
    case_c = evaluate_multimodal_call_risk(voice_match_score=0.35, deepfake_probability=0.15)
    assert "IDENTITY MISMATCH" in case_c["verdict"]
    print(f"PASS: Identity Mismatch -> {case_c['verdict']} (Risk: {case_c['risk_score']})")

    print("\n=== ALL TESTS PASSED! ===")

if __name__ == "__main__":
    run_tests()
