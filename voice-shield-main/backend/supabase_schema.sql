-- VoiceShield Supabase Schema with User Sub-Table Partitioning
-- Master tables and individual user sub-tables for all tables except profiles

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Profiles Master Table
CREATE TABLE IF NOT EXISTS profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    email text NOT NULL UNIQUE,
    phone text,
    role text NOT NULL DEFAULT 'user',
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 2. Trusted Contacts Table (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS trusted_contacts (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name text NOT NULL,
    phone text NOT NULL,
    relation text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

-- 3. Speaker Profiles Table (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS speaker_profiles (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    embedding jsonb,
    language text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

-- 4. Calls Table (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS calls (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    caller_id uuid NOT NULL REFERENCES profiles(id),
    receiver_id uuid NOT NULL REFERENCES profiles(id),
    status text NOT NULL DEFAULT 'started',
    started_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz,
    final_risk_score numeric,
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

-- 5. Call Analysis Table (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS call_analysis (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    call_id uuid NOT NULL,
    timestamp timestamptz NOT NULL DEFAULT now(),
    deepfake_score numeric NOT NULL,
    speaker_score numeric NOT NULL,
    prosody_score numeric NOT NULL,
    context_score numeric NOT NULL,
    risk_score numeric NOT NULL,
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

-- 6. Alerts Table (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS alerts (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    call_id uuid NOT NULL,
    severity text NOT NULL,
    message text NOT NULL,
    recommendation text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

-- Default Partitions
CREATE TABLE IF NOT EXISTS trusted_contacts_default PARTITION OF trusted_contacts DEFAULT;
CREATE TABLE IF NOT EXISTS speaker_profiles_default PARTITION OF speaker_profiles DEFAULT;
CREATE TABLE IF NOT EXISTS calls_default PARTITION OF calls DEFAULT;
CREATE TABLE IF NOT EXISTS call_analysis_default PARTITION OF call_analysis DEFAULT;
CREATE TABLE IF NOT EXISTS alerts_default PARTITION OF alerts DEFAULT;

-- Trigger Function to create user sub-tables dynamically for every new profile
CREATE OR REPLACE FUNCTION create_user_subtables()
RETURNS TRIGGER AS $$
DECLARE
    user_suffix TEXT;
BEGIN
    user_suffix := replace(substr(NEW.id::text, 1, 8), '-', '_');
    
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF trusted_contacts FOR VALUES IN (%L)', 'trusted_contacts_' || user_suffix, NEW.id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF speaker_profiles FOR VALUES IN (%L)', 'speaker_profiles_' || user_suffix, NEW.id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF calls FOR VALUES IN (%L)', 'calls_' || user_suffix, NEW.id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF call_analysis FOR VALUES IN (%L)', 'call_analysis_' || user_suffix, NEW.id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF alerts FOR VALUES IN (%L)', 'alerts_' || user_suffix, NEW.id);
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_create_user_subtables ON profiles;
CREATE TRIGGER trg_create_user_subtables
AFTER INSERT ON profiles
FOR EACH ROW
EXECUTE FUNCTION create_user_subtables();

-- Row Level Security (RLS) configuration for prototype
ALTER TABLE profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE trusted_contacts DISABLE ROW LEVEL SECURITY;
ALTER TABLE speaker_profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE calls DISABLE ROW LEVEL SECURITY;
ALTER TABLE call_analysis DISABLE ROW LEVEL SECURITY;
ALTER TABLE alerts DISABLE ROW LEVEL SECURITY;