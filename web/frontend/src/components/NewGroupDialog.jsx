import { useState } from 'react';
import { initials } from '../format';

export default function NewGroupDialog({ users, onCancel, onCreate }) {
  const [name, setName] = useState('');
  const [picked, setPicked] = useState([]);

  const candidates = Object.entries(users);

  function toggle(username) {
    setPicked((prev) =>
      prev.includes(username) ? prev.filter((u) => u !== username) : [...prev, username]);
  }

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>New group</h2>

        <label>
          Group name
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Weekend plans" />
        </label>

        <p className="modal-label">Members</p>
        <ul className="member-list">
          {candidates.length === 0 && <li className="chat-empty">No other accounts yet.</li>}
          {candidates.map(([username, info]) => (
            <li key={username}>
              <label className="member">
                <input
                  type="checkbox"
                  checked={picked.includes(username)}
                  onChange={() => toggle(username)}
                />
                <span className="avatar small">{initials(info.displayName || username)}</span>
                <span>{info.displayName || username}</span>
              </label>
            </li>
          ))}
        </ul>

        <div className="modal-actions">
          <button className="link" onClick={onCancel}>Cancel</button>
          <button
            className="primary"
            disabled={!name.trim() || picked.length === 0}
            onClick={() => onCreate(name.trim(), picked)}
          >
            Create
          </button>
        </div>
      </div>
    </div>
  );
}
