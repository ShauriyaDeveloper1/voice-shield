import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { confirmVerifiedProfile, googleSignIn } from '../services/api';
import { supabase } from '../services/supabaseClient';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function parseJwt(token) {
  try {
    if (!token || typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    console.warn('JWT parsing failed:', e);
    return null;
  }
}

/**
 * Resolves user details from any available Supabase OAuth or verification source.
 */
async function resolveUserFromAuth() {
  const hash = window.location.hash ? window.location.hash.substring(1) : '';
  const hashParams = new URLSearchParams(hash);
  const searchParams = new URLSearchParams(window.location.search);

  // Check for any OAuth error returned by Supabase/Google
  const oauthError = hashParams.get('error_description') || 
                     hashParams.get('error') || 
                     searchParams.get('error_description') || 
                     searchParams.get('error');
  if (oauthError) {
    throw new Error(decodeURIComponent(oauthError.replace(/\+/g, ' ')));
  }

  const accessToken = hashParams.get('access_token') || searchParams.get('access_token');
  const refreshToken = hashParams.get('refresh_token') || searchParams.get('refresh_token');
  const providerToken = hashParams.get('provider_token') || searchParams.get('provider_token');
  const code = searchParams.get('code');

  let userId = '';
  let email = '';
  let name = '';
  let phone = '';
  let validToken = accessToken || '';

  // 1. If we have an access_token directly in hash or query, decode its JWT payload immediately
  if (accessToken) {
    const payload = parseJwt(accessToken);
    if (payload && payload.sub) {
      userId = payload.sub;
      email = payload.email || payload.user_metadata?.email || '';
      const meta = payload.user_metadata || {};
      name = meta.full_name || meta.name || email.split('@')[0] || '';
      phone = meta.phone || '';
    }

    // Also establish the Supabase client session in the background
    if (refreshToken) {
      try {
        await supabase.auth.setSession({
          access_token: accessToken,
          refresh_token: refreshToken,
        });
      } catch (e) {
        console.warn('supabase.auth.setSession warning:', e);
      }
    }
  }

  // 2. If PKCE code is present, exchange code for session
  if (!userId && code) {
    try {
      const { data, error } = await supabase.auth.exchangeCodeForSession(code);
      if (!error && data?.session?.user) {
        const u = data.session.user;
        const meta = u.user_metadata || {};
        userId = u.id;
        email = u.email || '';
        name = meta.full_name || meta.name || email.split('@')[0] || '';
        phone = meta.phone || '';
        validToken = data.session.access_token || '';
      }
    } catch (e) {
      console.warn('PKCE exchangeCodeForSession warning:', e);
    }
  }

  // 3. Check existing Supabase session if not already resolved
  if (!userId) {
    try {
      const { data } = await supabase.auth.getSession();
      if (data?.session?.user) {
        const u = data.session.user;
        const meta = u.user_metadata || {};
        userId = u.id;
        email = u.email || '';
        name = meta.full_name || meta.name || email.split('@')[0] || '';
        phone = meta.phone || '';
        validToken = data.session.access_token || '';
      }
    } catch (e) {
      console.warn('supabase.auth.getSession warning:', e);
    }
  }

  // 4. Check pending registration data (for email verification flow)
  const pendingDataStr = localStorage.getItem('voiceshield_pending_registration');
  const pendingData = pendingDataStr ? JSON.parse(pendingDataStr) : null;

  if (pendingData) {
    if (!userId && pendingData.id && UUID_REGEX.test(pendingData.id)) {
      userId = pendingData.id;
    }
    if (!email && pendingData.email) email = pendingData.email;
    if (!name && pendingData.name) name = pendingData.name;
    if (!phone && pendingData.phone) phone = pendingData.phone;
  }

  if (userId && (!UUID_REGEX.test(userId))) {
    console.warn('Resolved user ID is not a valid UUID:', userId);
    userId = '';
  }

  if (!userId) {
    return null;
  }

  return {
    userId,
    email: email || '',
    name: name || email.split('@')[0] || 'User',
    phone: phone || '',
    accessToken: validToken,
    providerToken: providerToken || null,
  };
}

function VerifySuccess({ onLoginSuccess }) {
  const navigate = useNavigate();
  const [statusText, setStatusText] = useState('Verifying your account...');

  useEffect(() => {
    let mounted = true;

    async function processVerification() {
      try {
        const authUser = await resolveUserFromAuth();

        if (!authUser) {
          if (!mounted) return;
          setStatusText('Session expired. Redirecting to login...');
          setTimeout(() => {
            if (mounted) navigate('/login', { replace: true });
          }, 1500);
          return;
        }

        const { userId, email, name, phone, accessToken, providerToken } = authUser;

        if (mounted) {
          setStatusText('Sign-in successful! Setting up your account...');
        }

        // 1. Directly upsert user profile into Supabase public.profiles table
        try {
          await supabase.from('profiles').upsert({
            id: userId,
            name: name,
            email: email,
            phone: phone || '',
            role: 'user',
          }, { onConflict: 'id' });
        } catch (dbErr) {
          console.warn('Direct Supabase profile upsert notice:', dbErr);
        }

        // 2. Notify backend API
        try {
          if (accessToken) {
            await googleSignIn(accessToken, providerToken);
          } else {
            await confirmVerifiedProfile(userId, email, name, phone);
          }
        } catch (apiErr) {
          console.warn('Backend API notification notice:', apiErr);
          try {
            await confirmVerifiedProfile(userId, email, name, phone);
          } catch (e) {
            // Ignore if backend is asleep
          }
        }

        // 3. Create active session in localStorage
        const userData = {
          id: userId,
          name: name,
          email: email,
          phone: phone || '',
          verified: true,
        };

        localStorage.setItem('voiceshield_user', JSON.stringify(userData));
        localStorage.removeItem('voiceshield_pending_registration');

        // Trigger phone prompt if phone number is not set yet
        if (!phone || phone.trim() === '') {
          localStorage.setItem('voiceshield_prompt_phone', 'true');
        }

        if (onLoginSuccess) {
          onLoginSuccess(userData);
        }

        if (mounted) {
          setStatusText('Success! Redirecting to dashboard...');
          setTimeout(() => {
            if (mounted) navigate('/dashboard', { replace: true });
          }, 1000);
        }

      } catch (err) {
        console.error('Verification error:', err);
        if (mounted) {
          setStatusText(err.message || 'Something went wrong. Redirecting...');
          setTimeout(() => {
            if (mounted) navigate('/login', { replace: true });
          }, 2000);
        }
      }
    }

    processVerification();

    return () => {
      mounted = false;
    };
  }, [navigate, onLoginSuccess]);

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        backgroundColor: '#ffffff',
        color: '#111827',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        boxSizing: 'border-box',
        zIndex: 999999,
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      }}
    >
      <h1
        style={{
          fontSize: '22px',
          fontWeight: '500',
          color: '#111827',
          textAlign: 'center',
          maxWidth: '680px',
          lineHeight: '1.6',
          letterSpacing: '-0.01em',
          margin: 0,
        }}
      >
        {statusText}
      </h1>
    </div>
  );
}

export default VerifySuccess;
