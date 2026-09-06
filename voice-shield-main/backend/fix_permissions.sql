-- VoiceShield: Complete Database Permissions & New User Automation Script
-- Run this in Supabase SQL Editor (Dashboard -> SQL Editor -> New Query -> Run)

-- 1. Grant usage and table privileges to anon and authenticated roles
GRANT USAGE, CREATE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;

-- 2. Fix the sub-table partition creation trigger to run with SECURITY DEFINER
-- This ensures partition creation never fails due to role permissions
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
EXCEPTION WHEN OTHERS THEN
    -- In case partition already exists or table is default partitioned, do not block profile creation
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_create_user_subtables ON profiles;
CREATE TRIGGER trg_create_user_subtables
AFTER INSERT ON profiles
FOR EACH ROW
EXECUTE FUNCTION create_user_subtables();

-- 3. Automatic Auth User to Public Profiles Synchronization
-- Automatically inserts/updates public.profiles whenever any user signs up (Google, Email, etc.)
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.profiles (id, name, email, phone, role)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', split_part(NEW.email, '@', 1)),
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),
    'user'
  )
  ON CONFLICT (id) DO UPDATE
  SET
    name = EXCLUDED.name,
    email = EXCLUDED.email;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT OR UPDATE ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- 4. Disable RLS on all tables to allow the prototype seamless operation
ALTER TABLE IF EXISTS profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS trusted_contacts DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS speaker_profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS calls DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS call_analysis DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS alerts DISABLE ROW LEVEL SECURITY;
