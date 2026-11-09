import { useCallback, useEffect, useMemo, useState } from 'react';
import { useChat } from '../useChat';
import { useCall } from '../useCall';
import Sidebar from './Sidebar';
import Conversation from './Conversation';
import NewGroupDialog from './NewGroupDialog';
import GroupInfoDialog from './GroupInfoDialog';
import CallOverlay from './CallOverlay';

export default function Chat({ session, onSignOut }) {
  const chat = useChat(session, onSignOut);
  const call = useCall({ token: session.token, send: chat.send });
  const [selected, setSelected] = useState(null);
  const [groupDialog, setGroupDialog] = useState(false);
  const [infoGroupId, setInfoGroupId] = useState(null);

  useEffect(() => chat.onCallFrame(call.handleFrame), [chat.onCallFrame, call.handleFrame]);

  useEffect(() => {
    if (selected?.isGroup && !chat.groups.some((g) => g.id === selected.id)) {
      setSelected(null);
    }
  }, [selected, chat.groups]);

  const select = useCallback(
    (id, isGroup) => {
      setSelected({ id, isGroup });
      chat.openChat(id, isGroup);
    },
    [chat]
  );

  useEffect(() => chat.onOpenRequest(select), [chat.onOpenRequest, select]);

  const infoGroup = useMemo(
    () => (infoGroupId ? chat.groups.find((g) => g.id === infoGroupId) : null),
    [infoGroupId, chat.groups]
  );

  const title = useMemo(() => {
    if (!selected) return null;
    if (selected.isGroup) {
      return chat.groups.find((g) => g.id === selected.id)?.name || selected.id;
    }
    return chat.users[selected.id]?.displayName || selected.id;
  }, [selected, chat.groups, chat.users]);

  return (
    <div className={`app ${selected ? 'has-selection' : ''}`}>
      <Sidebar
        me={session.user}
        token={session.token}
        connection={chat.connection}
        users={chat.users}
        groups={chat.groups}
        mutes={chat.mutes}
        chats={chat.chats}
        unread={chat.unread}
        typing={chat.typing}
        selected={selected}
        onSelect={select}
        onNewGroup={() => setGroupDialog(true)}
        onSignOut={onSignOut}
      />

      {selected ? (
        <Conversation
          key={selected.id}
          session={session}
          chat={chat}
          target={selected}
          title={title}
          onBack={() => setSelected(null)}
          onCall={call.supported && !selected.isGroup
            ? (video) => call.startCall(selected.id, video)
            : null}
          callBusy={Boolean(call.call)}
          onOpenGroupInfo={() => setInfoGroupId(selected.id)}
        />
      ) : (
        <div className="empty-pane">
          <div>
            <h2>WhatsApp Clone</h2>
            <p>Pick a conversation to start messaging.</p>
            <p className="hint">
              Messages travel over the Erlang server; accounts, history and media live in the
              Java backend.
            </p>
          </div>
        </div>
      )}

      {groupDialog && (
        <NewGroupDialog
          users={chat.users}
          onCancel={() => setGroupDialog(false)}
          onCreate={(name, members) => {
            chat.createGroup(name, members);
            setGroupDialog(false);
          }}
        />
      )}

      {infoGroup && (
        <GroupInfoDialog
          group={infoGroup}
          users={chat.users}
          me={session.user.username}
          token={session.token}
          onClose={() => setInfoGroupId(null)}
          onAddMembers={(members) => chat.addMembers(infoGroup.id, members)}
          onSetAdmin={(user, admin) => chat.setAdmin(infoGroup.id, user, admin)}
          onRemoveMember={(user) => chat.removeMember(infoGroup.id, user)}
          onLeave={() => chat.leaveGroup(infoGroup.id)}
        />
      )}

      <CallOverlay
        call={call.call}
        remoteStream={call.remoteStream}
        localStream={call.localStream}
        micOn={call.micOn}
        camOn={call.camOn}
        displayName={call.call ? chat.users[call.call.peer]?.displayName : null}
        onAccept={call.accept}
        onDecline={call.decline}
        onHangUp={call.hangUp}
        onToggleMic={call.toggleMic}
        onToggleCam={call.toggleCam}
      />

      {chat.error && (
        <div className="toast" role="status" onClick={chat.dismissError}>
          {chat.error}
        </div>
      )}

      {call.callError && (
        <div className="toast" role="status" onClick={call.dismissCallError}>
          {call.callError}
        </div>
      )}
    </div>
  );
}
