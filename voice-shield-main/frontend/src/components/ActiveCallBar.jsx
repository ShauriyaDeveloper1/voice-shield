import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Phone, PhoneOff, Volume2, VolumeX, Shield, ArrowUpRight } from 'lucide-react';
import { useCall } from '../context/CallContext';

function ActiveCallBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const {
    callState,
    activePeerName,
    activePeerPhone,
    duration,
    riskScore,
    isMuted,
    toggleMute,
    hangUp,
  } = useCall();

  // Only display the floating bar if call is active and user is NOT on /calls page
  if (callState !== 'connected' && callState !== 'dialing') {
    return null;
  }

  if (location.pathname === '/calls') {
    return null;
  }

  const formatTime = (secs) => {
    const m = String(Math.floor(secs / 60)).padStart(2, '0');
    const s = String(secs % 60).padStart(2, '0');
    return `${m}:${s}`;
  };

  return (
    <div className="active-call-floating-bar" onClick={() => navigate('/calls')}>
      <div className="floating-bar-left">
        <div className="floating-bar-pulse-indicator">
          <span className="pulsing-call-dot"></span>
        </div>
        <div className="floating-bar-details">
          <span className="floating-bar-label">
            {callState === 'dialing' ? 'Dialing...' : 'Live Call in Progress'}
          </span>
          <strong className="floating-bar-peer">{activePeerName || activePeerPhone}</strong>
        </div>
      </div>

      <div className="floating-bar-center">
        {callState === 'connected' && (
          <>
            <span className="floating-bar-timer">{formatTime(duration)}</span>
            <span className={`floating-bar-risk-pill ${riskScore > 35 ? 'danger' : 'safe'}`}>
              <Shield size={12} />
              <span>Risk: {riskScore}%</span>
            </span>
          </>
        )}
      </div>

      <div className="floating-bar-right" onClick={(e) => e.stopPropagation()}>
        {callState === 'connected' && (
          <button 
            className={`floating-btn mute ${isMuted ? 'muted' : ''}`}
            onClick={toggleMute}
            title={isMuted ? 'Unmute microphone' : 'Mute microphone'}
          >
            {isMuted ? <VolumeX size={15} /> : <Volume2 size={15} />}
          </button>
        )}

        <button 
          className="floating-btn hangup"
          onClick={hangUp}
          title="End Call"
        >
          <PhoneOff size={15} />
        </button>

        <button 
          className="floating-btn console"
          onClick={() => navigate('/calls')}
          title="Return to Call Console"
        >
          <span>Console</span>
          <ArrowUpRight size={14} />
        </button>
      </div>
    </div>
  );
}

export default ActiveCallBar;
