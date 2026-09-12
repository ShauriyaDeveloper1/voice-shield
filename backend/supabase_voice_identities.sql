-- VoiceShield Voice Identities Table Schema
-- Stores encrypted biometric voice embeddings and on-chain blockchain commitment hashes

CREATE TABLE IF NOT EXISTS voice_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    voice_embedding_encrypted text NOT NULL,
    voice_hash text NOT NULL,
    version int NOT NULL DEFAULT 1,
    model_name text NOT NULL DEFAULT 'voice-encoder-v1',
    model_version text NOT NULL DEFAULT '1.0',
    transaction_hash text,
    blockchain_network text DEFAULT 'evm-local',
    contract_address text,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_voice_identities_user_id ON voice_identities(user_id);
CREATE INDEX IF NOT EXISTS idx_voice_identities_voice_hash ON voice_identities(voice_hash);
