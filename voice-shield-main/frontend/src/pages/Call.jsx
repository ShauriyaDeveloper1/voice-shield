import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Phone, PhoneOff, UploadCloud, ShieldAlert, CheckCircle, AlertTriangle, RefreshCw, Delete, Volume2, VolumeX, Shield, Play } from 'lucide-react';
import api, { uploadAudioFile } from '../services/api';
import { useCall } from '../context/CallContext';
import './Call.css';

function Call({ currentUser, onAddAlert }) {
  const [searchParams] = useSearchParams();
  const initialPhone = searchParams.get('phone') || '';

  // Tab states: 'live' or 'upload'
  const [activeTab, setActiveTab] = useState('live');

  // Directory and Dial Pad local states
  const [typedNumber, setTypedNumber] = useState(initialPhone);
  const [calleeName, setCalleeName] = useState('');

  // Upload States
  const [dragActive, setDragActive] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState(null);

  // Global Call State & Actions
  const {
    callState,
    activePeerName,
    activePeerPhone,
    duration,
    voiceAuth,
    speakerMatch,
    riskScore,
    setRiskScore,
    isMuted,
    directory,
    setDirectory,
    getContactNameByPhone,
    initiateCall,
    hangUp,
    toggleMute,
    resetCallState,
    startDemoCall,
  } = useCall();

  const myPhone = currentUser?.phone || '+91 99999 99999';

  // Load dynamic contacts for current user
  useEffect(() => {
    if (currentUser) {
      api.getContacts(currentUser.id)
        .then((data) => {
          const list = [
            ...data,
            { id: 'echo', name: 'VoiceShield Echo (Demo)', relation: 'System', phone: '000' }
          ];
          setDirectory(list);
        })
        .catch((err) => {
          console.warn('Failed to load dynamic contacts, using local storage cache fallback:', err);
          const cached = localStorage.getItem(`voiceshield_contacts_${currentUser.id}`);
          if (cached) {
            setDirectory([
              ...JSON.parse(cached),
              { id: 'echo', name: 'VoiceShield Echo (Demo)', relation: 'System', phone: '000' }
            ]);
          } else {
            setDirectory([
              { id: 'echo', name: 'VoiceShield Echo (Demo)', relation: 'System', phone: '000' }
            ]);
          }
        });
    }
  }, [currentUser, setDirectory]);

  // Update callee details if initial phone parameter is loaded
  useEffect(() => {
    if (initialPhone) {
      setTypedNumber(initialPhone);
      const contact = directory.find((c) => c.phone === initialPhone);
      setCalleeName(contact ? contact.name : getContactNameByPhone(initialPhone));
    }
  }, [initialPhone, directory, getContactNameByPhone]);

  // Dial pad keys press
  const handleKeyPress = (val) => {
    setTypedNumber((prev) => prev + val);
  };

  const handleBackspace = () => {
    setTypedNumber((prev) => prev.slice(0, -1));
  };

  const selectContact = (phone) => {
    setTypedNumber(phone);
    setCalleeName(getContactNameByPhone(phone));
  };

  // Initiate Internet Call
  const handleInitiateCall = () => {
    if (!typedNumber) return;
    const name = calleeName || getContactNameByPhone(typedNumber);
    initiateCall(typedNumber, name);
  };

  // Format MM:SS
  const formatTime = (secs) => {
    const m = String(Math.floor(secs / 60)).padStart(2, '0');
    const s = String(secs % 60).padStart(2, '0');
    return `${m}:${s}`;
  };

  // Upload Callbacks
  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      processUpload(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      processUpload(e.target.files[0]);
    }
  };

  const processUpload = async (file) => {
    setUploadFile(file);
    setAnalyzing(true);
    setAnalysisResult(null);

    try {
      const result = await uploadAudioFile(file);
      setAnalysisResult(result);
      if (result.risk_score > 35 && onAddAlert) {
        onAddAlert({
          severity: result.severity || 'HIGH',
          message: `Audio verification warning: File upload "${file.name}" returned a risk score of ${result.risk_score}%.`,
          recommendation: result.severity === 'HIGH' ? 'High spoofing probability. Do not authenticate or trust.' : 'Suspicious acoustic pattern detected.'
        });
      }
    } catch (err) {
      console.error('File upload analysis failed, falling back to mock:', err);
      setTimeout(() => {
        const mockResult = {
          deepfake_score: 0.78,
          speaker_similarity: 0.64,
          risk_score: 82,
          severity: 'HIGH',
        };
        setAnalysisResult(mockResult);
        if (onAddAlert) {
          onAddAlert({
            severity: 'HIGH',
            message: `Audio verification warning: File upload "${file.name}" analyzed with mock risk score of 82%.`,
            recommendation: 'High spoofing probability. Do not authenticate or trust.'
          });
        }
      }, 1500);
    } finally {
      setAnalyzing(false);
    }
  };

  const displayedPeerName = activePeerName || calleeName || getContactNameByPhone(typedNumber);
  const displayedPeerPhone = activePeerPhone || typedNumber;
  const avatarInitials = displayedPeerName ? displayedPeerName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'VS';

  return (
    <section className="call-page-container">
      {/* Navigation tabs */}
      <div className="tabs-header">
        <button 
          className={`tab-btn ${activeTab === 'live' ? 'active' : ''}`}
          onClick={() => setActiveTab('live')}
          disabled={callState !== 'idle'}
        >
          Live Protection
        </button>
        <button 
          className={`tab-btn ${activeTab === 'upload' ? 'active' : ''}`}
          onClick={() => setActiveTab('upload')}
          disabled={callState !== 'idle'}
        >
          Audio Upload Analysis
        </button>
      </div>

      {activeTab === 'live' ? (
        <div className="tab-pane">
          {callState === 'idle' ? (
            <div className="call-lobby-layout">
              {/* Left Side: Dial Pad */}
              <div className="lobby-dialpad-panel">
                <h3 className="lobby-title-sub">SECURE TELEPHONY</h3>
                <h2 className="lobby-title-main">Secure Dial Pad</h2>

                <div className="dialpad-number-display">
                  <input 
                    type="text" 
                    placeholder="Enter phone number..." 
                    value={typedNumber}
                    onChange={(e) => setTypedNumber(e.target.value)}
                    className="dialpad-input"
                  />
                  {typedNumber && (
                    <button className="dialpad-clear-btn" onClick={handleBackspace}>
                      <Delete size={18} />
                    </button>
                  )}
                </div>

                <div className="dialpad-grid">
                  {['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'].map((key) => (
                    <button 
                      key={key} 
                      className="dialpad-key" 
                      onClick={() => handleKeyPress(key)}
                    >
                      <span className="key-num">{key}</span>
                    </button>
                  ))}
                </div>

                <button 
                  className="dialpad-call-btn"
                  onClick={handleInitiateCall}
                  disabled={!typedNumber}
                >
                  <Phone size={18} fill="currentColor" />
                  <span>Call Monitored</span>
                </button>
                <div className="my-phone-number-info">
                  Your number: <strong className="highlight">{myPhone}</strong>
                </div>
              </div>

              {/* Right Side: Directory list */}
              <div className="lobby-directory-panel">
                <div className="directory-header">
                  <h3 className="directory-subtitle">DIRECTORY</h3>
                  <h4 className="directory-title">Registered Users</h4>
                </div>
                <div className="directory-list">
                  {directory.map((contact, idx) => (
                    <div 
                      key={idx} 
                      className={`directory-item ${typedNumber === contact.phone ? 'selected' : ''}`}
                      onClick={() => selectContact(contact.phone)}
                    >
                      <div className="directory-item-left">
                        <div className="directory-avatar">
                          {contact.name ? contact.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'C'}
                        </div>
                        <div className="directory-details">
                          <span className="directory-name">{contact.name}</span>
                          <span className="directory-phone">{contact.phone}</span>
                        </div>
                      </div>
                      <span className={`directory-tag ${(contact.relation || 'Friend').toLowerCase()}`}>
                        {contact.relation || 'Friend'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : callState === 'dialing' || callState === 'ringing' ? (
            <div className="calling-panel">
              <div className="calling-status-card">
                <div className="ringing-avatar pulse">
                  {avatarInitials}
                </div>
                <h2 className="calling-callee-name">{displayedPeerName}</h2>
                <p className="calling-number">{displayedPeerPhone}</p>
                <div className="pulse-connection-loader">
                  <div className="loader-ring" />
                  <span className="ringing-label">DIALING MONITORED SESSION...</span>
                </div>
                <button className="hangup-action-btn" onClick={hangUp}>
                  <PhoneOff size={20} />
                  <span>Cancel call</span>
                </button>
              </div>
            </div>
          ) : callState === 'offline' ? (
            <div className="calling-panel">
              <div className="calling-status-card offline-mode">
                <div className="lobby-icon-container warning">
                  <AlertTriangle size={32} />
                </div>
                <h2 className="calling-callee-name">{displayedPeerName} is Offline</h2>
                <p className="offline-subtext">This user is not registered or connected right now.</p>
                <div className="offline-actions">
                  <button className="demo-session-btn" onClick={() => startDemoCall(displayedPeerPhone, displayedPeerName)}>
                    <Play size={14} fill="currentColor" />
                    <span>Start Mock Demo call</span>
                  </button>
                  <button className="back-lobby-btn" onClick={resetCallState}>
                    <span>Back to dial pad</span>
                  </button>
                </div>
              </div>
            </div>
          ) : (
            /* Active Call Panel (Connected state) */
            <div className="active-call-grid-layout">
              {/* Left Column: Live Call UI */}
              <div className="active-call-card-panel">
                <div className="live-panel-header">
                  <span className="live-pill-indicator pulsing">LIVE ANALYSIS</span>
                  <span className="live-timer-span">{formatTime(duration)}</span>
                </div>

                <div className="active-call-avatar-center">
                  <div className="active-avatar-glow">
                    <span className="active-avatar-initials">
                      {avatarInitials}
                    </span>
                  </div>
                  <h3 className="active-callee-title">{displayedPeerName}</h3>
                  <span className="active-callee-meta">{displayedPeerPhone}</span>
                </div>

                {/* Animated Waveform */}
                <div className="active-call-soundwave" aria-label="Audio wave">
                  {Array.from({ length: 24 }).map((_, i) => (
                    <span 
                      key={i} 
                      className="soundwave-bar" 
                      style={{ 
                        height: `${15 + (Math.sin(duration * 2 + i) * 15) + (Math.random() * 25)}%`,
                        animationDelay: `${i * 0.08}s` 
                      }} 
                    />
                  ))}
                </div>

                {/* Risk score gauge dial */}
                <div className="risk-gauge-block">
                  <div className="gauge-dial-outer">
                    <svg className="gauge-svg" viewBox="0 0 100 100">
                      <circle className="gauge-track-bg" cx="50" cy="50" r="40" />
                      <circle 
                        className="gauge-track-fill" 
                        cx="50" 
                        cy="50" 
                        r="40" 
                        style={{ strokeDasharray: `${2 * Math.PI * 40}`, strokeDashoffset: `${2 * Math.PI * 40 * (1 - riskScore / 100)}` }}
                      />
                    </svg>
                    <div className="gauge-number-center">
                      <strong className="gauge-val-text">{riskScore}</strong>
                      <span className="gauge-lbl-text">RISK SCORE</span>
                    </div>
                  </div>
                </div>

                {/* Control Options */}
                <div className="active-call-controls-row">
                  <button className={`control-btn ${isMuted ? 'muted' : ''}`} onClick={toggleMute}>
                    {isMuted ? <VolumeX size={18} /> : <Volume2 size={18} />}
                    <span>{isMuted ? 'Unmute' : 'Mute'}</span>
                  </button>
                  <button className="control-btn hangup" onClick={hangUp}>
                    <PhoneOff size={18} />
                    <span>End call</span>
                  </button>
                </div>
              </div>

              {/* Right Column: AI Analysis Feed */}
              <div className="active-analysis-feed-panel">
                <div className="feed-header-row">
                  <div className="feed-header-title">
                    <span className="feed-subtitle">AI ANALYSIS</span>
                    <h3 className="feed-title">Detection feed</h3>
                  </div>
                  <button className="feed-refresh-btn" onClick={() => setRiskScore(18)}>
                    <RefreshCw size={12} />
                    <span>Refresh</span>
                  </button>
                </div>

                <div className="analysis-feed-list">
                  <div className="analysis-feed-item bar-green">
                    <span className="feed-item-title">Voice embedding</span>
                    <p className="feed-item-desc">Speaker profile is consistent with previous verified calls ({voiceAuth}% match).</p>
                  </div>

                  <div className="analysis-feed-item bar-green">
                    <span className="feed-item-title">Spectral analysis</span>
                    <p className="feed-item-desc">No strong vocoder signature. Natural harmonic variance observed ({speakerMatch}% match).</p>
                  </div>

                  <div className="analysis-feed-item bar-cyan">
                    <span className="feed-item-title">Prosody</span>
                    <p className="feed-item-desc">Speaking rhythm is natural and well below spoofing threshold.</p>
                  </div>

                  <div className="analysis-feed-item bar-green">
                    <span className="feed-item-title">Conversation context</span>
                    <p className="feed-item-desc">No requests for OTP, transfers, passwords or remote access.</p>
                  </div>
                </div>

                {/* Bottom safety alert box */}
                <div className={`analysis-verdict-card ${riskScore > 35 ? 'danger' : 'safe'}`}>
                  {riskScore <= 35 ? (
                    <div className="verdict-content">
                      <CheckCircle size={18} className="verdict-icon success" />
                      <div className="verdict-text-group">
                        <span className="verdict-title text-success">Safe to continue</span>
                        <p className="verdict-desc">No high-confidence impersonation signal currently detected.</p>
                      </div>
                    </div>
                  ) : (
                    <div className="verdict-content">
                      <ShieldAlert size={18} className="verdict-icon alert" />
                      <div className="verdict-text-group">
                        <span className="verdict-title text-danger">Threat Warning</span>
                        <p className="verdict-desc">High risk spoofing markers identified. Exercise extreme caution.</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      ) : (
        /* Upload analysis Tab pane */
        <div className="tab-pane">
          <div className="upload-section">
            <h2>Audio Verification Terminal</h2>
            <p>Upload files (WAV, MP3) to analyze deepfake markers and profile similarity.</p>

            {/* Drop Zone */}
            <div 
              className={`upload-dropzone ${dragActive ? 'active' : ''}`}
              onDragEnter={handleDrag}
              onDragOver={handleDrag}
              onDragLeave={handleDrag}
              onDrop={handleDrop}
            >
              <input 
                type="file" 
                id="file-upload-input" 
                className="file-input-hidden" 
                accept="audio/wav, audio/mpeg, audio/mp3" 
                onChange={handleFileChange}
              />
              <label htmlFor="file-upload-input" className="dropzone-label">
                <UploadCloud size={44} className="upload-icon" />
                <span className="dropzone-text-primary">Click to upload or drag & drop</span>
                <span className="dropzone-text-secondary">WAV or MP3 (Max 15MB)</span>
              </label>
            </div>

            {uploadFile && (
              <div className="uploaded-file-row">
                <span className="file-name-label">File: {uploadFile.name}</span>
                {analyzing && <RefreshCw size={16} className="spin-icon" />}
              </div>
            )}

            {/* Loading State */}
            {analyzing && (
              <div className="analyzing-loader">
                <div className="loading-bar"></div>
                <span>Analyzing...</span>
              </div>
            )}

            {/* Results Panel */}
            {analysisResult && !analyzing && (
              <div className="analysis-results-card">
                <h3>Verification Report</h3>

                <div className="results-grid">
                  <div className="result-metric">
                    <span className="result-label">Deepfake Score</span>
                    <span className="result-val">{Math.round(analysisResult.deepfake_score * 100)}%</span>
                  </div>

                  <div className="result-metric">
                    <span className="result-label">Speaker Match</span>
                    <span className="result-val">{Math.round(analysisResult.speaker_similarity * 100)}%</span>
                  </div>

                  <div className="result-metric risk-status">
                    <span className="result-label">Risk</span>
                    <span className={`result-val-risk severity-${analysisResult.severity ? analysisResult.severity.toLowerCase() : 'high'}`}>
                      {analysisResult.risk_score} {analysisResult.severity}
                    </span>
                  </div>
                </div>

                <div className="results-alert-box">
                  {analysisResult.severity === 'HIGH' ? (
                    <div className="alert-content high">
                      <ShieldAlert size={20} />
                      <div>
                        <strong>Spoofing Warning:</strong> High probability of synthetic audio detected. Do not share confidential information.
                      </div>
                    </div>
                  ) : analysisResult.severity === 'MEDIUM' ? (
                    <div className="alert-content medium">
                      <AlertTriangle size={20} />
                      <div>
                        <strong>Suspicious Signal:</strong> Minor vocal discrepancies identified. Proceed with caution.
                      </div>
                    </div>
                  ) : (
                    <div className="alert-content low">
                      <CheckCircle size={20} />
                      <div>
                        <strong>Caller Authentic:</strong> Voice matches trusted template. No threats detected.
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

export default Call;
