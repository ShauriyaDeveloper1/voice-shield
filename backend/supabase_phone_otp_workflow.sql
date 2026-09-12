-- VoiceShield: Supabase Workflow Implementation for Phone OTP & Google Auth Synchronization
-- Compatible with PostgreSQL List Partitioning and Sub-Tables

-- 1. Profiles Table Updates (Allow phone-first signup without mandatory email)
ALTER TABLE public.profiles ALTER COLUMN email DROP NOT NULL;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS phone_verified boolean DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS avatar_url text;

CREATE INDEX IF NOT EXISTS idx_profiles_phone ON public.profiles(phone);
CREATE INDEX IF NOT EXISTS idx_profiles_email ON public.profiles(email);

-- 2. Phone Verification / OTP Table (For MSG91 / Hackathon free trial workflow)
CREATE TABLE IF NOT EXISTS public.phone_verifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone text NOT NULL,
    otp_code text NOT NULL,
    attempts int DEFAULT 0,
    verified boolean DEFAULT false,
    expires_at timestamptz NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_phone_verifications_phone ON public.phone_verifications(phone);

-- 3. Dedicated User Sub-table Partitioning Function
CREATE OR REPLACE FUNCTION public.create_user_subtables_for_user(target_user_id uuid)
RETURNS void AS $$
DECLARE
    user_suffix TEXT;
BEGIN
    user_suffix := replace(substr(target_user_id::text, 1, 8), '-', '_');
    
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF trusted_contacts FOR VALUES IN (%L)', 'trusted_contacts_' || user_suffix, target_user_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF speaker_profiles FOR VALUES IN (%L)', 'speaker_profiles_' || user_suffix, target_user_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF calls FOR VALUES IN (%L)', 'calls_' || user_suffix, target_user_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF call_analysis FOR VALUES IN (%L)', 'call_analysis_' || user_suffix, target_user_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF alerts FOR VALUES IN (%L)', 'alerts_' || user_suffix, target_user_id);

    NOTIFY pgrst, 'reload schema';
EXCEPTION WHEN OTHERS THEN
    NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- 4. Automatic Auth User Synchronization Function
-- Syncs both Phone OTP and Google OAuth users from auth.users -> public.profiles
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger AS $$
DECLARE
  extracted_name TEXT;
  extracted_phone TEXT;
  extracted_email TEXT;
  is_phone_verified BOOLEAN;
BEGIN
  -- Extract name from metadata or fallback
  extracted_name := COALESCE(
    NEW.raw_user_meta_data->>'full_name',
    NEW.raw_user_meta_data->>'name',
    NULLIF(split_part(NEW.email, '@', 1), ''),
    CASE 
      WHEN NEW.phone IS NOT NULL AND length(NEW.phone) >= 4 THEN 'User ' || right(NEW.phone, 4)
      WHEN (NEW.raw_user_meta_data->>'phone') IS NOT NULL THEN 'User ' || right(NEW.raw_user_meta_data->>'phone', 4)
      ELSE 'VoiceShield User'
    END
  );

  -- Extract phone
  extracted_phone := COALESCE(
    NULLIF(NEW.phone, ''),
    NULLIF(NEW.raw_user_meta_data->>'phone', ''),
    ''
  );

  -- Extract email
  extracted_email := NULLIF(NEW.email, '');

  -- Determine if phone has been verified
  is_phone_verified := (
    NEW.phone_confirmed_at IS NOT NULL 
    OR (NEW.raw_user_meta_data->>'phone_verified')::boolean = TRUE
  );

  INSERT INTO public.profiles (id, name, email, phone, phone_verified, role, avatar_url)
  VALUES (
    NEW.id,
    extracted_name,
    extracted_email,
    extracted_phone,
    is_phone_verified,
    'user',
    COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
  )
  ON CONFLICT (id) DO UPDATE
  SET
    name = COALESCE(NULLIF(EXCLUDED.name, 'VoiceShield User'), profiles.name),
    email = COALESCE(EXCLUDED.email, profiles.email),
    phone = CASE 
      WHEN EXCLUDED.phone IS NOT NULL AND EXCLUDED.phone <> '' THEN EXCLUDED.phone 
      ELSE profiles.phone 
    END,
    phone_verified = CASE 
      WHEN EXCLUDED.phone_verified = TRUE THEN TRUE 
      ELSE profiles.phone_verified 
    END,
    avatar_url = COALESCE(NULLIF(EXCLUDED.avatar_url, ''), profiles.avatar_url);

  -- Ensure physical partition sub-tables are provisioned
  PERFORM public.create_user_subtables_for_user(NEW.id);

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Attach Trigger to auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT OR UPDATE ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- 6. Trigger on public.profiles
CREATE OR REPLACE FUNCTION public.create_user_subtables()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM public.create_user_subtables_for_user(NEW.id);
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_create_user_subtables ON public.profiles;
CREATE TRIGGER trg_create_user_subtables
AFTER INSERT ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.create_user_subtables();

