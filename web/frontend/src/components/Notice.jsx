import { useEffect } from 'react';

const VISIBLE_MS = 4000;

export default function Notice({ text, onDone }) {
  useEffect(() => {
    if (!text) return undefined;
    const timer = setTimeout(onDone, VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [text, onDone]);

  if (!text) return null;

  return (
    <div className="notice" role="status">
      {text}
    </div>
  );
}
