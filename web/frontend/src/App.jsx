import { useCallback, useState } from 'react';
import Login from './components/Login';
import Chat from './components/Chat';
import Notice from './components/Notice';

const STORAGE_KEY = 'waclone.session';

function loadSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export default function App() {
  const [session, setSession] = useState(loadSession);
  const [notice, setNotice] = useState(null);

  const signIn = useCallback((next, message) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setSession(next);
    setNotice(message);
  }, []);

  const signOut = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
    setNotice('Signed out');
  }, []);

  const clearNotice = useCallback(() => setNotice(null), []);

  return (
    <>
      <Notice text={notice} onDone={clearNotice} />
      {session ? (
        <Chat key={session.user.username} session={session} onSignOut={signOut} />
      ) : (
        <Login onSignIn={signIn} />
      )}
    </>
  );
}
