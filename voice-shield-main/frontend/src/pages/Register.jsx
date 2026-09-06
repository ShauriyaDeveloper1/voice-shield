import { useState } from 'react';
import { Link } from 'react-router-dom';
import { registerUser, sendEmailVerification } from '../services/api';
import { signInWithGoogle } from '../services/supabaseClient';
import './Auth.css';

const GoogleIcon = () => (
  <svg viewBox="0 0 24 24" width="20" height="20">
    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
  </svg>
);

function Register() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [countryCode, setCountryCode] = useState('+91');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [verifyingEmail, setVerifyingEmail] = useState(false);
  const [emailVerificationSent, setEmailVerificationSent] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleVerifyEmail = async () => {
    if (!email || !email.includes('@')) {
      setError('Please enter a valid email address first.');
      return;
    }
    setError('');
    setSuccess('');
    setVerifyingEmail(true);

    try {
      const phone = phoneNumber ? `${countryCode} ${phoneNumber.trim()}` : '';
      // Store pending form inputs so they can be restored upon verification redirect
      localStorage.setItem('voiceshield_pending_registration', JSON.stringify({
        name,
        email,
        phone,
        password
      }));

      const redirectUrl = `${window.location.origin}/verify-success`;
      const res = await sendEmailVerification(email, name, phone, redirectUrl);
      setEmailVerificationSent(true);
      setSuccess(res.message || `Verification link sent to ${email}! Please check your email.`);
    } catch (err) {
      setError(err.message || 'Failed to send verification email.');
    } finally {
      setVerifyingEmail(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name || !email || !phoneNumber || !password) {
      setError('Please fill in all fields.');
      return;
    }
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const phone = `${countryCode} ${phoneNumber.trim()}`;
      await registerUser(name, email, phone, password);
      setSuccess('Account created successfully! You can now sign in.');
      setName('');
      setEmail('');
      setPhoneNumber('');
      setPassword('');
    } catch (err) {
      setError(err.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSignUp = async () => {
    setError('');
    setGoogleLoading(true);
    try {
      await signInWithGoogle();
      // The user will be redirected to Google's OAuth consent screen.
      // After authentication, they'll be redirected back to /verify-success
      // where App.jsx handles the callback and creates their profile.
    } catch (err) {
      setError(err.message || 'Google sign-up failed');
      setGoogleLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-logo">V</div>
          <span className="auth-brand-name">VoiceShield</span>
        </div>

        <div className="auth-header">
          <h2 className="auth-title">Create account</h2>
          <p className="auth-subtitle">Monitor calls and keep conversations safer.</p>
        </div>

        {error && <div className="auth-alert error">{error}</div>}
        {success && <div className="auth-alert success">{success}</div>}

        <button 
          type="button" 
          className="google-signin-btn" 
          onClick={handleGoogleSignUp}
          disabled={googleLoading}
          style={{ marginBottom: '0.5rem' }}
        >
          <GoogleIcon />
          {googleLoading ? 'Redirecting to Google...' : 'Continue with Google'}
        </button>

        <div className="auth-divider">or</div>

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label className="form-label">Full Name</label>
            <input 
              type="text" 
              className="form-input"
              placeholder="Your name" 
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <div className="email-verify-row">
              <input 
                type="email" 
                className="form-input email-input-with-btn"
                placeholder="you@example.com" 
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setEmailVerificationSent(false);
                }}
                disabled={loading || verifyingEmail}
              />
              <button 
                type="button" 
                className="verify-email-blue-btn"
                onClick={handleVerifyEmail}
                disabled={loading || verifyingEmail || !email.trim()}
              >
                {verifyingEmail ? 'Sending...' : emailVerificationSent ? 'Resend' : 'Verify'}
              </button>
            </div>
            {emailVerificationSent && (
              <p className="email-verify-hint-text">
                ✓ Verification email sent. Please check your inbox and click the link to verify.
              </p>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">Mobile Number</label>
            <div className="phone-input-container">
              <select 
                className="form-input country-code-select"
                value={countryCode}
                onChange={(e) => setCountryCode(e.target.value)}
                disabled={loading}
              >
                <option value="+91">🇮🇳 +91</option>
                <option value="+1">🇺🇸 +1</option>
                <option value="+44">🇬🇧 +44</option>
                <option value="+61">🇦🇺 +61</option>
                <option value="+49">🇩🇪 +49</option>
                <option value="+33">🇫🇷 +33</option>
                <option value="+971">🇦🇪 +971</option>
                <option value="+966">🇸🇦 +966</option>
                <option value="+65">🇸🇬 +65</option>
                <option value="+81">🇯🇵 +81</option>
                <option value="+86">🇨🇳 +86</option>
                <option value="+7">🇷🇺 +7</option>
              </select>
              <input 
                type="tel" 
                className="form-input phone-number-input"
                placeholder="99999 99999" 
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input 
              type="password" 
              className="form-input"
              placeholder="Choose a password" 
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <div className="auth-footer">
          Already registered? <Link to="/login" className="auth-link">Sign in</Link>
        </div>
      </div>
    </div>
  );
}

export default Register;
