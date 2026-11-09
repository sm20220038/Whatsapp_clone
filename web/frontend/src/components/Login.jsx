import { useState } from 'react';
import { api } from '../api';

export default function Login({ onSignIn }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const registering = mode === 'register';

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = registering
        ? await api.register(username.trim(), displayName.trim(), password)
        : await api.login(username.trim(), password);
      onSignIn(result, registering ? 'Account created' : 'Signed in');
    } catch (e) {
      setError(e.message || 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-brand">
          <svg viewBox="0 0 24 24" width="42" height="42" aria-hidden="true">
            <path
              fill="#25d366"
              d="M12 2a10 10 0 0 0-8.6 15.06L2 22l5.06-1.35A10 10 0 1 0 12 2Zm5.5 14.2c-.24.66-1.4 1.27-1.94 1.32-.5.05-.98.23-3.3-.69-2.77-1.09-4.53-3.92-4.67-4.1-.13-.18-1.1-1.47-1.1-2.8 0-1.33.7-1.98.94-2.25.25-.27.54-.34.72-.34h.52c.17 0 .4-.06.62.48.24.57.8 1.98.87 2.12.07.14.12.3.02.48-.1.18-.15.29-.3.45l-.44.5c-.14.14-.29.3-.12.59.16.28.73 1.2 1.57 1.95 1.08.96 1.98 1.26 2.27 1.4.28.14.45.12.61-.07.17-.2.7-.81.89-1.09.18-.28.37-.23.62-.14.25.09 1.6.75 1.87.89.28.14.46.2.53.32.07.11.07.66-.17 1.32Z"
            />
          </svg>
          <h1>WhatsApp Clone</h1>
        </div>
        <p className="login-tagline">
          {registering ? 'Create an account to start chatting.' : 'Sign in to continue.'}
        </p>

        <form onSubmit={submit}>
          <label>
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoCapitalize="none"
              placeholder="alice"
              required
            />
          </label>

          {registering && (
            <label>
              Display name
              <input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Alice"
              />
            </label>
          )}

          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={registering ? 'new-password' : 'current-password'}
              placeholder="••••••••"
              required
            />
          </label>

          {error && <p className="login-error">{error}</p>}

          <button type="submit" className="primary" disabled={busy}>
            {busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <button
          type="button"
          className="link"
          onClick={() => { setMode(registering ? 'login' : 'register'); setError(null); }}
        >
          {registering ? 'I already have an account' : 'Create a new account'}
        </button>
      </div>
    </div>
  );
}
