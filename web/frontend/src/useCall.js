import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from './api';
import { ICE_SERVERS } from './config';

const RING_TIMEOUT_MS = 45000;

export function useCall({ token, send }) {
  const [call, setCall] = useState(null);
  const [remoteStream, setRemoteStream] = useState(null);
  const [localStream, setLocalStream] = useState(null);
  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [callError, setCallError] = useState(null);

  const pc = useRef(null);
  const local = useRef(null);
  const pendingIce = useRef([]);
  const pendingOffer = useRef(null);
  const peer = useRef(null);
  const startedAt = useRef(0);
  const ringTimer = useRef(null);
  const logged = useRef(false);

  const callDirection = useRef(null);
  const callVideo = useRef(false);

  const record = useCallback(
    (status) => {
      if (logged.current || !peer.current || callDirection.current !== 'out') return;
      logged.current = true;
      const wasActive = startedAt.current > 0;
      api
        .logCall(token, {
          peer: peer.current,
          outgoing: true,
          type: callVideo.current ? 'video' : 'voice',
          status,
          durationMs: wasActive ? Date.now() - startedAt.current : 0,
        })
        .catch(() => {});
    },
    [token]
  );

  const cleanup = useCallback(() => {
    clearTimeout(ringTimer.current);
    ringTimer.current = null;

    if (pc.current) {
      pc.current.onicecandidate = null;
      pc.current.ontrack = null;
      pc.current.onconnectionstatechange = null;
      pc.current.close();
      pc.current = null;
    }
    if (local.current) {
      local.current.getTracks().forEach((t) => t.stop());
      local.current = null;
    }
    pendingIce.current = [];
    pendingOffer.current = null;
    peer.current = null;
    callDirection.current = null;
    callVideo.current = false;
    startedAt.current = 0;

    setLocalStream(null);
    setRemoteStream(null);
    setMicOn(true);
    setCamOn(true);
    setCall(null);
  }, []);

  const finish = useCallback(
    (status) => {
      record(status);
      cleanup();
    },
    [record, cleanup]
  );

  const getMedia = useCallback(async (video) => {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error(
        window.isSecureContext
          ? 'This browser has no camera/microphone support.'
          : 'Calls need HTTPS. Open the app on localhost, or serve it over HTTPS.'
      );
    }
    return navigator.mediaDevices.getUserMedia({
      audio: true,
      video: video ? { width: { ideal: 1280 }, height: { ideal: 720 } } : false,
    });
  }, []);

  const createPeer = useCallback(
    (other, stream) => {
      const connection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

      stream.getTracks().forEach((track) => connection.addTrack(track, stream));

      connection.onicecandidate = (event) => {
        if (event.candidate) {
          send({ type: 'call_ice', to: other, candidate: event.candidate.toJSON() });
        }
      };
      connection.ontrack = (event) => {
        setRemoteStream(event.streams[0]);
      };
      connection.onconnectionstatechange = () => {
        if (connection.connectionState === 'failed') {
          setCallError('Could not establish a direct connection.');
          finish('ended');
        }
      };

      pc.current = connection;
      local.current = stream;
      setLocalStream(stream);
      return connection;
    },
    [send, finish]
  );

  const goActive = useCallback(() => {
    clearTimeout(ringTimer.current);
    startedAt.current = Date.now();
    setCall((prev) => (prev ? { ...prev, state: 'active', since: Date.now() } : prev));
  }, []);

  const startCall = useCallback(
    async (other, video) => {
      if (call) return;
      setCallError(null);
      logged.current = false;
      peer.current = other;
      callDirection.current = 'out';
      callVideo.current = video;
      setCall({ peer: other, video, direction: 'out', state: 'dialing' });

      try {
        const stream = await getMedia(video);
        const connection = createPeer(other, stream);
        const offer = await connection.createOffer();
        await connection.setLocalDescription(offer);
        send({ type: 'call_offer', to: other, sdp: connection.localDescription, video });

        ringTimer.current = setTimeout(() => {
          send({ type: 'call_end', to: other });
          setCallError('No answer');
          finish('missed');
        }, RING_TIMEOUT_MS);
      } catch (e) {
        setCallError(e.message || 'Could not start the call');
        cleanup();
      }
    },
    [call, getMedia, createPeer, send, finish, cleanup]
  );

  const accept = useCallback(async () => {
    const offer = pendingOffer.current;
    const other = peer.current;
    if (!offer || !other) return;

    try {
      const stream = await getMedia(callVideo.current);
      const connection = createPeer(other, stream);
      await connection.setRemoteDescription(new RTCSessionDescription(offer));

      for (const candidate of pendingIce.current) {
        await connection.addIceCandidate(new RTCIceCandidate(candidate)).catch(() => {});
      }
      pendingIce.current = [];

      const answer = await connection.createAnswer();
      await connection.setLocalDescription(answer);
      send({ type: 'call_answer', to: other, sdp: connection.localDescription });

      pendingOffer.current = null;
      goActive();
    } catch (e) {
      setCallError(e.message || 'Could not answer the call');
      send({ type: 'call_reject', to: other, reason: 'failed' });
      finish('missed');
    }
  }, [getMedia, createPeer, send, goActive, finish]);

  const decline = useCallback(() => {
    if (peer.current) send({ type: 'call_reject', to: peer.current, reason: 'declined' });
    finish('rejected');
  }, [send, finish]);

  const hangUp = useCallback(() => {
    if (peer.current) send({ type: 'call_end', to: peer.current });
    finish(startedAt.current > 0 ? 'ended' : 'missed');
  }, [send, finish]);

  const handleFrame = useCallback(
    async (frame) => {
      switch (frame.type) {
        case 'call_offer': {
          if (call || pendingOffer.current) {
            send({ type: 'call_reject', to: frame.from, reason: 'busy' });
            return;
          }
          logged.current = false;
          peer.current = frame.from;
          callDirection.current = 'in';
          callVideo.current = Boolean(frame.video);
          pendingOffer.current = frame.sdp;
          setCallError(null);
          setCall({
            peer: frame.from,
            video: Boolean(frame.video),
            direction: 'in',
            state: 'ringing',
          });
          ringTimer.current = setTimeout(() => finish('missed'), RING_TIMEOUT_MS);
          break;
        }

        case 'call_answer': {
          if (!pc.current) return;
          await pc.current
            .setRemoteDescription(new RTCSessionDescription(frame.sdp))
            .catch(() => {});
          for (const candidate of pendingIce.current) {
            await pc.current.addIceCandidate(new RTCIceCandidate(candidate)).catch(() => {});
          }
          pendingIce.current = [];
          goActive();
          break;
        }

        case 'call_ice': {
          if (pc.current?.remoteDescription) {
            await pc.current.addIceCandidate(new RTCIceCandidate(frame.candidate)).catch(() => {});
          } else {
            pendingIce.current.push(frame.candidate);
          }
          break;
        }

        case 'call_reject':
          setCallError(frame.reason === 'busy' ? 'They are on another call' : 'Call declined');
          finish('rejected');
          break;

        case 'call_end':
          finish(startedAt.current > 0 ? 'ended' : 'missed');
          break;

        case 'call_failed':
          setCallError(`${frame.user} is not available.`);
          finish('missed');
          break;

        default:
          break;
      }
    },
    [call, send, goActive, finish]
  );

  const toggleMic = useCallback(() => {
    const track = local.current?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setMicOn(track.enabled);
  }, []);

  const toggleCam = useCallback(() => {
    const track = local.current?.getVideoTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setCamOn(track.enabled);
  }, []);

  useEffect(() => cleanup, [cleanup]);

  return useMemo(
    () => ({
      call, remoteStream, localStream, micOn, camOn, callError,
      startCall, accept, decline, hangUp, handleFrame, toggleMic, toggleCam,
      dismissCallError: () => setCallError(null),
      supported: typeof RTCPeerConnection !== 'undefined',
    }),
    [call, remoteStream, localStream, micOn, camOn, callError,
     startCall, accept, decline, hangUp, handleFrame, toggleMic, toggleCam]
  );
}
