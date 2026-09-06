function CallStatus({ status = 'Waiting' }) {
  return <div className="call-status"><span className="status-dot" aria-hidden="true" />{status}</div>
}

export default CallStatus
