import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { EMOJI_GROUPS } from '../emoji';

const MAX_BYTES = 500 * 1024 * 1024;

export default function Composer({ replyTo, editing, onCancelReply, onCancelEdit,
                                   onSend, onTyping, onSendFile }) {
  const [text, setText] = useState('');
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState(null);
  const [emojiOpen, setEmojiOpen] = useState(false);
  const fileInput = useRef(null);
  const textInput = useRef(null);
  const lastTyping = useRef(0);
  const pendingCaret = useRef(null);
  const [recording, setRecording] = useState(false);
  const [recSeconds, setRecSeconds] = useState(0);
  const mediaRecorder = useRef(null);
  const recTimer = useRef(null);
  const recCount = useRef(0);
  const cancelRec = useRef(false);

  useEffect(() => {
    if (editing) {
      setText(editing.text || '');
      textInput.current?.focus();
    }
  }, [editing]);

  useEffect(() => {
    if (!emojiOpen) return;
    const close = (e) => {
      if (!e.target.closest('.emoji-panel') && !e.target.closest('.emoji-toggle')) {
        setEmojiOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [emojiOpen]);

  function submit(event) {
    event.preventDefault();
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText('');
    setEmojiOpen(false);
  }

  function change(event) {
    setText(event.target.value);

    const now = Date.now();
    if (now - lastTyping.current > 1000) {
      lastTyping.current = now;
      onTyping();
    }
  }

  function insertEmoji(emoji) {
    const input = textInput.current;
    const at = input?.selectionStart ?? text.length;
    setText((prev) => prev.slice(0, at) + emoji + prev.slice(at));
    pendingCaret.current = at + emoji.length;
  }

  useLayoutEffect(() => {
    if (pendingCaret.current === null) return;
    const pos = pendingCaret.current;
    pendingCaret.current = null;
    const input = textInput.current;
    input?.focus();
    input?.setSelectionRange(pos, pos);
  }, [text]);

  async function startRecording() {
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
      setError(window.isSecureContext
        ? 'This browser cannot record audio.'
        : 'Recording needs HTTPS. Open the app on localhost, or over HTTPS.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });

      const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : '';
      const recorder = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      const chunks = [];
      recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        clearInterval(recTimer.current);
        const cancelled = cancelRec.current;
        const seconds = recCount.current;
        setRecording(false);
        setRecSeconds(0);
        cancelRec.current = false;
        if (cancelled || chunks.length === 0) return;

        const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
        const file = new File([blob], `voice-${Date.now()}.webm`, { type: blob.type });
        setProgress(0);
        try {
          await onSendFile(file, setProgress, { duration: seconds });
        } catch (e) {
          setError(e.message || 'Could not send the voice message');
        } finally {
          setProgress(null);
        }
      };
      mediaRecorder.current = recorder;
      recorder.start();
      setError(null);
      setRecording(true);
      recCount.current = 0;
      setRecSeconds(0);
      recTimer.current = setInterval(() => {
        recCount.current += 1;
        setRecSeconds(recCount.current);
      }, 1000);
    } catch (e) {
      setError(e.message || 'Microphone unavailable');
    }
  }

  function stopRecording(cancel) {
    cancelRec.current = cancel;
    mediaRecorder.current?.stop();
  }

  async function pickFile(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (file.size > MAX_BYTES) {
      setError('That file is over the 500 MB limit');
      return;
    }
    setError(null);
    setProgress(0);
    try {
      await onSendFile(file, setProgress);
    } catch (e) {
      setError(e.message || 'Upload failed');
    } finally {
      setProgress(null);
    }
  }

  return (
    <div className="composer-wrap">
      {editing && (
        <div className="reply-bar">
          <div>
            <span className="quote-from">Editing message</span>
            <span className="quote-text">{editing.text}</span>
          </div>
          <button
            className="icon"
            onClick={() => { onCancelEdit(); setText(''); }}
            aria-label="Cancel edit"
          >
            ✕
          </button>
        </div>
      )}

      {replyTo && !editing && (
        <div className="reply-bar">
          <div>
            <span className="quote-from">{replyTo.from}</span>
            <span className="quote-text">{replyTo.text || replyTo.media?.fileName}</span>
          </div>
          <button className="icon" onClick={onCancelReply} aria-label="Cancel reply">✕</button>
        </div>
      )}

      {progress !== null && (
        <div className="upload-bar">
          <div className="upload-fill" style={{ width: `${Math.round(progress * 100)}%` }} />
          <span>Uploading… {Math.round(progress * 100)}%</span>
        </div>
      )}

      {error && <div className="composer-error" onClick={() => setError(null)}>{error}</div>}

      {emojiOpen && (
        <div className="emoji-panel">
          {EMOJI_GROUPS.map((group) => (
            <div key={group.name}>
              <p className="emoji-group-name">{group.name}</p>
              <div className="emoji-grid">
                {group.emoji.map((e) => (
                  <button key={e} type="button" onClick={() => insertEmoji(e)}>{e}</button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {recording ? (
        <div className="composer recording">
          <span className="rec-dot" />
          <span className="rec-time">{formatSeconds(recSeconds)}</span>
          <span className="rec-hint">Recording a voice message…</span>
          <button type="button" className="icon" onClick={() => stopRecording(true)} title="Cancel">
            🗑
          </button>
          <button type="button" className="send" onClick={() => stopRecording(false)} aria-label="Send voice message">
            ➤
          </button>
        </div>
      ) : (
      <form className="composer" onSubmit={submit}>
        <button
          type="button"
          className={`icon emoji-toggle ${emojiOpen ? 'active' : ''}`}
          onClick={() => setEmojiOpen((v) => !v)}
          title="Emoji"
          aria-label="Emoji"
        >
          😊
        </button>
        <button
          type="button"
          className="icon"
          onClick={() => fileInput.current?.click()}
          title="Attach a file"
          aria-label="Attach a file"
        >
          📎
        </button>
        <input ref={fileInput} type="file" hidden onChange={pickFile} />
        <input
          ref={textInput}
          className="composer-input"
          value={text}
          onChange={change}
          placeholder={editing ? 'Edit your message' : 'Type a message'}
          autoComplete="off"
        />
        {editing ? (
          <button type="submit" className="send" disabled={!text.trim()} aria-label="Save edit">✓</button>
        ) : text.trim() ? (
          <button type="submit" className="send" aria-label="Send">➤</button>
        ) : (
          <button
            type="button"
            className="send"
            onClick={startRecording}
            title="Record a voice message"
            aria-label="Record a voice message"
          >
            🎙
          </button>
        )}
      </form>
      )}
    </div>
  );
}

function formatSeconds(total) {
  const mm = String(Math.floor(total / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}
