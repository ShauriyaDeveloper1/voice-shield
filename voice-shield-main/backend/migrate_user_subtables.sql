-- ====================================================================
-- VoiceShield: Migration Script for User Sub-Tables Partitioning
-- ====================================================================
-- This script segregates all tables (except profiles) into user-based 
-- sub-tables under each parent table.
-- Run this in your Supabase Project's SQL Editor (SQL Editor -> New Query -> Run)
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Step 1: Backup existing trusted_contacts safely
CREATE TEMP TABLE IF NOT EXISTS temp_backup_trusted_contacts AS 
SELECT * FROM trusted_contacts;

-- Step 2: Drop old unpartitioned tables (if not already partitioned)
DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS call_analysis CASCADE;
DROP TABLE IF EXISTS calls CASCADE;
DROP TABLE IF EXISTS speaker_profiles CASCADE;
DROP TABLE IF EXISTS trusted_contacts CASCADE;

-- Step 3: Ensure profiles master table exists
CREATE TABLE IF NOT EXISTS profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    email text NOT NULL UNIQUE,
    phone text,
    role text NOT NULL DEFAULT 'user',
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Step 4: Create Partitioned Parent Tables (Partitioned by user_id)
CREATE TABLE IF NOT EXISTS trusted_contacts (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name text NOT NULL,
    phone text NOT NULL,
    relation text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

CREATE TABLE IF NOT EXISTS speaker_profiles (
    id uuid DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    embedding jsonb,
    language text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, user_id)
) PARTITION BY LIST (user_id);

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

-- Step 5: Create DEFAULT partition fallbacks
CREATE TABLE IF NOT EXISTS trusted_contacts_default PARTITION OF trusted_contacts DEFAULT;
CREATE TABLE IF NOT EXISTS speaker_profiles_default PARTITION OF speaker_profiles DEFAULT;
CREATE TABLE IF NOT EXISTS calls_default PARTITION OF calls DEFAULT;
CREATE TABLE IF NOT EXISTS call_analysis_default PARTITION OF call_analysis DEFAULT;
CREATE TABLE IF NOT EXISTS alerts_default PARTITION OF alerts DEFAULT;

-- Step 6: Dynamically create individual sub-tables for all existing users
DO $$
DECLARE
    rec RECORD;
    user_suffix TEXT;
BEGIN
    FOR rec IN SELECT id, name FROM profiles LOOP
        user_suffix := replace(substr(rec.id::text, 1, 8), '-', '_');
        
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF trusted_contacts FOR VALUES IN (%L)', 'trusted_contacts_' || user_suffix, rec.id);
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF speaker_profiles FOR VALUES IN (%L)', 'speaker_profiles_' || user_suffix, rec.id);
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF calls FOR VALUES IN (%L)', 'calls_' || user_suffix, rec.id);
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF call_analysis FOR VALUES IN (%L)', 'call_analysis_' || user_suffix, rec.id);
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF alerts FOR VALUES IN (%L)', 'alerts_' || user_suffix, rec.id);
    END LOOP;
END $$;

-- Step 7: Restore backed up trusted_contacts (PostgreSQL automatically routes into user sub-tables!)
INSERT INTO trusted_contacts (id, user_id, name, phone, relation, created_at)
SELECT id, user_id, name, phone, relation, created_at FROM temp_backup_trusted_contacts
ON CONFLICT (id, user_id) DO NOTHING;

DROP TABLE IF EXISTS temp_backup_trusted_contacts;

-- Step 8: Trigger function to automatically create user sub-tables whenever a new profile registers
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

-- Step 9: Disable Row Level Security on all tables for seamless API access
ALTER TABLE profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE trusted_contacts DISABLE ROW LEVEL SECURITY;
ALTER TABLE speaker_profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE calls DISABLE ROW LEVEL SECURITY;
ALTER TABLE call_analysis DISABLE ROW LEVEL SECURITY;
ALTER TABLE alerts DISABLE ROW LEVEL SECURITY;
