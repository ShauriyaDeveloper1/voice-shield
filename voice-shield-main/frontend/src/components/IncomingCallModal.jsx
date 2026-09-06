import React from 'react';
import { Phone, PhoneOff } from 'lucide-react';
import { useCall } from '../context/CallContext';

function IncomingCallModal() {
  const {
    isIncomingCall,
    incomingCallData,
    getContactNameByPhone,
    answerCall,
    declineCall,
  } = useCall();

  if (!isIncomingCall || !incomingCallData) {
    return null;
  }

  const callerPhone = incomingCallData.from_phone;
  const callerName = incomingCallData.from_name || getContactNameByPhone(callerPhone);
  const initials = callerName ? callerName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'IN';

  return (
    <div className="incoming-call-overlay">
      <div className="incoming-call-card">
        <div className="incoming-avatar pulse">
          {initials}
        </div>
        <h3 className="incoming-caller-title">Incoming Protected Call</h3>
        <h2 className="incoming-caller-name">{callerName}</h2>
        <p className="incoming-caller-phone">{callerPhone}</p>
        
        <div className="incoming-actions">
          <button className="answer-btn" onClick={answerCall}>
            <Phone size={18} fill="currentColor" />
            <span>Answer</span>
          </button>
          <button className="decline-btn" onClick={declineCall}>
            <PhoneOff size={18} />
            <span>Decline</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default IncomingCallModal;
