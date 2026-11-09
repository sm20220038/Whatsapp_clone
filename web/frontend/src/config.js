const host = window.location.hostname || 'localhost';
const secure = window.location.protocol === 'https:';

export const API = import.meta.env.VITE_API
  || (secure ? `https://${host}:8444/api` : `http://${host}:8080/api`);
export const WS_URL = import.meta.env.VITE_WS
  || (secure ? `wss://${host}:8443/` : `ws://${host}:8081/`);

export const ICE_SERVERS = (() => {
  const raw = import.meta.env.VITE_ICE_SERVERS;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    console.warn('VITE_ICE_SERVERS is not valid JSON — continuing without STUN');
    return [];
  }
})();
