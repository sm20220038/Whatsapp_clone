import { useMemo, useState } from 'react';
import Avatar from './Avatar';

export default function GroupInfoDialog({ group, users, me, token, onClose, onAddMembers,
                                          onSetAdmin, onRemoveMember, onLeave }) {
  const [picking, setPicking] = useState(false);
  const [picked, setPicked] = useState([]);
  const [confirmLeave, setConfirmLeave] = useState(false);

  const admins = group.admins || [];
  const owner = group.owner || null;
  const iAmAdmin = admins.includes(me);

  const roster = useMemo(
    () =>
      [...group.members].sort((a, b) => {
        const rank = (u) => (u === owner ? 0 : admins.includes(u) ? 1 : 2);
        return rank(a) - rank(b) || a.localeCompare(b);
      }),
    [group.members, admins, owner]
  );

  const candidates = useMemo(
    () => Object.keys(users).filter((u) => !group.members.includes(u)).sort(),
    [users, group.members]
  );

  const nameOf = (username) =>
    username === me ? 'You' : users[username]?.displayName || username;

  function toggle(username) {
    setPicked((prev) =>
      prev.includes(username) ? prev.filter((u) => u !== username) : [...prev, username]);
  }

  function confirmAdd() {
    onAddMembers(picked);
    setPicked([]);
    setPicking(false);
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="group-head">
          <div className="avatar group large">#</div>
          <div>
            <h2>{group.name}</h2>
            <p className="modal-label">{group.members.length} members</p>
          </div>
        </div>

        {picking ? (
          <>
            <p className="modal-label">Select users to add</p>
            {candidates.length === 0 ? (
              <p className="chat-empty">Everyone you know is already in.</p>
            ) : (
              <ul className="member-list">
                {candidates.map((username) => (
                  <li key={username}>
                    <label className="member">
                      <input
                        type="checkbox"
                        checked={picked.includes(username)}
                        onChange={() => toggle(username)}
                      />
                      <Avatar name={nameOf(username)} avatarId={users[username]?.avatarId}
                              token={token} variant="small" />
                      <span>{nameOf(username)}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
            <div className="modal-actions">
              <button className="link" onClick={() => { setPicking(false); setPicked([]); }}>
                Cancel
              </button>
              <button className="primary" disabled={picked.length === 0} onClick={confirmAdd}>
                Add to this group{picked.length > 0 ? ` (${picked.length})` : ''}
              </button>
            </div>
          </>
        ) : (
          <>
            <ul className="member-list roster">
              {roster.map((username) => {
                const isOwner = username === owner;
                const isAdmin = admins.includes(username);
                return (
                  <li key={username} className="roster-row">
                    <Avatar name={nameOf(username)} avatarId={users[username]?.avatarId}
                            token={token} variant="small" />
                    <span className="roster-name">{nameOf(username)}</span>
                    {isOwner && <span className="role-badge owner">owner</span>}
                    {!isOwner && isAdmin && <span className="role-badge">admin</span>}
                    {iAmAdmin && !isOwner && username !== me && (
                      <>
                        <button
                          className="link inline"
                          onClick={() => onSetAdmin(username, !isAdmin)}
                        >
                          {isAdmin ? 'Dismiss as admin' : 'Make admin'}
                        </button>
                        <button
                          className="link inline danger"
                          onClick={() => onRemoveMember(username)}
                        >
                          Remove
                        </button>
                      </>
                    )}
                  </li>
                );
              })}
            </ul>

            {confirmLeave ? (
              <div className="modal-actions">
                <span className="modal-label">
                  {group.owner === me
                    ? 'Leave? Ownership passes to another member.'
                    : 'Leave this group?'}
                </span>
                <button className="link" onClick={() => setConfirmLeave(false)}>Stay</button>
                <button className="primary danger" onClick={() => { onLeave(); onClose(); }}>
                  Leave
                </button>
              </div>
            ) : (
              <div className="modal-actions">
                <button className="link" onClick={onClose}>Close</button>
                <button className="link danger" onClick={() => setConfirmLeave(true)}>
                  Leave group
                </button>
                {iAmAdmin ? (
                  <button className="primary" onClick={() => setPicking(true)}>Add members</button>
                ) : (
                  <span className="modal-label">Only admins can add members.</span>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
