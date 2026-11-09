import { API } from './config';

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(path, { method = 'GET', body, token, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';

  const res = await fetch(API + path, {
    method,
    headers,
    body: form || (body ? JSON.stringify(body) : undefined),
  });

  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.message || data?.error || text || res.statusText);
  }
  return data;
}

export const api = {
  register: (username, displayName, password) =>
    request('/auth/register', { method: 'POST', body: { username, displayName, password } }),

  login: (username, password) =>
    request('/auth/login', { method: 'POST', body: { username, password } }),

  users: (token) => request('/users', { token }),

  me: (token) => request('/me', { token }),

  updateProfile: (token, profile) => request('/me', { method: 'PUT', token, body: profile }),

  history: (token, { peer, group, before, limit }) => {
    const q = new URLSearchParams(peer ? { peer } : { group });
    if (before) q.set('before', String(before));
    if (limit) q.set('limit', String(limit));
    return request(`/messages?${q}`, { token });
  },

  editMessage: (token, body) => request('/messages/edit', { method: 'POST', token, body }),

  deleteMessage: (token, body) => request('/messages/delete', { method: 'POST', token, body }),

  saveMessage: (token, message) => request('/messages', { method: 'POST', token, body: message }),

  saveReaction: (token, reaction) =>
    request('/messages/react', { method: 'POST', token, body: reaction }),

  saveStatus: (token, status) =>
    request('/messages/status', { method: 'POST', token, body: status }),

  syncGroup: (token, group) => request('/groups', { method: 'POST', token, body: group }),

  logCall: (token, entry) => request('/calls', { method: 'POST', token, body: entry }),

  callHistory: (token) => request('/calls', { token }),

  upload: (token, file, onProgress) =>
    new Promise((resolve, reject) => {
      const form = new FormData();
      form.append('file', file);
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${API}/media`);
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText));
        } else {
          reject(new ApiError(xhr.status, xhr.responseText || 'upload failed'));
        }
      };
      xhr.onerror = () => reject(new ApiError(0, 'network error during upload'));
      xhr.send(form);
    }),
};

export const mediaUrl = (token, id) => `${API}/media/${id}?token=${encodeURIComponent(token)}`;
