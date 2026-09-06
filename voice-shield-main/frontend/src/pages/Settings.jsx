import React, { useState } from 'react';
import { checkHealth } from '../services/api';
import './Settings.css';

function Settings({ backendOnline }) {
  const [realtimeMonitoring, setRealtimeMonitoring] = useState(true);
  const [highRiskWarning, setHighRiskWarning] = useState(true);
  const [audioConsent, setAudioConsent] = useState(true);
  const [testingConnection, setTestingConnection] = useState(false);
  const [testResult, setTestResult] = useState(null);

  const handleTestConnection = async () => {
    setTestingConnection(true);
    setTestResult(null);
    try {
      const isOnline = await checkHealth();
      if (isOnline) {
        setTestResult('success');
      } else {
        setTestResult('failed');
      }
    } catch (err) {
      setTestResult('failed');
    } finally {
      setTestingConnection(false);
    }
  };

  return (
    <section className="settings-page-container">
      <div className="settings-grid">
        {/* Left Column: Security Controls */}
        <div className="settings-panel">
          <div className="panel-header">
            <span className="panel-subtitle">PROTECTION</span>
            <h3 className="panel-title">Security controls</h3>
          </div>

          <div className="controls-list">
            <div className="control-item">
              <div className="control-text">
                <span className="control-name">Realtime call monitoring</span>
                <p className="control-desc">Analyze incoming and outgoing calls.</p>
              </div>
              <label className="toggle-switch">
                <input 
                  type="checkbox" 
                  checked={realtimeMonitoring} 
                  onChange={(e) => setRealtimeMonitoring(e.target.checked)} 
                />
                <span className="toggle-slider" />
              </label>
            </div>

            <div className="control-item">
              <div className="control-text">
                <span className="control-name">High-risk call warning</span>
                <p className="control-desc">Show an interruption before sensitive actions.</p>
              </div>
              <label className="toggle-switch">
                <input 
                  type="checkbox" 
                  checked={highRiskWarning} 
                  onChange={(e) => setHighRiskWarning(e.target.checked)} 
                />
                <span className="toggle-slider" />
              </label>
            </div>

            <div className="control-item">
              <div className="control-text">
                <span className="control-name">Audio capture consent</span>
                <p className="control-desc">Only analyze audio after permission is granted.</p>
              </div>
              <label className="toggle-switch">
                <input 
                  type="checkbox" 
                  checked={audioConsent} 
                  onChange={(e) => setAudioConsent(e.target.checked)} 
                />
                <span className="toggle-slider" />
              </label>
            </div>
          </div>
        </div>

        {/* Right Column: AI Engine Info */}
        <div className="settings-panel">
          <div className="panel-header">
            <span className="panel-subtitle">BACKEND</span>
            <h3 className="panel-title">AI engine</h3>
          </div>

          <div className="engine-info-list">
            <div className="info-row">
              <span className="info-label">Python analysis server</span>
              <span className={`info-status ${backendOnline ? 'status-green' : 'status-red'}`}>
                {backendOnline ? 'ONLINE' : 'OFFLINE'}
              </span>
            </div>

            <div className="info-row">
              <span className="info-label">Risk engine</span>
              <span className="info-status status-green">READY</span>
            </div>

            <div className="info-row">
              <span className="info-label">Model version</span>
              <span className="info-version-text">VoiceShield-DETECT-0.1</span>
            </div>
          </div>

          <div className="test-connection-section">
            <button 
              className={`test-conn-btn ${testingConnection ? 'loading' : ''}`}
              onClick={handleTestConnection}
              disabled={testingConnection}
            >
              {testingConnection ? 'Testing...' : 'Test backend connection'}
            </button>

            {testResult === 'success' && (
              <p className="test-result-msg success">Backend online and communicating successfully.</p>
            )}
            {testResult === 'failed' && (
              <p className="test-result-msg failed">Unable to connect to backend server. Make sure FastAPI is running.</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

export default Settings;
