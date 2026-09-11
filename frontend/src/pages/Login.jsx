import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { loginUser, sendPhoneOtp, loginWithPhoneOtp } from '../services/api';
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

function Login({ onLoginSuccess }) {
  const [authMethod, setAuthMethod] = useState('phone'); // 'phone' | 'password'
  
  // Password login inputs
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  // Phone OTP login inputs
  const [countryCode, setCountryCode] = useState('+91');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [sendingOtp, setSendingOtp] = useState(false);
  const [devOtpHint, setDevOtpHint] = useState('');

  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setInterval(() => setCountdown(prev => prev - 1), 1000);
    }
    return () => clearInterval(timer);
  }, [countdown]);

  const getFullPhone = () => {
    return `${countryCode}${phoneNumber.replace(/\D/g, '')}`;
  };

  const handleSendOtp = async () => {
    const rawDigits = phoneNumber.replace(/\D/g, '');
    if (!rawDigits || rawDigits.length < 7) {
      setError('Please enter a valid mobile number.');
      return;
    }
    setError('');
    setSuccess('');
    setDevOtpHint('');
    setSendingOtp(true);
    try {
      const fullPhone = getFullPhone();
      const res = await sendPhoneOtp(fullPhone);
      setOtpSent(true);
      setCountdown(30);
      setSuccess(`Verification code sent to ${fullPhone}`);
      if (res.debug_otp) {
        setDevOtpHint(`Dev Code: ${res.debug_otp}`);
      }
    } catch (err) {
      setError(err.message || 'Failed to send OTP code.');
    } finally {
      setSendingOtp(false);
    }
  };

  const handlePhoneSubmit = async (e) => {
    e.preventDefault();
    if (!phoneNumber.trim()) {
      setError('Please enter your mobile number.');
      return;
    }
    if (!otpSent) {
      setError('Please click "Get OTP" to receive a verification code.');
      return;
    }
    if (!otp.trim()) {
      setError('Please enter the OTP code.');
      return;
    }

    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const fullPhone = getFullPhone();
      const data = await loginWithPhoneOtp(fullPhone, otp.trim());
      if (data.user) {
        onLoginSuccess(data.user);
      } else {
        setError('Login failed: Invalid server response');
      }
    } catch (err) {
      setError(err.message || 'Invalid or expired OTP code.');
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    if (!username || !password) {
      setError('Please fill in all fields.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const data = await loginUser(username, password);
      if (data.user) {
        onLoginSuccess(data.user);
      } else {
        setError('Login failed: Invalid server response');
      }
    } catch (err) {
      setError(err.message || 'Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSignIn = async () => {
    setError('');
    setGoogleLoading(true);
    try {
      await signInWithGoogle();
    } catch (err) {
      setError(err.message || 'Google sign-in failed');
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
          <h2 className="auth-title">Welcome Back</h2>
          <p className="auth-subtitle">Sign in to access AI call security & threat intelligence.</p>
        </div>

        {error && <div className="auth-alert error">{error}</div>}
        {success && <div className="auth-alert success">{success}</div>}
        {devOtpHint && (
          <div className="auth-alert info" style={{ backgroundColor: 'rgba(0, 240, 194, 0.1)', color: '#00f0c2', border: '1px solid rgba(0, 240, 194, 0.3)' }}>
            🔑 {devOtpHint} (Mock Sandbox)
          </div>
        )}

        {/* Auth Method Selector */}
        <div style={{
          display: 'flex',
          background: 'rgba(255, 255, 255, 0.05)',
          padding: '4px',
          borderRadius: '8px',
          marginBottom: '1.25rem',
          border: '1px solid rgba(255, 255, 255, 0.08)'
        }}>
          <button
            type="button"
            onClick={() => { setAuthMethod('phone'); setError(''); setSuccess(''); }}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              fontWeight: authMethod === 'phone' ? 'bold' : 'normal',
              background: authMethod === 'phone' ? 'var(--color-primary, #6366f1)' : 'transparent',
              color: '#fff',
              fontSize: '0.86rem',
              transition: 'all 0.2s ease'
            }}
          >
            📱 Phone & OTP
          </button>
          <button
            type="button"
            onClick={() => { setAuthMethod('password'); setError(''); setSuccess(''); }}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              fontWeight: authMethod === 'password' ? 'bold' : 'normal',
              background: authMethod === 'password' ? 'var(--color-primary, #6366f1)' : 'transparent',
              color: '#fff',
              fontSize: '0.86rem',
              transition: 'all 0.2s ease'
            }}
          >
            🔒 Password
          </button>
        </div>

        {authMethod === 'phone' ? (
          <form onSubmit={handlePhoneSubmit} className="auth-form">
            <div className="form-group">
              <label className="form-label">Mobile Number</label>
              <div className="phone-input-container">
                <select 
                  className="form-input country-code-select"
                  value={countryCode}
                  onChange={(e) => setCountryCode(e.target.value)}
                  disabled={loading || otpSent}
                >
                  <option value="+91">🇮🇳 +91</option>
                  <option value="+1">🇺🇸 +1</option>
                  <option value="+44">🇬🇧 +44</option>
                  <option value="+61">🇦🇺 +61</option>
                  <option value="+971">🇦🇪 +971</option>
                  <option value="+65">🇸🇬 +65</option>
                </select>
                <input 
                  type="tel" 
                  className="form-input phone-number-input"
                  placeholder="98765 43210" 
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  disabled={loading || (otpSent && countdown > 0)}
                  required
                />
                <button 
                  type="button" 
                  className="verify-email-blue-btn"
                  onClick={handleSendOtp}
                  disabled={loading || sendingOtp || (otpSent && countdown > 0)}
                  style={{ whiteSpace: 'nowrap', minWidth: '95px' }}
                >
                  {sendingOtp ? 'Sending...' : otpSent ? (countdown > 0 ? `${countdown}s` : 'Resend') : 'Get OTP'}
                </button>
              </div>
            </div>

            {otpSent && (
              <div className="form-group" style={{ animation: 'fadeIn 0.3s ease-in' }}>
                <label className="form-label">Enter 6-Digit OTP</label>
                <input 
                  type="text" 
                  maxLength="6"
                  className="form-input"
                  placeholder="• • • • • •" 
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  disabled={loading}
                  autoFocus
                  style={{ letterSpacing: '6px', fontSize: '1.2rem', textAlign: 'center', fontWeight: 'bold' }}
                  required
                />
              </div>
            )}

            <button type="submit" className="auth-btn" disabled={loading || (!otpSent && !otp)}>
              {loading ? 'Signing in...' : otpSent ? 'Verify & Sign In' : 'Get OTP to Sign In'}
            </button>
          </form>
        ) : (
          <form onSubmit={handlePasswordSubmit} className="auth-form">
            <div className="form-group">
              <label className="form-label">Name or Email</label>
              <input 
                type="text" 
                className="form-input"
                placeholder="Enter your name or email" 
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={loading}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input 
                type="password" 
                className="form-input"
                placeholder="Your password" 
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
                required
              />
            </div>

            <button type="submit" className="auth-btn" disabled={loading}>
              {loading ? 'Signing in...' : 'Sign in with Password'}
            </button>
          </form>
        )}

        <div className="auth-divider">or continue with</div>

        <button 
          type="button" 
          className="google-signin-btn" 
          onClick={handleGoogleSignIn}
          disabled={googleLoading || loading}
        >
          <GoogleIcon />
          {googleLoading ? 'Connecting Google...' : 'Continue with Google'}
        </button>

        <div className="auth-footer" style={{ marginTop: '1.25rem' }}>
          Don't have an account? <Link to="/register" className="auth-link">Create an account</Link>
        </div>
      </div>
    </div>
  );
}

export default Login;
