let audioCtx = null;

export function requestNotifyPermission() {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission().catch(() => {});
  }
}

export function beep() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const now = audioCtx.currentTime;
    for (const [freq, at] of [[880, 0], [1174.66, 0.09]]) {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.001, now + at);
      gain.gain.exponentialRampToValueAtTime(0.12, now + at + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.001, now + at + 0.09);
      osc.connect(gain).connect(audioCtx.destination);
      osc.start(now + at);
      osc.stop(now + at + 0.1);
    }
  } catch {
  }
}

export function notifyMessage({ title, body, chatId, isGroup, onOpen }) {
  beep();
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  try {
    const n = new Notification(title, { body, tag: `waclone-${chatId}`, silent: true });
    n.onclick = () => {
      window.focus();
      onOpen?.(chatId, isGroup);
      n.close();
    };
  } catch {
  }
}
