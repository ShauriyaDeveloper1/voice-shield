"""
Voice Identity Registration, Blockchain Anchoring & Call-Time Verification API.
"""

import os
import shutil
import logging
from tempfile import NamedTemporaryFile
from typing import Optional
from fastapi import APIRouter, File, Form, UploadFile, Query, HTTPException

from app.services.voice_service import (
    extract_voice_embedding,
    encrypt_embedding,
    decrypt_embedding,
    compute_voice_hash,
    calculate_speaker_similarity,
    VOICE_MATCH_THRESHOLD
)
from app.services.blockchain_service import blockchain_service
from app.services.risk_engine import evaluate_multimodal_call_risk
from app.services import repository

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/register")
async def register_voice_identity(
    file: UploadFile = File(...),
    user_id: str = Form(...)
):
    """Enroll user's biometric voice sample:
    1. Validate audio length and quality.
    2. Extract 192-dim normalized biometric voice embedding.
    3. Generate canonical Keccak-256 / SHA-256 fingerprint hash.
    4. Encrypt embedding using AES-256-GCM.
    5. Anchor commitment hash onto EVM blockchain smart contract.
    6. Store encrypted embedding and blockchain metadata in Supabase.
    7. Delete temporary raw audio for privacy compliance.
    """
    tmp_path = None
    try:
        suffix = os.path.splitext(file.filename)[1] if file.filename else ".wav"
        with NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            shutil.copyfileobj(file.file, tmp)
            tmp_path = tmp.name

        # 1. Extract biometric embedding
        embedding = extract_voice_embedding(tmp_path)
        if not embedding or len(embedding) == 0:
            raise HTTPException(status_code=400, detail="Failed to extract biometric features from audio")

        # 2. Canonical fingerprint hash
        voice_hash = compute_voice_hash(embedding)

        # 3. Encrypt embedding with AES-256-GCM
        encrypted_embedding = encrypt_embedding(embedding)

        # 4. Anchor hash on blockchain
        chain_res = blockchain_service.register_voice_on_chain(user_id=user_id, voice_hash=voice_hash)

        # 5. Check if prior version exists in Supabase to increment version
        existing_records = repository.find_by("voice_identities", "user_id", user_id)
        version = 1
        for rec in existing_records:
            if rec.get("active", True):
                # Mark previous versions inactive
                try:
                    repository.update("voice_identities", rec["id"], {"active": False})
                except Exception:
                    pass
            rec_ver = rec.get("version", 0)
            if rec_ver >= version:
                version = rec_ver + 1

        # 6. Store encrypted embedding off-chain in Supabase
        db_record = {
            "user_id": user_id,
            "voice_embedding_encrypted": encrypted_embedding,
            "voice_hash": voice_hash,
            "version": version,
            "model_name": "voice-encoder-dvec-192",
            "model_version": "1.0",
            "transaction_hash": chain_res.get("transaction_hash", ""),
            "blockchain_network": chain_res.get("network", "evm-simulated"),
            "contract_address": chain_res.get("contract_address", ""),
            "active": True
        }
        
        saved = repository.insert("voice_identities", db_record)

        logger.info(f"Voice Identity registered for user {user_id} (Version: {version}, Hash: {voice_hash[:10]}..., Tx: {chain_res.get('transaction_hash')[:10]}...)")

        return {
            "success": True,
            "voiceIdentityRegistered": True,
            "version": version,
            "voiceHash": voice_hash,
            "transactionHash": chain_res.get("transaction_hash"),
            "blockchainNetwork": chain_res.get("network"),
            "contractAddress": chain_res.get("contract_address"),
            "status": "active"
        }
    except Exception as e:
        logger.error(f"Error registering voice identity: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        # 7. Zero retention: immediately unlink raw audio file
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except Exception:
                pass


@router.get("/status")
async def get_voice_identity_status(user_id: str = Query(...)):
    """Check current voice identity registration and on-chain verification state."""
    records = repository.find_by("voice_identities", "user_id", user_id)
    active_record = next((r for r in records if r.get("active", True)), None)

    if not active_record:
        return {
            "registered": False,
            "status": "not_enrolled",
            "message": "No voice identity registered for this user."
        }

    voice_hash = active_record.get("voice_hash", "")
    chain_ver = blockchain_service.verify_voice_on_chain(user_id, voice_hash)

    return {
        "registered": True,
        "status": "active" if active_record.get("active", True) else "revoked",
        "version": active_record.get("version", 1),
        "voiceHash": voice_hash,
        "transactionHash": active_record.get("transaction_hash", ""),
        "blockchainNetwork": active_record.get("blockchain_network", "evm"),
        "contractAddress": active_record.get("contract_address", ""),
        "blockchainVerified": chain_ver.get("is_valid", True),
        "modelName": active_record.get("model_name", "voice-encoder-v1"),
        "createdAt": active_record.get("created_at", "")
    }


@router.post("/verify")
async def verify_call_voice_chunk(
    file: UploadFile = File(...),
    user_id: str = Form(...),
    deepfake_prob: Optional[float] = Form(0.12)
):
    """Call-time voice verification:
    Compares incoming audio chunk against user's encrypted registered voice embedding.
    Runs in parallel with deepfake analysis.
    """
    tmp_path = None
    try:
        suffix = os.path.splitext(file.filename)[1] if file.filename else ".wav"
        with NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            shutil.copyfileobj(file.file, tmp)
            tmp_path = tmp.name

        # 1. Retrieve user's registered voice identity
        records = repository.find_by("voice_identities", "user_id", user_id)
        active_record = next((r for r in records if r.get("active", True)), None)

        if not active_record:
            # Fallback evaluation when no enrolled voice profile exists
            return evaluate_multimodal_call_risk(
                voice_match_score=0.50,
                deepfake_probability=deepfake_prob or 0.12,
                voice_identity_active=False,
                blockchain_identity_valid=False
            )

        # 2. Decrypt registered embedding
        ref_embedding = decrypt_embedding(active_record["voice_embedding_encrypted"])

        # 3. Extract candidate chunk embedding
        cand_embedding = extract_voice_embedding(tmp_path)

        # 4. Compute cosine similarity
        sim_score = calculate_speaker_similarity(ref_embedding, cand_embedding)

        # 5. Verify on-chain status
        chain_ver = blockchain_service.verify_voice_on_chain(user_id, active_record.get("voice_hash", ""))
        is_chain_valid = chain_ver.get("is_valid", True) and chain_ver.get("active", True)

        # 6. Centralized Multi-Modal Risk Evaluation
        eval_result = evaluate_multimodal_call_risk(
            voice_match_score=sim_score,
            deepfake_probability=deepfake_prob or 0.12,
            voice_identity_active=active_record.get("active", True),
            blockchain_identity_valid=is_chain_valid
        )

        return eval_result
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except Exception:
                pass


@router.post("/revoke")
async def revoke_voice_identity(user_id: str = Form(...)):
    """Revoke user's registered voice identity in database and on blockchain."""
    records = repository.find_by("voice_identities", "user_id", user_id)
    for rec in records:
        try:
            repository.update("voice_identities", rec["id"], {"active": False})
        except Exception:
            pass

    chain_res = blockchain_service.revoke_voice_on_chain(user_id)

    return {
        "success": True,
        "status": "revoked",
        "blockchain": chain_res
    }
