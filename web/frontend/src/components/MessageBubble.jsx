import { useState } from 'react';
import { mediaUrl } from '../api';
import { fileSize } from '../format';

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏'];

export default function MessageBubble({ message, token, isGroup, me, onReply, onReact,
                                        onEdit, onDelete }) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const outgoing = message.outgoing ?? message.from === me;
  const reactions = Object.entries(message.reactions || {});

  if (message.deleted) {
    return (
      <div className={`bubble-row ${outgoing ? 'out' : 'in'}`}>
        <div className={`bubble ${outgoing ? 'outgoing' : 'incoming'} deleted`}>
          <div className="bubble-text">🚫 Message was deleted</div>
          <div className="bubble-meta">
            <time>{new Date(message.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`bubble-row ${outgoing ? 'out' : 'in'}`}>
      <div className={`bubble ${outgoing ? 'outgoing' : 'incoming'}`}>
        {isGroup && !outgoing && <div className="bubble-author">{message.from}</div>}

        {message.reply && (
          <div className="quote">
            <span className="quote-from">{message.reply.from}</span>
            <span className="quote-text">{message.reply.text}</span>
          </div>
        )}

        {message.kind === 'media' ? (
          <Attachment media={message.media} token={token} />
        ) : (
          <div className="bubble-text">{message.text}</div>
        )}

        <div className="bubble-meta">
          {message.editedAt > 0 && <span className="edited-tag">edited</span>}
          <time>{new Date(message.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
          {outgoing && <Ticks status={message.status} />}
        </div>

        {reactions.length > 0 && (
          <div className="reaction-chips">
            {reactions.map(([who, emoji]) => (
              <span key={who} className="reaction-chip" title={who}>{emoji}</span>
            ))}
          </div>
        )}

        <div className="bubble-actions">
          <button onClick={onReply} title="Reply">↩</button>
          <button onClick={() => setPickerOpen((v) => !v)} title="React">☺</button>
          {outgoing && message.kind !== 'media' && (
            <button onClick={onEdit} title="Edit">✎</button>
          )}
          {outgoing && <button onClick={onDelete} title="Delete for everyone">🗑</button>}
        </div>

        {pickerOpen && (
          <div className="emoji-pop">
            {QUICK_REACTIONS.map((emoji) => (
              <button
                key={emoji}
                onClick={() => { onReact(emoji); setPickerOpen(false); }}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function Attachment({ media, token }) {
  if (!media?.mediaId) return <div className="bubble-text">📎 Attachment unavailable</div>;

  const url = mediaUrl(token, media.mediaId);
  const name = media.fileName || 'file';
  const mime = media.mime || '';
  const isImage = mime.startsWith('image/') || /\.(png|jpe?g|gif|webp)$/i.test(name);
  const isAudio = mime.startsWith('audio/') || /\.(mp3|ogg|wav)$/i.test(name)
    || /^voice-\d+\.webm$/i.test(name);

  const isVideo = mime.startsWith('video/') || /\.(mp4|webm)$/i.test(name);

  if (isAudio) {
    return (
      <div className="attachment voice">
        <audio src={url} controls preload="metadata" />
        {media.duration > 0 && <small>{formatDuration(media.duration)}</small>}
      </div>
    );
  }

  if (isImage) {
    return (
      <a className="attachment image" href={url} target="_blank" rel="noreferrer">
        <img src={url} alt={name} loading="lazy" />
      </a>
    );
  }
  if (isVideo) {
    return <video className="attachment" src={url} controls preload="metadata" />;
  }
  return (
    <a className="attachment file" href={url} target="_blank" rel="noreferrer" download={name}>
      <span className="file-icon">📄</span>
      <span className="file-info">
        <strong>{name}</strong>
        <small>{fileSize(media.size)}</small>
      </span>
    </a>
  );
}

function formatDuration(seconds) {
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

function Ticks({ status }) {
  if (status === 'pending' || !status) return <span className="ticks">🕘</span>;
  const read = status === 'read';
  const double = status === 'delivered' || read;
  return <span className={`ticks ${read ? 'read' : ''}`}>{double ? '✔✔' : '✔'}</span>;
}
