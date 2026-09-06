import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { API_BASE } from '../services/api';

const CallContext = createContext(null);

const ICE_SERVERS = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    { urls: 'stun:stun3.l.google.com:19302' },
    { urls: 'stun:stun4.l.google.com:19302' },
    { urls: 'stun:stun.services.mozilla.com:3478' },
    { urls: 'stun:stun.relay.metered.ca:80' },
    {
      urls: 'turn:openrelay.metered.ca:80',
      username: 'openrelayproject',
      credential: 'openrelayproject',
    },
    {
      urls: 'turn:openrelay.metered.ca:443',
      username: 'openrelayproject',
      credential: 'openrelayproject',
    },
    {
      urls: 'turn:openrelay.metered.ca:443?transport=tcp',
      username: 'openrelayproject',
      credential: 'openrelayproject',
    },
  ],
};

export function CallProvider({ children, currentUser, onAddAlert }) {
  // Call state: 'idle' | 'dialing' | 'ringing' | 'connected' | 'offline'
  const [callState, setCallState] = useState('idle');
  const [isIncomingCall, setIsIncomingCall] = useState(false);
  const [incomingCallData, setIncomingCallData] = useState(null);

  // Active call peer information
  const [activePeerName, setActivePeerName] = useState('');
  const [activePeerPhone, setActivePeerPhone] = useState('');

  // Call metrics
  const [duration, setDuration] = useState(0);
  const [voiceAuth, setVoiceAuth] = useState(98);
  const [speakerMatch, setSpeakerMatch] = useState(98);
  const [riskScore, setRiskScore] = useState(18);
  const [isMuted, setIsMuted] = useState(false);

  // Directory cache
  const [directory, setDirectory] = useState([]);

  // WebRTC & WebSocket references
  const wsRef = useRef(null);
  const pcRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteAudioRef = useRef(null);
  const iceCandidatesQueueRef = useRef([]);
  const initializingPcPromiseRef = useRef(null);

  const timerRef = useRef(null);
  const fluctuationRef = useRef(null);
  const alertTriggeredRef = useRef(false);
  const reconnectTimeoutRef = useRef(null);

  const myPhone = currentUser?.phone || '';
  const myName = currentUser?.name || 'Anonymous';

  // ──────────────────────────────────────────────────────────────────
  // Refs that mirror state so that the stable WebSocket handler can
  // always read latest values without the WS effect re-running.
  // ──────────────────────────────────────────────────────────────────
  const callStateRef = useRef(callState);
  const isIncomingCallRef = useRef(isIncomingCall);
  const incomingCallDataRef = useRef(incomingCallData);
  const activePeerPhoneRef = useRef(activePeerPhone);
  const activePeerNameRef = useRef(activePeerName);
  const riskScoreRef = useRef(riskScore);
  const durationRef = useRef(duration);
  const directoryRef = useRef(directory);
  const isMutedRef = useRef(isMuted);
  const myNameRef = useRef(myName);
  const onAddAlertRef = useRef(onAddAlert);

  useEffect(() => { callStateRef.current = callState; }, [callState]);
  useEffect(() => { isIncomingCallRef.current = isIncomingCall; }, [isIncomingCall]);
  useEffect(() => { incomingCallDataRef.current = incomingCallData; }, [incomingCallData]);
  useEffect(() => { activePeerPhoneRef.current = activePeerPhone; }, [activePeerPhone]);
  useEffect(() => { activePeerNameRef.current = activePeerName; }, [activePeerName]);
  useEffect(() => { riskScoreRef.current = riskScore; }, [riskScore]);
  useEffect(() => { durationRef.current = duration; }, [duration]);
  useEffect(() => { directoryRef.current = directory; }, [directory]);
  useEffect(() => { isMutedRef.current = isMuted; }, [isMuted]);
  useEffect(() => { myNameRef.current = myName; }, [myName]);
  useEffect(() => { onAddAlertRef.current = onAddAlert; }, [onAddAlert]);

  // ──────────────────────────────────────────────────────────────────
  // Stable helper: look up contact name (reads directoryRef)
  // ──────────────────────────────────────────────────────────────────
  const getContactNameByPhoneStable = useCallback((phone) => {
    if (!phone) return 'Unknown';
    const dir = directoryRef.current;
    const contact = dir.find((c) => c.phone === phone);
    if (contact) return contact.name;
    const digits = phone.replace(/[^\d]/g, '');
    return digits.length >= 10 ? `+91 ${digits.slice(-10)}` : phone;
  }, []);                          // ← stable: zero deps

  // Also keep an unstable version for the context value so the UI re-renders
  // when directory changes.
  const getContactNameByPhone = useCallback((phone) => {
    if (!phone) return 'Unknown';
    const contact = directory.find((c) => c.phone === phone);
    if (contact) return contact.name;
    const digits = phone.replace(/[^\d]/g, '');
    return digits.length >= 10 ? `+91 ${digits.slice(-10)}` : phone;
  }, [directory]);

  // ──────────────────────────────────────────────────────────────────
  // WebRTC helpers (all stable — zero or ref-only deps)
  // ──────────────────────────────────────────────────────────────────

  const processQueuedCandidates = async (pc) => {
    if (!pc || !pc.remoteDescription) return;
    const queue = [...iceCandidatesQueueRef.current];
    iceCandidatesQueueRef.current = [];
    for (const candidate of queue) {
      if (candidate) {
        try {
          await pc.addIceCandidate(new RTCIceCandidate(candidate));
        } catch (err) {
          console.warn('Error adding queued ICE candidate:', err);
        }
      }
    }
  };

  const cleanupWebRTC = useCallback(() => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
    }
    if (pcRef.current) {
      try {
        pcRef.current.onicecandidate = null;
        pcRef.current.ontrack = null;
        pcRef.current.onconnectionstatechange = null;
        pcRef.current.oniceconnectionstatechange = null;
        pcRef.current.close();
      } catch (e) {
        console.warn('Error closing peer connection:', e);
      }
      pcRef.current = null;
    }
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = null;
    }
    const globalAudio = document.getElementById('voiceshield-remote-audio');
    if (globalAudio) {
      globalAudio.srcObject = null;
    }
    iceCandidatesQueueRef.current = [];
    initializingPcPromiseRef.current = null;
  }, []);                          // ← stable

  const saveCallToHistory = useCallback((name, phone, finalScore, seconds) => {
    try {
      const historyStr = localStorage.getItem('voiceshield_call_history') || '[]';
      const history = JSON.parse(historyStr);
      const risk = finalScore < 34 ? 'low' : finalScore < 67 ? 'medium' : 'high';
      const status = risk === 'low' ? 'Safe' : risk === 'medium' ? 'Warned' : 'Blocked';
      const formattedTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

      const newRecord = {
        id: String(Date.now()),
        name: name || 'Unknown',
        phone: phone || 'Unknown',
        time: `Today, ${formattedTime}`,
        risk: risk,
        score: finalScore,
        status: status,
      };

      localStorage.setItem('voiceshield_call_history', JSON.stringify([newRecord, ...history]));
    } catch (err) {
      console.error('Failed to log call history:', err);
    }
  }, []);                          // ← stable

  const resetCallState = useCallback(() => {
    cleanupWebRTC();
    setCallState('idle');
    setIsIncomingCall(false);
    setIncomingCallData(null);
    setActivePeerName('');
    setActivePeerPhone('');
    setDuration(0);
    setVoiceAuth(98);
    setSpeakerMatch(98);
    setRiskScore(18);
    setIsMuted(false);
    alertTriggeredRef.current = false;
  }, [cleanupWebRTC]);             // ← stable (cleanupWebRTC is stable)

  // ──────────────────────────────────────────────────────────────────
  // Peer connection factory — stable (reads isMutedRef, not isMuted)
  // ──────────────────────────────────────────────────────────────────
  const getOrCreatePeerConnection = useCallback(async (peerPhone) => {
    if (initializingPcPromiseRef.current) {
      return await initializingPcPromiseRef.current;
    }
    if (pcRef.current && pcRef.current.signalingState !== 'closed') {
      return pcRef.current;
    }

    const initPromise = (async () => {
      try {
        console.log('[WebRTC] Creating new RTCPeerConnection for peer:', peerPhone);
        const pc = new RTCPeerConnection(ICE_SERVERS);
        pcRef.current = pc;

        // Obtain local mic stream if not already active
        if (!localStreamRef.current) {
          const stream = await navigator.mediaDevices.getUserMedia({
            audio: {
              echoCancellation: true,
              noiseSuppression: true,
              autoGainControl: true,
            },
            video: false,
          });
          localStreamRef.current = stream;
        }

        // Add local audio tracks to peer connection
        localStreamRef.current.getAudioTracks().forEach((track) => {
          track.enabled = !isMutedRef.current;     // ← read from ref
          pc.addTrack(track, localStreamRef.current);
        });

        // Send gathered ICE candidates via WebSocket
        pc.onicecandidate = (event) => {
          if (event.candidate && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
            wsRef.current.send(JSON.stringify({
              type: 'ice_candidate',
              candidate: event.candidate,
              to_phone: peerPhone || activePeerPhoneRef.current,
            }));
          }
        };

        // Handle incoming remote audio track
        pc.ontrack = (event) => {
          console.log('[WebRTC] Remote track received:', event.track.id, event.streams);
          const inboundStream = event.streams && event.streams[0]
            ? event.streams[0]
            : new MediaStream([event.track]);

          const audioElem = document.getElementById('voiceshield-remote-audio') || remoteAudioRef.current;
          if (audioElem) {
            audioElem.srcObject = inboundStream;
            audioElem.muted = false;
            audioElem.volume = 1.0;
            audioElem.play().catch((err) => {
              console.warn('[WebRTC] Audio auto-play prompt required:', err);
            });
          }
        };

        // Monitor ICE connection health and attempt recovery
        pc.oniceconnectionstatechange = () => {
          const state = pc.iceConnectionState;
          console.log('[WebRTC] ICE Connection State:', state);
          if (state === 'disconnected') {
            // Short-lived disconnections are normal — the browser will
            // attempt ICE restart automatically within ~5-10s.
            console.warn('[WebRTC] ICE disconnected — waiting for recovery...');
          }
          if (state === 'failed') {
            console.warn('[WebRTC] ICE failed — attempting ICE restart...');
            try {
              pc.restartIce();
            } catch (e) {
              console.error('[WebRTC] ICE restart failed:', e);
            }
          }
        };

        pc.onconnectionstatechange = () => {
          const state = pc.connectionState;
          console.log('[WebRTC] Connection State:', state);
          if (state === 'connected') {
            const audioElem = document.getElementById('voiceshield-remote-audio') || remoteAudioRef.current;
            if (audioElem && audioElem.srcObject) {
              audioElem.play().catch((e) => console.warn('[WebRTC] Play failed on connected:', e));
            }
          }
        };

        return pc;
      } finally {
        initializingPcPromiseRef.current = null;
      }
    })();

    initializingPcPromiseRef.current = initPromise;
    return await initPromise;
  }, []);                          // ← stable: zero deps (reads refs only)

  // ──────────────────────────────────────────────────────────────────
  // Stable WebRTC signaling helpers
  // ──────────────────────────────────────────────────────────────────
  const startCallerWebRTC = useCallback(async (peerPhone) => {
    try {
      console.log('[WebRTC] Starting Caller WebRTC flow for:', peerPhone);
      const pc = await getOrCreatePeerConnection(peerPhone);
      const offer = await pc.createOffer({
        offerToReceiveAudio: true,
        offerToReceiveVideo: false,
      });
      await pc.setLocalDescription(offer);

      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'webrtc_offer',
          offer: offer,
          to_phone: peerPhone,
        }));
      }
    } catch (err) {
      console.error('[WebRTC] Caller WebRTC setup failed:', err);
    }
  }, [getOrCreatePeerConnection]); // ← stable

  const handleWebRTCOffer = useCallback(async (offer, peerPhone) => {
    try {
      console.log('[WebRTC] Handling WebRTC Offer from:', peerPhone);
      const pc = await getOrCreatePeerConnection(peerPhone);

      await pc.setRemoteDescription(new RTCSessionDescription(offer));
      await processQueuedCandidates(pc);

      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'webrtc_answer',
          answer: answer,
          to_phone: peerPhone,
        }));
      }
    } catch (err) {
      console.error('[WebRTC] Failed handling WebRTC offer:', err);
    }
  }, [getOrCreatePeerConnection]); // ← stable

  const handleWebRTCAnswer = useCallback(async (answer) => {
    try {
      console.log('[WebRTC] Handling WebRTC Answer');
      const pc = pcRef.current;
      if (pc) {
        await pc.setRemoteDescription(new RTCSessionDescription(answer));
        await processQueuedCandidates(pc);
      }
    } catch (err) {
      console.error('[WebRTC] Failed setting remote description from answer:', err);
    }
  }, []);                          // ← stable

  const handleIceCandidate = useCallback(async (candidate) => {
    if (!candidate) return;
    const pc = pcRef.current;
    if (pc && pc.remoteDescription && pc.remoteDescription.type) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (err) {
        console.warn('[WebRTC] Error adding ICE candidate:', err);
      }
    } else {
      iceCandidatesQueueRef.current.push(candidate);
    }
  }, []);                          // ← stable

  // ──────────────────────────────────────────────────────────────────
  // User actions — some read refs to stay stable
  // ──────────────────────────────────────────────────────────────────

  const startDemoCall = useCallback((phone, name) => {
    const displayName = name || (phone === '000' ? 'VoiceShield Echo (Demo)' : 'Demo Session');
    setActivePeerName(displayName);
    setActivePeerPhone(phone || '000');
    setCallState('connected');

    navigator.mediaDevices.getUserMedia({ audio: true })
      .then((stream) => {
        localStreamRef.current = stream;
        const audioElem = document.getElementById('voiceshield-remote-audio') || remoteAudioRef.current;
        if (audioElem) {
          audioElem.srcObject = stream;
          audioElem.muted = true;
        }
      })
      .catch((err) => console.warn('Demo call mic capture failed:', err));
  }, []);

  const initiateCall = useCallback((targetPhone, targetName) => {
    if (!targetPhone) return;

    if (targetPhone === '000' || targetPhone === 'echo') {
      startDemoCall('000', 'VoiceShield Echo (Demo)');
      return;
    }

    const resolvedName = targetName || getContactNameByPhoneStable(targetPhone);
    setActivePeerPhone(targetPhone);
    setActivePeerName(resolvedName);
    setCallState('dialing');

    // Pre-request mic so user gesture authorizes permission immediately
    navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    }).then((stream) => {
      localStreamRef.current = stream;
    }).catch((err) => {
      console.warn('Microphone permission pre-fetch failed:', err);
    });

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'call_initiate',
        to_phone: targetPhone,
        from_name: myNameRef.current,        // ← read from ref
      }));
    }
  }, [getContactNameByPhoneStable, startDemoCall]);  // ← stable

  const answerCall = useCallback(async () => {
    const data = incomingCallDataRef.current;
    if (!data) return;

    const callerPhone = data.from_phone;
    const callerName = data.from_name || getContactNameByPhoneStable(callerPhone);

    setIsIncomingCall(false);
    setIncomingCallData(null);
    setActivePeerPhone(callerPhone);
    setActivePeerName(callerName);
    setCallState('connected');

    const audioElem = document.getElementById('voiceshield-remote-audio') || remoteAudioRef.current;
    if (audioElem) {
      audioElem.muted = false;
      audioElem.play().catch(() => {});
    }

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'call_status',
        status: 'accepted',
        to_phone: callerPhone,
      }));
    }

    try {
      await getOrCreatePeerConnection(callerPhone);
    } catch (err) {
      console.error('Failed to initialize callee peer connection:', err);
    }
  }, [getContactNameByPhoneStable, getOrCreatePeerConnection]); // ← stable

  const declineCall = useCallback(() => {
    const data = incomingCallDataRef.current;
    if (!data) return;

    setIsIncomingCall(false);
    setIncomingCallData(null);

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'call_status',
        status: 'declined',
        to_phone: data.from_phone,
      }));
    }
  }, []);

  const hangUp = useCallback(() => {
    const peerPhone = activePeerPhoneRef.current || (incomingCallDataRef.current ? incomingCallDataRef.current.from_phone : '');

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN && peerPhone) {
      wsRef.current.send(JSON.stringify({
        type: 'call_status',
        status: 'ended',
        to_phone: peerPhone,
      }));
    }

    saveCallToHistory(
      activePeerNameRef.current || 'Unknown',
      peerPhone,
      riskScoreRef.current,
      durationRef.current
    );

    resetCallState();
  }, [resetCallState, saveCallToHistory]); // ← stable

  const toggleMute = useCallback(() => {
    setIsMuted((prev) => {
      const next = !prev;
      if (localStreamRef.current) {
        localStreamRef.current.getAudioTracks().forEach((track) => {
          track.enabled = !next;
        });
      }
      return next;
    });
  }, []);

  // ──────────────────────────────────────────────────────────────────
  // WebSocket effect — depends ONLY on myPhone.
  //
  // All handler functions used inside are stable (zero-dep useCallbacks
  // that read refs instead of state). This guarantees the WebSocket and
  // peer connection are NEVER torn down because some unrelated state
  // (directory, isMuted, riskScore…) changed.
  // ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!myPhone) return;

    let isSubscribed = true;

    const connectWebSocket = () => {
      if (!isSubscribed) return;

      const cleanPhone = encodeURIComponent(myPhone);
      const wsBase = API_BASE.replace(/^http/, 'ws');
      const wsUrl = `${wsBase}/api/calls/ws/${cleanPhone}`;

      console.log('[WebSocket] Connecting signaling to:', wsUrl);
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log('[WebSocket] Connected successfully for phone:', myPhone);
      };

      ws.onmessage = async (event) => {
        try {
          const data = JSON.parse(event.data);
          console.log('[WebSocket] Message received:', data.type);

          switch (data.type) {
            case 'call_initiate':
              if (callStateRef.current === 'idle' && !isIncomingCallRef.current) {
                setIncomingCallData(data);
                setIsIncomingCall(true);
              } else {
                ws.send(JSON.stringify({
                  type: 'call_status',
                  status: 'busy',
                  to_phone: data.from_phone,
                }));
              }
              break;

            case 'call_status':
              if (data.status === 'offline') {
                setCallState('offline');
              } else if (data.status === 'busy') {
                alert('User is currently busy on another call.');
                resetCallState();
              } else if (data.status === 'accepted') {
                setCallState('connected');
                setActivePeerName(getContactNameByPhoneStable(data.from_phone));
                setActivePeerPhone(data.from_phone);
                await startCallerWebRTC(data.from_phone);
              } else if (data.status === 'declined') {
                alert('Call was declined.');
                resetCallState();
              } else if (data.status === 'ended') {
                saveCallToHistory(
                  activePeerNameRef.current,
                  activePeerPhoneRef.current,
                  riskScoreRef.current,
                  durationRef.current
                );
                resetCallState();
              }
              break;

            case 'webrtc_offer':
              setCallState('connected');
              setActivePeerPhone(data.from_phone);
              setActivePeerName(data.from_name || getContactNameByPhoneStable(data.from_phone));
              await handleWebRTCOffer(data.offer, data.from_phone);
              break;

            case 'webrtc_answer':
              await handleWebRTCAnswer(data.answer);
              break;

            case 'ice_candidate':
              await handleIceCandidate(data.candidate);
              break;

            default:
              break;
          }
        } catch (err) {
          console.error('[WebSocket] Message processing error:', err);
        }
      };

      ws.onclose = () => {
        console.log('[WebSocket] Connection closed.');
        // Only auto-reconnect if the component is still mounted
        // AND we are not in an active call (avoid disrupting calls
        // with a fresh WebSocket — the existing one just closed cleanly).
        if (isSubscribed) {
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, 3000);
        }
      };

      ws.onerror = (err) => {
        console.warn('[WebSocket] Error encountered:', err);
        ws.close();
      };
    };

    connectWebSocket();

    return () => {
      isSubscribed = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      // Only clean up WebRTC when the component truly unmounts
      // (i.e. user logs out), not on re-renders.
      cleanupWebRTC();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myPhone]);
  // ⬆ ALL handler refs are stable (proven zero-dep), so listing only
  //   myPhone is safe. This is the critical fix — previously the effect
  //   listed getContactNameByPhone (depends on directory) and
  //   getOrCreatePeerConnection (depended on isMuted) which caused the
  //   entire WS + WebRTC teardown when contacts loaded or mute toggled.

  // ──────────────────────────────────────────────────────────────────
  // Active call duration timer & metric fluctuation
  // ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (callState === 'connected') {
      setDuration(0);
      timerRef.current = setInterval(() => {
        setDuration((prev) => prev + 1);
      }, 1000);

      fluctuationRef.current = setInterval(() => {
        setVoiceAuth((prev) => {
          const delta = (Math.random() - 0.5) * 4;
          return Math.max(90, Math.min(99, Math.round(prev + delta)));
        });
        setSpeakerMatch((prev) => {
          const delta = (Math.random() - 0.5) * 3;
          return Math.max(92, Math.min(99, Math.round(prev + delta)));
        });
        setRiskScore((prev) => {
          const delta = (Math.random() - 0.5) * 5;
          return Math.max(10, Math.min(28, Math.round(prev + delta)));
        });
      }, 2500);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
      if (fluctuationRef.current) clearInterval(fluctuationRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (fluctuationRef.current) clearInterval(fluctuationRef.current);
    };
  }, [callState]);

  // ──────────────────────────────────────────────────────────────────
  // Security alert when risk crosses threshold
  // ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (callState !== 'connected') {
      alertTriggeredRef.current = false;
      return;
    }

    if (riskScore > 35 && !alertTriggeredRef.current) {
      alertTriggeredRef.current = true;
      const alertFn = onAddAlertRef.current;
      if (alertFn) {
        alertFn({
          severity: 'HIGH',
          message: `Elevated call risk detected: ${riskScore}% spoofing markers in call with ${activePeerName || activePeerPhone}.`,
          recommendation: 'Review credentials, do not share OTPs, and verify caller identity.',
        });
      }
    }
  }, [riskScore, callState, activePeerName, activePeerPhone]);

  // ──────────────────────────────────────────────────────────────────
  // Context value
  // ──────────────────────────────────────────────────────────────────
  const value = {
    callState,
    setCallState,
    isIncomingCall,
    incomingCallData,
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
    answerCall,
    declineCall,
    hangUp,
    toggleMute,
    resetCallState,
    startDemoCall,
    remoteAudioRef,
  };

  return (
    <CallContext.Provider value={value}>
      {children}
    </CallContext.Provider>
  );
}

export function useCall() {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error('useCall must be used within a CallProvider');
  }
  return context;
}

export default CallContext;
