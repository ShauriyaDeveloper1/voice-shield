import { createClient } from '@supabase/supabase-js'

// Supabase client for frontend OAuth operations (Google Sign-In)
const supabaseUrl = 'https://qxengrvbkxxdxdrpzbng.supabase.co'
const supabaseAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4ZW5ncnZia3h4ZHhkcnB6Ym5nIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc2NTc3MjEsImV4cCI6MjEwMzIzMzcyMX0.rBCyKY8Y4hoQNkFiNohFEt9iJ6IHeOFzK012pUD68eM'

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: true,
  },
})

/**
 * Initiates Google OAuth sign-in via Supabase.
 * Redirects the user to Google's consent screen, then back to /verify-success.
 */
export async function signInWithGoogle() {
  const { data, error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: {
      redirectTo: `${window.location.origin}/verify-success`,
      queryParams: {
        access_type: 'offline',
        prompt: 'consent',
      },
    },
  })

  if (error) {
    throw new Error(error.message)
  }

  return data
}
