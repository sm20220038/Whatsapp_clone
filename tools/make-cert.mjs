import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { networkInterfaces } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(
  join(dirname(fileURLToPath(import.meta.url)), '..', 'web', 'frontend', 'package.json'));
const selfsigned = require('selfsigned');

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dir = join(root, 'certs');

const ips = Object.values(networkInterfaces())
  .flat()
  .filter((i) => i && i.family === 'IPv4' && !i.internal)
  .map((i) => i.address);

const altNames = [
  { type: 2, value: 'localhost' },
  { type: 7, ip: '127.0.0.1' },
  ...ips.map((ip) => ({ type: 7, ip })),
];

const pems = await selfsigned.generate(
  [{ name: 'commonName', value: 'whatsapp-clone-dev' }],
  {
    days: 825,
    keySize: 2048,
    algorithm: 'sha256',
    extensions: [
      { name: 'basicConstraints', cA: false },
      { name: 'keyUsage', digitalSignature: true, keyEncipherment: true },
      { name: 'extKeyUsage', serverAuth: true },
      { name: 'subjectAltName', altNames },
    ],
  }
);

mkdirSync(dir, { recursive: true });
writeFileSync(join(dir, 'dev.key'), pems.private);
writeFileSync(join(dir, 'dev.crt'), pems.cert);

const forge = require('node-forge');
const p12Asn1 = forge.pkcs12.toPkcs12Asn1(
  forge.pki.privateKeyFromPem(pems.private),
  [forge.pki.certificateFromPem(pems.cert)],
  'waclone-dev',
  { algorithm: '3des', friendlyName: 'waclone' }
);
writeFileSync(join(dir, 'dev.p12'), Buffer.from(forge.asn1.toDer(p12Asn1).getBytes(), 'binary'));

console.log(`certs written to ${dir}`);
console.log(`SANs: localhost, 127.0.0.1${ips.length ? ', ' + ips.join(', ') : ''}`);
