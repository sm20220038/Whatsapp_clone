import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import Composer from './Composer';
import MessageBubble from './MessageBubble';
import Avatar from './Avatar';
import { dayLabel, lastSeenLabel } from '../format';

export default function Conversation({ session, chat, target, title, onBack, onCall, callBusy,
                                       onOpenGroupInfo }) {
  const { id, isGroup } = target;
  const messages = chat.chats[id] || [];
  const [replyTo, setReplyTo] = useState(null);
  const [editing, setEditing] = useState(null);
  const [atStart, setAtStart] = useState(false);
  const scroller = useRef(null);
  const stickToBottom = useRef(true);
  const loadingOlder = useRef(false);
  const anchorHeight = useRef(null);

  const peer = isGroup ? null : chat.users[id];
  const group = isGroup ? chat.groups.find((g) => g.id === id) : null;
  const muted = !isGroup && chat.mutes.includes(id);
  const typingHint = chat.typing[id];

  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;

    if (anchorHeight.current !== null) {
      el.scrollTop += el.scrollHeight - anchorHeight.current;
      anchorHeight.current = null;
      loadingOlder.current = false;
      return;
    }
    if (stickToBottom.current) el.scrollTop = el.scrollHeight;
  }, [messages.length, typingHint]);

  async function onScroll() {
    const el = scroller.current;
    if (!el) return;
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;

    if (el.scrollTop < 120 && !loadingOlder.current && !atStart) {
      loadingOlder.current = true;
      anchorHeight.current = el.scrollHeight;
      const added = await chat.loadOlder(id, isGroup);
      if (added === 0) {
        setAtStart(true);
        anchorHeight.current = null;
        loadingOlder.current = false;
      }
    }
  }

  const withDays = useMemo(() => {
    const out = [];
    let lastDay = null;
    for (const m of messages) {
      const day = dayLabel(m.ts);
      if (day !== lastDay) {
        out.push({ divider: true, id: `day-${day}-${m.ts}`, day });
        lastDay = day;
      }
      out.push(m);
    }
    return out;
  }, [messages]);

  const subtitle = isGroup
    ? group?.members?.join(', ')
    : typingHint
      ? 'typing…'
      : peer?.online
        ? 'online'
        : lastSeenLabel(peer?.lastSeen);

  return (
    <section className="conversation">
      <header className="conv-head">
        <button className="icon back" onClick={onBack} aria-label="Back">‹</button>
        {isGroup ? (
          <button className="conv-identity" onClick={onOpenGroupInfo} title="Group info">
            <Avatar name={title} isGroup />
            <div className="conv-title">
              <strong>{title}</strong>
              <span className={typingHint ? 'typing' : ''}>{subtitle}</span>
            </div>
          </button>
        ) : (
          <>
            <Avatar name={title} avatarId={peer?.avatarId} token={session.token} online={peer?.online} />
            <div className="conv-title">
              <strong>{title}</strong>
              <span className={typingHint ? 'typing' : ''}>{subtitle}</span>
            </div>
          </>
        )}
        {onCall && (
          <>
            <button
              className="icon"
              title={`Voice call ${title}`}
              disabled={callBusy}
              onClick={() => onCall(false)}
            >
              📞
            </button>
            <button
              className="icon"
              title={`Video call ${title}`}
              disabled={callBusy}
              onClick={() => onCall(true)}
            >
              🎥
            </button>
          </>
        )}
        {!isGroup && (
          <button
            className={`icon ${muted ? 'active' : ''}`}
            title={muted ? 'Unmute this person' : 'Mute this person'}
            onClick={() => chat.toggleMute(id)}
          >
            {muted ? '🔇' : '🔔'}
          </button>
        )}
      </header>

      {muted && (
        <div className="muted-banner">
          You muted {title}. Their messages are dropped by the server and never reach you.
        </div>
      )}

      <div className="messages" ref={scroller} onScroll={onScroll}>
        {atStart && withDays.length > 0 && (
          <p className="history-start">This is the beginning of the conversation.</p>
        )}
        {withDays.length === 0 && (
          <p className="no-messages">No messages yet. Say hello 👋</p>
        )}
        {withDays.map((item) =>
          item.divider ? (
            <div key={item.id} className="day-chip">{item.day}</div>
          ) : (
            <MessageBubble
              key={item.id}
              message={item}
              token={session.token}
              isGroup={isGroup}
              me={session.user.username}
              onReply={() => setReplyTo(item)}
              onReact={(emoji) => chat.react(id, isGroup, item.id, emoji)}
              onEdit={() => { setEditing(item); setReplyTo(null); }}
              onDelete={() => chat.deleteMessage(id, isGroup, item.id)}
            />
          )
        )}
        {typingHint && (
          <div className="bubble incoming typing-bubble">
            <span className="typing-dots"><i /><i /><i /></span>
          </div>
        )}
      </div>

      <Composer
        replyTo={replyTo}
        editing={editing}
        onCancelReply={() => setReplyTo(null)}
        onCancelEdit={() => setEditing(null)}
        onSend={(text) => {
          if (editing) {
            chat.editMessage(id, isGroup, editing.id, text);
            setEditing(null);
            return;
          }
          chat.sendText(id, isGroup, text, replyTo && {
            id: replyTo.id, text: replyTo.text || replyTo.media?.fileName || '', from: replyTo.from,
          });
          setReplyTo(null);
        }}
        onTyping={() => chat.notifyTyping(id, isGroup)}
        onSendFile={(file, onProgress, extra) => chat.sendMedia(id, isGroup, file, onProgress, extra)}
      />
    </section>
  );
}
