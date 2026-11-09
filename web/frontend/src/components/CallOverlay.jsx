import { useEffect, useRef, useState } from 'react';
import { initials } from '../format';

export default function CallOverlay({ call, remoteStream, localStream, micOn, camOn,
                                      displayName, onAccept, onDecline, onHangUp,
                                      onToggleMic, onToggleCam }) {
  const remoteVideo = useRef(null);
  const localVideo = useRef(null);
  const remoteAudio = useRef(null);

  useEffect(() => {
    if (remoteVideo.current) remoteVideo.current.srcObject = remoteStream || null;
    if (remoteAudio.current) remoteAudio.current.srcObject = remoteStream || null;
  }, [remoteStream, call?.state, call?.video]);

  useEffect(() => {
    if (localVideo.current) localVideo.current.srcObject = localStream || null;
  }, [localStream, call?.state]);

  if (!call) return null;

  const ringingIn = call.state === 'ringing';
  const name = displayName || call.peer;

  if (ringingIn) {
    return (
      <div className="call-backdrop">
        <div className="call-sheet">
          <div className="avatar call-avatar">{initials(name)}</div>
          <h2>{name}</h2>
          <p className="call-status">
            Incoming {call.video ? 'video' : 'voice'} call…
          </p>
          <div className="call-actions">
            <button className="call-btn decline" onClick={onDecline} aria-label="Decline">✕</button>
            <button className="call-btn accept" onClick={onAccept} aria-label="Accept">
              {call.video ? '🎥' : '📞'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  const active = call.state === 'active';

  return (
    <div className="call-backdrop">
      <div className={`call-window ${call.video ? 'video' : 'voice'}`}>
        <audio ref={remoteAudio} autoPlay playsInline />

        {call.video ? (
          <>
            <video ref={remoteVideo} className="remote-video" autoPlay playsInline />
            {!remoteStream && (
              <div className="call-waiting">
                <div className="avatar call-avatar">{initials(name)}</div>
                <h2>{name}</h2>
                <p className="call-status">{active ? 'Connecting video…' : 'Ringing…'}</p>
              </div>
            )}
            <video ref={localVideo} className="local-video" autoPlay playsInline muted />
          </>
        ) : (
          <div className="call-waiting">
            <div className="avatar call-avatar">{initials(name)}</div>
            <h2>{name}</h2>
            <p className="call-status">
              {active ? <Duration since={call.since} /> : 'Ringing…'}
            </p>
          </div>
        )}

        {call.video && active && (
          <div className="call-timer"><Duration since={call.since} /></div>
        )}

        <div className="call-controls">
          <button
            className={`call-btn ${micOn ? '' : 'off'}`}
            onClick={onToggleMic}
            title={micOn ? 'Mute microphone' : 'Unmute microphone'}
          >
            {micOn ? '🎙' : '🔇'}
          </button>
          {call.video && (
            <button
              className={`call-btn ${camOn ? '' : 'off'}`}
              onClick={onToggleCam}
              title={camOn ? 'Turn camera off' : 'Turn camera on'}
            >
              {camOn ? '🎥' : '📷'}
            </button>
          )}
          <button className="call-btn decline" onClick={onHangUp} title="Hang up">✕</button>
        </div>
      </div>
    </div>
  );
}

function Duration({ since }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  if (!since) return '00:00';
  const total = Math.max(0, Math.floor((now - since) / 1000));
  const mm = String(Math.floor(total / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}
