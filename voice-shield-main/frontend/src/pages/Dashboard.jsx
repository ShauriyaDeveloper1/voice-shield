import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Play, ArrowRight, CheckCircle2, AlertTriangle, ShieldCheck, Check } from 'lucide-react';
import './Dashboard.css';

function Dashboard() {
  const navigate = useNavigate();

  const handleStartCall = () => {
    navigate('/calls');
  };

  const handleViewAllHistory = () => {
    navigate('/history');
  };

  const recentCalls = [
    {
      name: 'Rahul Kumar',
      initials: 'RK',
      time: 'Today, 16:42',
      phone: '+91 98••• ••421',
      badgeClass: 'safe',
      badgeText: 'Safe'
    },
    {
      name: 'Unknown caller',
      initials: 'Uc',
      time: 'Today, 14:18',
      phone: '+91 70••• ••903',
      badgeClass: 'blocked',
      badgeText: 'Blocked'
    },
    {
      name: 'Priya Sharma',
      initials: 'PS',
      time: 'Yesterday, 19:06',
      phone: '+91 88••• ••117',
      badgeClass: 'safe',
      badgeText: 'Safe'
    },
    {
      name: 'Bank Support',
      initials: 'BS',
      time: 'Yesterday, 11:31',
      phone: '+91 80••• ••221',
      badgeClass: 'warned',
      badgeText: 'Warned'
    }
  ];

  const riskSignals = [
    {
      title: 'Voice consistency',
      desc: 'Speaker embedding matches expected profile',
      score: '98%',
      status: 'success'
    },
    {
      title: 'Synthetic speech artifacts',
      desc: 'Minor spectral irregularities detected',
      score: '22%',
      status: 'warning'
    },
    {
      title: 'Social engineering',
      desc: 'No urgent-transfer language detected',
      score: 'LOW',
      status: 'success'
    }
  ];

  return (
    <section className="dashboard-container">
      {/* Top Threat Shield Banner */}
      <div className="threat-shield-banner">
        <div className="banner-left">
          <span className="banner-eyebrow">THREAT SHIELD</span>
          <h2 className="banner-heading">Your voice is protected.</h2>
          <p className="banner-subheading">
            VoiceShield monitors calls for AI-generated speech, impersonation patterns, and social-engineering signals.
          </p>
          <button className="start-call-action-btn" onClick={handleStartCall}>
            <Play size={14} fill="currentColor" />
            <span>Start protected call</span>
          </button>
        </div>
        <div className="banner-right">
          <div className="success-circle">
            <div className="pulse-ring ring-1"></div>
            <div className="pulse-ring ring-2"></div>
            <div className="dotted-ring"></div>
            <div className="glow"></div>
            <span className="checkmark">✓</span>
          </div>
        </div>
      </div>

      {/* Stats row */}
      <div className="stats-row-grid">
        <div className="stat-item-card">
          <span className="stat-label">Calls scanned</span>
          <strong className="stat-value">128</strong>
          <span className="stat-subtext">+12 this week</span>
        </div>
        
        <div className="stat-item-card">
          <span className="stat-label">Threats blocked</span>
          <strong className="stat-value">7</strong>
          <span className="stat-subtext danger-text">2 high-risk today</span>
        </div>
        
        <div className="stat-item-card">
          <span className="stat-label">Avg. confidence</span>
          <strong className="stat-value">94.8%</strong>
          <span className="stat-subtext">Detection engine</span>
        </div>
        
        <div className="stat-item-card">
          <span className="stat-label">Protection</span>
          <strong className="stat-value success-text">ON</strong>
          <span className="stat-subtext">Microphone guarded</span>
        </div>
      </div>

      {/* Bottom Columns */}
      <div className="dashboard-content-columns">
        {/* Left Column: Recent Activity */}
        <div className="activity-panel">
          <div className="panel-header-row">
            <div className="panel-header-title-group">
              <span className="panel-sub">RECENT ACTIVITY</span>
              <h3 className="panel-main">Latest calls</h3>
            </div>
            <button className="view-all-btn" onClick={handleViewAllHistory}>
              <span>View all</span>
              <ArrowRight size={14} />
            </button>
          </div>

          <div className="calls-list">
            {recentCalls.map((call, idx) => (
              <div key={idx} className="call-list-item">
                <div className="call-avatar">
                  {call.initials}
                </div>
                <div className="call-item-details">
                  <span className="call-user-name">{call.name}</span>
                  <span className="call-user-meta">{call.time} • {call.phone}</span>
                </div>
                <span className={`badge ${call.badgeClass}`}>
                  {call.badgeText}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Right Column: Risk Engine Signals */}
        <div className="signals-panel">
          <div className="panel-header-row">
            <div className="panel-header-title-group">
              <span className="panel-sub">RISK ENGINE</span>
              <h3 className="panel-main">Current signals</h3>
            </div>
            <span className="live-pill">LIVE</span>
          </div>

          <div className="signals-list">
            {riskSignals.map((sig, idx) => (
              <div key={idx} className="signal-item">
                <div className={`signal-icon-container ${sig.status}`}>
                  {sig.status === 'success' ? (
                    <CheckCircle2 size={16} />
                  ) : (
                    <AlertTriangle size={16} />
                  )}
                </div>
                <div className="signal-details">
                  <span className="signal-title">{sig.title}</span>
                  <span className="signal-desc">{sig.desc}</span>
                </div>
                <span className={`signal-score ${sig.status}`}>
                  {sig.score}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export default Dashboard;
