function RiskScore({ score = 0, label = 'Unknown' }) {
  return <article className="metric-card"><span>Risk score</span><strong>{score}%</strong><small>{label}</small></article>
}

export default RiskScore
