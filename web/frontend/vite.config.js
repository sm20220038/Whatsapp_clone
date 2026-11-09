import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const certDir = resolve(__dirname, '../../certs');

export default defineConfig(({ mode }) => {
  const secure = mode === 'https';
  if (secure && !existsSync(resolve(certDir, 'dev.key'))) {
    throw new Error('certs/ missing — run node tools/make-cert.mjs first');
  }
  return {
    plugins: [react()],
    server: {
      port: secure ? 5174 : 5173,
      https: secure
        ? {
            key: readFileSync(resolve(certDir, 'dev.key')),
            cert: readFileSync(resolve(certDir, 'dev.crt')),
          }
        : undefined,
    },
  };
});