-- 7. Master Phone User Registration Function (Direct auth.users + profiles + sub-tables)
CREATE OR REPLACE FUNCTION public.register_phone_user(phone_input text, name_input text)
RETURNS jsonb AS $$
DECLARE
    clean_digits TEXT;
    clean_phone TEXT;
    synthetic_email TEXT;
    target_user_id UUID;
    result_profile RECORD;
BEGIN
    clean_phone := trim(phone_input);
    clean_digits := regexp_replace(clean_phone, '[^0-9]', '', 'g');
    synthetic_email := 'phone_' || clean_digits || '@voiceshield.com';

    -- 1. Check if user already exists in public.profiles with this phone or email
    SELECT * INTO result_profile FROM public.profiles 
    WHERE phone = clean_phone OR email = synthetic_email
    LIMIT 1;

    IF result_profile.id IS NOT NULL THEN
        target_user_id := result_profile.id;

        UPDATE public.profiles 
        SET phone_verified = true,
            name = COALESCE(NULLIF(name_input, ''), result_profile.name)
        WHERE id = target_user_id;

        UPDATE auth.users
        SET phone = clean_phone,
            phone_confirmed_at = COALESCE(phone_confirmed_at, now()),
            updated_at = now()
        WHERE id = target_user_id;

        PERFORM public.create_user_subtables_for_user(target_user_id);

        SELECT * INTO result_profile FROM public.profiles WHERE id = target_user_id;
        RETURN to_jsonb(result_profile);
    END IF;

    -- 2. Check if user already exists in auth.users by email or phone
    SELECT id INTO target_user_id FROM auth.users 
    WHERE phone = clean_phone OR email = synthetic_email 
    LIMIT 1;

    IF target_user_id IS NULL THEN
        target_user_id := gen_random_uuid();

        INSERT INTO auth.users (
            id,
            instance_id,
            aud,
            role,
            email,
            phone,
            phone_confirmed_at,
            email_confirmed_at,
            raw_app_meta_data,
            raw_user_meta_data,
            created_at,
            updated_at
        ) VALUES (
            target_user_id,
            '00000000-0000-0000-0000-000000000000',
            'authenticated',
            'authenticated',
            synthetic_email,
            clean_phone,
            now(),
            now(),
            '{"provider": "phone", "providers": ["phone"]}'::jsonb,
            jsonb_build_object(
                'name', name_input,
                'full_name', name_input,
                'phone', clean_phone,
                'phone_verified', true
            ),
            now(),
            now()
        );

        INSERT INTO auth.identities (
            id,
            user_id,
            identity_data,
            provider,
            provider_id,
            last_sign_in_at,
            created_at,
            updated_at
        ) VALUES (
            target_user_id,
            target_user_id,
            jsonb_build_object('sub', target_user_id::text, 'phone', clean_phone),
            'phone',
            clean_phone,
            now(),
            now(),
            now()
        )
        ON CONFLICT (provider, provider_id) DO NOTHING;
    ELSE
        UPDATE auth.users 
        SET phone = clean_phone,
            phone_confirmed_at = COALESCE(phone_confirmed_at, now()),
            updated_at = now()
        WHERE id = target_user_id;
    END IF;

    -- 3. Upsert into public.profiles
    INSERT INTO public.profiles (
        id,
        name,
        email,
        phone,
        phone_verified,
        role,
        avatar_url,
        created_at
    ) VALUES (
        target_user_id,
        name_input,
        synthetic_email,
        clean_phone,
        true,
        'user',
        '',
        now()
    )
    ON CONFLICT (id) DO UPDATE
    SET
        name = COALESCE(NULLIF(EXCLUDED.name, ''), profiles.name),
        phone = EXCLUDED.phone,
        phone_verified = true,
        email = COALESCE(profiles.email, EXCLUDED.email);

    -- 4. Provision user partition sub-tables
    PERFORM public.create_user_subtables_for_user(target_user_id);

    -- 5. Return profile
    SELECT * INTO result_profile FROM public.profiles WHERE id = target_user_id;
    RETURN to_jsonb(result_profile);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. Grant Schema Privileges
ALTER TABLE IF EXISTS public.phone_verifications DISABLE ROW LEVEL SECURITY;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.register_phone_user(text, text) TO anon, authenticated, service_role;

