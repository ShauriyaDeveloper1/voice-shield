import React from 'react';
import './ThreatAnalytics.css';

function ThreatAnalytics() {
  const trendData = [
    { day: 'M', height: '35%' },
    { day: 'T', height: '55%' },
    { day: 'W', height: '28%' },
    { day: 'T', height: '70%' },
    { day: 'F', height: '48%' },
    { day: 'S', height: '80%' },
    { day: 'S', height: '40%' },
  ];

  const signals = [
    { label: 'Voice biometrics', value: 96 },
    { label: 'Spectral artifacts', value: 91 },
    { label: 'Prosody / timing', value: 89 },
    { label: 'Social engineering', value: 94 },
  ];

  return (
    <section className="analytics-page-container">
      {/* Stats Cards Section */}
      <div className="analytics-stats-grid">
        <div className="analytics-stat-card">
          <span className="card-lbl">AI voices detected</span>
          <strong className="card-val">11</strong>
          <span className="card-sub">8.6% of scanned calls</span>
        </div>
        
        <div className="analytics-stat-card">
          <span className="card-lbl">Impersonation attempts</span>
          <strong className="card-val">7</strong>
          <span className="card-sub danger-text">+40% vs last month</span>
        </div>
        
        <div className="analytics-stat-card">
          <span className="card-lbl">False positives</span>
          <strong className="card-val">1.8%</strong>
          <span className="card-sub">Below target of 3%</span>
        </div>
        
        <div className="analytics-stat-card">
          <span className="card-lbl">Protected minutes</span>
          <strong className="card-val">31h</strong>
          <span className="card-sub">Across 128 calls</span>
        </div>
      </div>

      {/* Chart and Signals Section */}
      <div className="analytics-content-row">
        {/* Left Column: 7-Day Trend Chart */}
        <div className="analytics-chart-panel">
          <div className="panel-header">
            <span className="panel-subtitle">7-DAY TREND</span>
            <h3 className="panel-title">Threat score</h3>
          </div>
          
          <div className="chart-container">
            <div className="bar-chart-grid">
              {trendData.map((data, idx) => (
                <div key={idx} className="chart-bar-wrapper">
                  <div className="chart-bar-slot">
                    <div className="chart-bar-fill" style={{ height: data.height }} />
                  </div>
                  <span className="chart-bar-label">{data.day}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column: Model Signals */}
        <div className="analytics-signals-panel">
          <div className="panel-header">
            <span className="panel-subtitle">MODEL SIGNALS</span>
            <h3 className="panel-title">What we inspect</h3>
          </div>
          
          <div className="signals-list">
            {signals.map((sig, idx) => (
              <div key={idx} className="signal-row">
                <div className="signal-row-header">
                  <span className="signal-name">{sig.label}</span>
                  <span className="signal-percent">{sig.value}%</span>
                </div>
                <div className="signal-bar-slot">
                  <div className="signal-bar-fill" style={{ width: `${sig.value}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export default ThreatAnalytics;
