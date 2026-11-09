import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api';
import { lastSeenLabel, preview } from '../format';
import Avatar from './Avatar';

export default function Sidebar({
  me, token, connection, users, groups, mutes, chats, unread, typing,
  selected, onSelect, onNewGroup, onSignOut,
}) {
  const [query, setQuery] = useState('');
  const [myAvatar, setMyAvatar] = useState(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const avatarInput = useRef(null);

  useEffect(() => {
    api.me(token).then((profile) => setMyAvatar(profile.avatarId || null)).catch(() => {});
  }, [token]);

  async function pickAvatar(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    setMenuOpen(false);
    if (!file || !file.type.startsWith('image/')) return;
    try {
      const uploaded = await api.upload(token, file);
      const profile = await api.updateProfile(token, { avatarId: uploaded.id });
      setMyAvatar(profile.avatarId);
    } catch {
    }
  }

  async function clearAvatar() {
    setMenuOpen(false);
    try {
      await api.updateProfile(token, { avatarId: '' });
      setMyAvatar(null);
    } catch {}
  }

  const rows = useMemo(() => {
    const list = [];

    for (const g of groups) {
      list.push({
        id: g.id,
        isGroup: true,
        name: g.name,
        subtitle: `${g.members.length} members`,
        members: g.members,
      });
    }
    for (const [username, info] of Object.entries(users)) {
      list.push({
        id: username,
        isGroup: false,
        name: info.displayName || username,
        subtitle: info.online ? 'online' : lastSeenLabel(info.lastSeen),
        online: info.online,
        avatarId: info.avatarId,
        muted: mutes.includes(username),
      });
    }

    const needle = query.trim().toLowerCase();
    const filtered = needle
      ? list.filter((r) => r.name.toLowerCase().includes(needle) || r.id.toLowerCase().includes(needle))
      : list;

    return filtered.sort((a, b) => {
      const at = lastTs(chats[a.id]);
      const bt = lastTs(chats[b.id]);
      if (at !== bt) return bt - at;
      return a.name.localeCompare(b.name);
    });
  }, [users, groups, mutes, chats, query]);

  return (
    <aside className="sidebar">
      <header className="sidebar-head">
        <button
          className="avatar-btn"
          onClick={() => setMenuOpen((v) => !v)}
          title="Profile"
          aria-label="Profile"
        >
          <Avatar name={me.displayName || me.username} avatarId={myAvatar} token={token} variant="me" />
        </button>
        <div className="sidebar-me">
          <strong>{me.displayName || me.username}</strong>
          <span className={`conn conn-${connection}`}>
            {{
              online: 'connected',
              connecting: 'connecting…',
              replaced: 'open in another tab',
            }[connection] || 'reconnecting…'}
          </span>
        </div>
        <button className="icon" title="New group" onClick={onNewGroup} aria-label="New group">＋</button>
        <button className="icon" title="Sign out" onClick={onSignOut} aria-label="Sign out">⏻</button>

        {menuOpen && (
          <div className="profile-menu">
            <button onClick={() => { setMenuOpen(false); avatarInput.current?.click(); }}>
              {myAvatar ? 'Change photo' : 'Add photo'}
            </button>
            {myAvatar && <button onClick={clearAvatar}>Remove photo</button>}
          </div>
        )}
        <input ref={avatarInput} type="file" accept="image/*" hidden onChange={pickAvatar} />
      </header>

      <div className="search">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search or start a new chat"
        />
      </div>

      <ul className="chat-list">
        {rows.length === 0 && <li className="chat-empty">Nobody here yet — register a second account to chat with.</li>}
        {rows.map((row) => {
          const messages = chats[row.id] || [];
          const last = messages[messages.length - 1];
          const isTyping = Boolean(typing[row.id]);
          const count = unread[row.id] || 0;
          return (
            <li key={row.id}>
              <button
                className={`chat-row ${selected?.id === row.id ? 'active' : ''}`}
                onClick={() => onSelect(row.id, row.isGroup)}
              >
                <Avatar
                  name={row.name}
                  avatarId={row.avatarId}
                  token={token}
                  isGroup={row.isGroup}
                  online={row.online}
                />
                <div className="chat-row-body">
                  <div className="chat-row-top">
                    <span className="chat-name">
                      {row.name}
                      {row.muted && <span className="muted-badge" title="Muted">🔇</span>}
                    </span>
                    {last && <time>{new Date(last.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>}
                  </div>
                  <div className="chat-row-bottom">
                    <span className="chat-preview">
                      {isTyping ? <em className="typing">typing…</em> : last ? preview(last) : row.subtitle}
                    </span>
                    {count > 0 && <span className="badge">{count}</span>}
                  </div>
                </div>
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}

function lastTs(messages) {
  return messages && messages.length ? messages[messages.length - 1].ts : 0;
}
