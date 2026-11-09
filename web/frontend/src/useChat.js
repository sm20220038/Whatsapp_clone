import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { WS_URL } from './config';
import { api } from './api';
import { notifyMessage, requestNotifyPermission } from './notify';

const PAGE_SIZE = 50;

export function useChat(session, onAuthError) {
  const { token, user: me } = session;

  const [connection, setConnection] = useState('connecting');
  const [users, setUsers] = useState({});
  const [groups, setGroups] = useState([]);
  const [mutes, setMutes] = useState([]);
  const [chats, setChats] = useState({});
  const [typing, setTyping] = useState({});
  const [unread, setUnread] = useState({});
  const [error, setError] = useState(null);

  const socket = useRef(null);
  const activeChat = useRef(null);
  const loaded = useRef(new Set());
  const exhausted = useRef(new Set());
  const chatsRef = useRef({});
  const replaced = useRef(false);
  const callHandler = useRef(null);
  const openChatRef = useRef(null);
  const groupIds = useRef(new Set());

  const send = useCallback((frame) => {
    const ws = socket.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(frame));
      return true;
    }
    return false;
  }, []);

  const appendMessage = useCallback((chatId, message) => {
    setChats((prev) => {
      const list = prev[chatId] || [];
      if (list.some((m) => m.id === message.id)) return prev;
      return { ...prev, [chatId]: [...list, message].sort((a, b) => a.ts - b.ts) };
    });
  }, []);

  const patchMessage = useCallback((chatId, id, patch) => {
    setChats((prev) => {
      const list = prev[chatId];
      if (!list) return prev;
      return {
        ...prev,
        [chatId]: list.map((m) => (m.id === id ? { ...m, ...patch } : m)),
      };
    });
  }, []);

  const handle = useCallback(
    (frame) => {
      switch (frame.type) {
        case 'login_ok': {
          setConnection('online');
          setUsers((prev) => {
            const next = { ...prev };
            for (const u of frame.users || []) {
              next[u.user] = { ...next[u.user], online: u.online, lastSeen: u.last_seen };
            }
            return next;
          });
          setGroups(frame.groups || []);
          setMutes(frame.mutes || []);
          break;
        }

        case 'presence':
          setUsers((prev) => ({
            ...prev,
            [frame.user]: { ...prev[frame.user], online: frame.online, lastSeen: frame.last_seen },
          }));
          break;

        case 'msg':
        case 'gmsg':
        case 'media': {
          const chatId = frame.group || frame.from;
          const isGroup = Boolean(frame.group);
          appendMessage(chatId, {
            id: frame.id,
            from: frame.from,
            text: frame.text || '',
            ts: frame.ts,
            outgoing: false,
            kind: frame.type === 'media' ? 'media' : 'text',
            media: frame.type === 'media'
              ? {
                  mediaId: frame.media_id,
                  fileName: frame.file_name,
                  size: frame.size,
                  mime: frame.mime,
                  duration: frame.duration,
                }
              : null,
            reply: frame.reply_to
              ? { id: frame.reply_to, text: frame.reply_text, from: frame.reply_from }
              : null,
            reactions: {},
          });

          if (!isGroup) send({ type: 'ack_delivered', to: frame.from, id: frame.id });
          const looking = activeChat.current === chatId && !document.hidden;
          if (looking) {
            if (!isGroup) send({ type: 'ack_read', to: frame.from, id: frame.id });
          } else {
            setUnread((prev) => ({ ...prev, [chatId]: (prev[chatId] || 0) + 1 }));
            notifyMessage({
              title: isGroup ? `${frame.from} · ${frame.group_name || 'group'}` : frame.from,
              body: frame.type === 'media' ? `📎 ${frame.file_name || 'Attachment'}` : frame.text,
              chatId,
              isGroup,
              onOpen: (id, group) => openChatRef.current?.(id, group),
            });
          }
          break;
        }

        case 'receipt': {
          patchMessage(frame.peer, frame.id, { status: frame.status });

          if (frame.status === 'delivered' || frame.status === 'read') {
            const isGroup = groupIds.current.has(frame.peer);
            api.saveStatus(token, {
              clientId: frame.id,
              ...(isGroup ? { group: frame.peer } : { peer: frame.peer }),
              status: frame.status,
            }).catch(() => {});
          }
          break;
        }

        case 'typing': {
          const chatId = frame.group || frame.from;
          setTyping((prev) => ({ ...prev, [chatId]: { who: frame.from, at: Date.now() } }));
          break;
        }

        case 'msg_edit':
        case 'msg_delete': {
          const chatId = frame.group || frame.from;
          setChats((prev) => {
            const list = prev[chatId];
            if (!list) return prev;
            return {
              ...prev,
              [chatId]: list.map((m) => {
                if (m.id !== frame.target || m.from !== frame.from) return m;
                return frame.type === 'msg_edit'
                  ? { ...m, text: frame.text, editedAt: frame.ts }
                  : { ...m, deleted: true, text: '', media: null, reactions: {} };
              }),
            };
          });
          break;
        }

        case 'reaction': {
          const chatId = frame.group || frame.from;
          setChats((prev) => {
            const list = prev[chatId];
            if (!list) return prev;
            return {
              ...prev,
              [chatId]: list.map((m) => {
                if (m.id !== frame.target) return m;
                const reactions = { ...m.reactions };
                if (frame.op === 'remove') delete reactions[frame.from];
                else reactions[frame.from] = frame.emoji;
                return { ...m, reactions };
              }),
            };
          });
          break;
        }

        case 'group_created':
        case 'group_updated': {
          const group = {
            id: frame.id,
            name: frame.name,
            members: frame.members,
            admins: frame.admins || [],
            owner: frame.owner || null,
          };

          if (!group.members.includes(me.username)) {
            setGroups((prev) => prev.filter((g) => g.id !== group.id));
            setChats((prev) => {
              const { [group.id]: _gone, ...rest } = prev;
              return rest;
            });
            setUnread((prev) => ({ ...prev, [group.id]: 0 }));
            loaded.current.delete(group.id);
            if (frame.action === 'removed') {
              setError(`You were removed from ${group.name}`);
            }
            break;
          }

          setGroups((prev) =>
            prev.some((g) => g.id === group.id)
              ? prev.map((g) => (g.id === group.id ? group : g))
              : [...prev, group]);

          if (frame.by === me.username) api.syncGroup(token, group).catch(() => {});
          break;
        }

        case 'mute_ok':
          setMutes((prev) =>
            frame.muted ? [...new Set([...prev, frame.user])] : prev.filter((u) => u !== frame.user));
          break;

        case 'call_offer':
        case 'call_answer':
        case 'call_ice':
        case 'call_reject':
        case 'call_end':
        case 'call_failed':
          callHandler.current?.(frame);
          break;

        case 'session_replaced':
          replaced.current = true;
          setConnection('replaced');
          setError('This account was opened somewhere else. Reload to use it here.');
          break;

        case 'error':
          if (frame.reason === 'invalid token') onAuthError?.();
          else setError(frame.reason);
          break;

        default:
          break;
      }
    },
    [appendMessage, patchMessage, send, onAuthError, token, me.username]
  );

  useEffect(() => {
    let disposed = false;
    let timer = null;
    let current = null;
    let attempt = 0;

    const open = () => {
      if (disposed) return;
      const ws = new WebSocket(WS_URL);
      current = ws;
      socket.current = ws;

      ws.onopen = () => {
        attempt = 0;
        ws.send(JSON.stringify({ type: 'login', token }));
      };
      ws.onmessage = (event) => {
        try {
          handle(JSON.parse(event.data));
        } catch {
        }
      };
      ws.onclose = () => {
        if (disposed || current !== ws || replaced.current) return;
        setConnection('offline');

        const delay = Math.min(1000 * 2 ** attempt, 15000);
        attempt += 1;
        timer = setTimeout(open, delay);
      };
      ws.onerror = () => ws.close();
    };

    open();
    return () => {
      disposed = true;
      clearTimeout(timer);
      current?.close();
    };
  }, [token, handle]);

  useEffect(() => {
    const id = setInterval(() => {
      setTyping((prev) => {
        const now = Date.now();
        const next = {};
        let changed = false;
        for (const [chatId, value] of Object.entries(prev)) {
          if (now - value.at < 3000) next[chatId] = value;
          else changed = true;
        }
        return changed ? next : prev;
      });
    }, 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    api
      .users(token)
      .then((list) => {
        setUsers((prev) => {
          const next = { ...prev };
          for (const u of list) {
            next[u.username] = {
              ...next[u.username],
              displayName: u.displayName,
              avatarId: u.avatarId || null,
              lastSeen: next[u.username]?.lastSeen ?? u.lastSeen,
              online: next[u.username]?.online ?? false,
            };
          }
          return next;
        });
      })
      .catch((e) => {
        if (e.status === 401) onAuthError?.();
      });
  }, [token, onAuthError]);

  useEffect(() => {
    requestNotifyPermission();
  }, []);

  useEffect(() => {
    groupIds.current = new Set(groups.map((g) => g.id));
  }, [groups]);

  useEffect(() => {
    chatsRef.current = chats;
  }, [chats]);

  const fromHistory = useCallback(
    (m) => ({
      id: m.id,
      from: m.from,
      text: m.text || '',
      ts: m.ts,
      outgoing: m.from === me.username,
      status: m.status,
      editedAt: m.editedAt || 0,
      deleted: Boolean(m.deleted),
      kind: m.type === 'image' || m.type === 'file' ? 'media' : 'text',
      media: m.storagePath
        ? { mediaId: m.storagePath, fileName: m.fileName, size: m.fileSizeBytes, mime: m.mime }
        : null,
      reactions: m.reactions || {},
    }),
    [me.username]
  );

  const mergeHistory = useCallback((chatId, page) => {
    setChats((prev) => {
      const merged = [...(prev[chatId] || [])];
      for (const m of page) {
        if (!merged.some((x) => x.id === m.id)) merged.push(m);
      }
      merged.sort((a, b) => a.ts - b.ts);
      return { ...prev, [chatId]: merged };
    });
  }, []);

  const loadOlder = useCallback(
    async (chatId, isGroup) => {
      if (exhausted.current.has(chatId)) return 0;
      const current = chatsRef.current[chatId] || [];
      const before = current.length ? current[0].ts : undefined;
      try {
        const page = await api.history(token, {
          ...(isGroup ? { group: chatId } : { peer: chatId }),
          before,
          limit: PAGE_SIZE,
        });
        if (page.length < PAGE_SIZE) exhausted.current.add(chatId);
        if (page.length) mergeHistory(chatId, page.map(fromHistory));
        return page.length;
      } catch {
        return 0;
      }
    },
    [token, mergeHistory, fromHistory]
  );

  const openChat = useCallback(
    async (chatId, isGroup) => {
      activeChat.current = chatId;
      setUnread((prev) => ({ ...prev, [chatId]: 0 }));

      if (!loaded.current.has(chatId)) {
        loaded.current.add(chatId);
        try {
          const page = await api.history(token, {
            ...(isGroup ? { group: chatId } : { peer: chatId }),
            limit: PAGE_SIZE,
          });
          if (page.length < PAGE_SIZE) exhausted.current.add(chatId);
          mergeHistory(chatId, page.map(fromHistory));
        } catch (e) {
          if (e.status === 401) onAuthError?.();
        }
      }

      const list = chats[chatId] || [];
      for (const m of list) {
        if (!isGroup && !m.outgoing && m.status !== 'read') {
          send({ type: 'ack_read', to: chatId, id: m.id });
        }
      }
    },
    [token, me.username, chats, send, onAuthError]
  );

  const sendText = useCallback(
    (chatId, isGroup, text, reply) => {
      const id = crypto.randomUUID();
      const frame = {
        type: isGroup ? 'gmsg' : 'msg',
        [isGroup ? 'group' : 'to']: chatId,
        id,
        text,
      };
      if (reply) {
        frame.reply_to = reply.id;
        frame.reply_text = reply.text;
        frame.reply_from = reply.from;
      }
      if (!send(frame)) {
        setError('Not connected, message not sent');
        return;
      }
      appendMessage(chatId, {
        id, from: me.username, text, ts: Date.now(), outgoing: true,
        status: 'pending', kind: 'text', reply, reactions: {},
      });

      const group = groups.find((g) => g.id === chatId);
      api
        .saveMessage(token, {
          clientId: id,
          ...(isGroup ? { group: chatId, groupName: group?.name, members: group?.members }
                      : { to: chatId }),
          text,
          replyTo: reply?.id,
        })
        .catch(() => {});
    },
    [send, appendMessage, me.username, token, groups]
  );

  const sendMedia = useCallback(
    async (chatId, isGroup, file, onProgress, extra = {}) => {
      const uploaded = await api.upload(token, file, onProgress);
      const id = crypto.randomUUID();
      const frame = {
        type: 'media',
        [isGroup ? 'group' : 'to']: chatId,
        id,
        media_id: uploaded.id,
        file_name: uploaded.fileName,
        size: uploaded.size,
        mime: uploaded.contentType,
        sha256: uploaded.sha256,
        ...(extra.duration ? { duration: extra.duration } : {}),
      };
      if (!send(frame)) throw new Error('Not connected');

      appendMessage(chatId, {
        id, from: me.username, text: '', ts: Date.now(), outgoing: true,
        status: 'pending', kind: 'media', reactions: {},
        media: {
          mediaId: uploaded.id, fileName: uploaded.fileName,
          size: uploaded.size, mime: uploaded.contentType,
          duration: extra.duration,
        },
      });

      const group = groups.find((g) => g.id === chatId);
      api
        .saveMessage(token, {
          clientId: id,
          ...(isGroup ? { group: chatId, groupName: group?.name, members: group?.members }
                      : { to: chatId }),
          text: '',
          type: (uploaded.contentType || '').startsWith('image/') ? 'image' : 'file',
          fileName: uploaded.fileName,
          fileSizeBytes: uploaded.size,
          storagePath: uploaded.id,
          sha256: uploaded.sha256,
        })
        .catch(() => {});
    },
    [token, send, appendMessage, me.username, groups]
  );

  const react = useCallback(
    (chatId, isGroup, messageId, emoji) => {
      const list = chats[chatId] || [];
      const message = list.find((m) => m.id === messageId);
      const had = message?.reactions?.[me.username] === emoji;

      send({
        type: 'react',
        [isGroup ? 'group' : 'to']: chatId,
        target: messageId,
        emoji,
        op: had ? 'remove' : 'add',
      });

      setChats((prev) => {
        const current = prev[chatId];
        if (!current) return prev;
        return {
          ...prev,
          [chatId]: current.map((m) => {
            if (m.id !== messageId) return m;
            const reactions = { ...m.reactions };
            if (had) delete reactions[me.username];
            else reactions[me.username] = emoji;
            return { ...m, reactions };
          }),
        };
      });

      api
        .saveReaction(token, {
          clientId: messageId,
          ...(isGroup ? { group: chatId } : { to: chatId }),
          emoji,
        })
        .catch(() => {});
    },
    [chats, me.username, send, token]
  );

  const editMessage = useCallback(
    (chatId, isGroup, messageId, text) => {
      send({ type: 'msg_edit', [isGroup ? 'group' : 'to']: chatId, target: messageId, text });
      setChats((prev) => {
        const list = prev[chatId];
        if (!list) return prev;
        return {
          ...prev,
          [chatId]: list.map((m) =>
            m.id === messageId ? { ...m, text, editedAt: Date.now() } : m),
        };
      });
      api.editMessage(token, {
        clientId: messageId,
        ...(isGroup ? { group: chatId } : { peer: chatId }),
        text,
      }).catch(() => {});
    },
    [send, token]
  );

  const deleteMessage = useCallback(
    (chatId, isGroup, messageId) => {
      send({ type: 'msg_delete', [isGroup ? 'group' : 'to']: chatId, target: messageId });
      setChats((prev) => {
        const list = prev[chatId];
        if (!list) return prev;
        return {
          ...prev,
          [chatId]: list.map((m) =>
            m.id === messageId
              ? { ...m, deleted: true, text: '', media: null, reactions: {} }
              : m),
        };
      });
      api.deleteMessage(token, {
        clientId: messageId,
        ...(isGroup ? { group: chatId } : { peer: chatId }),
      }).catch(() => {});
    },
    [send, token]
  );

  const notifyTyping = useCallback(
    (chatId, isGroup) => send({ type: 'typing', [isGroup ? 'group' : 'to']: chatId }),
    [send]
  );

  const createGroup = useCallback(
    (name, members) => send({ type: 'create_group', name, members }),
    [send]
  );

  const addMembers = useCallback(
    (group, members) => send({ type: 'add_members', group, members }),
    [send]
  );

  const setAdmin = useCallback(
    (group, user, admin) => send({ type: 'set_admin', group, user, admin }),
    [send]
  );

  const removeMember = useCallback(
    (group, user) => send({ type: 'remove_member', group, user }),
    [send]
  );

  const leaveGroup = useCallback(
    (group) => send({ type: 'leave_group', group }),
    [send]
  );

  const toggleMute = useCallback(
    (username) => send({ type: mutes.includes(username) ? 'unmute' : 'mute', user: username }),
    [send, mutes]
  );

  const dismissError = useCallback(() => setError(null), []);

  const onCallFrame = useCallback((fn) => {
    callHandler.current = fn;
  }, []);

  const onOpenRequest = useCallback((fn) => {
    openChatRef.current = fn;
  }, []);

  return useMemo(
    () => ({
      connection, users, groups, mutes, chats, typing, unread, error,
      openChat, sendText, sendMedia, react, notifyTyping, createGroup, toggleMute, dismissError,
      addMembers, setAdmin, removeMember, leaveGroup, send, onCallFrame, onOpenRequest,
      editMessage, deleteMessage, loadOlder,
    }),
    [connection, users, groups, mutes, chats, typing, unread, error,
     openChat, sendText, sendMedia, react, notifyTyping, createGroup, toggleMute, dismissError,
     addMembers, setAdmin, removeMember, leaveGroup, send, onCallFrame, onOpenRequest,
     editMessage, deleteMessage, loadOlder]
  );
}
