export function initials(name) {
  const parts = String(name || '?').trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

export function lastSeenLabel(lastSeen) {
  if (!lastSeen) return 'offline';
  const date = new Date(lastSeen);
  const sameDay = new Date().toDateString() === date.toDateString();
  return sameDay
    ? `last seen ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
    : `last seen ${date.toLocaleDateString()}`;
}

export function preview(message) {
  if (message.kind === 'media') return `📎 ${message.media?.fileName || 'Attachment'}`;
  return message.text;
}

export function fileSize(bytes) {
  if (bytes == null) return '';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i += 1;
  }
  return `${value < 10 && i > 0 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

export function dayLabel(ts) {
  const date = new Date(ts);
  const today = new Date();
  const yesterday = new Date(today.getTime() - 86400000);
  if (date.toDateString() === today.toDateString()) return 'Today';
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}
