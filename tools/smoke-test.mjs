const API = 'http://localhost:8080/api';
const WS = 'ws://localhost:8081/';
const ok = (label, cond, extra = '') =>
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${label}${extra ? ' :: ' + extra : ''}`);

const n = Date.now().toString().slice(-6);
const A = `proba${n}`, B = `proba2${n}`;

const call = async (path, { method = 'GET', body, token, form } = {}) => {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(API + path, { method, headers, body: form || (body ? JSON.stringify(body) : undefined) });
  const text = await res.text();
  let json = null; try { json = JSON.parse(text); } catch {}
  return { status: res.status, json, text };
};
const connect = (token) => new Promise((resolve) => {
  const ws = new WebSocket(WS);
  const frames = [];
  ws.onopen = () => ws.send(JSON.stringify({ type: 'login', token }));
  ws.onmessage = (m) => {
    const f = JSON.parse(m.data);
    frames.push(f);
    if (f.type === 'login_ok') resolve({ ws, frames, send: (o) => ws.send(JSON.stringify(o)) });
  };
});
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const last = (p, t) => p.frames.filter((f) => f.type === t).at(-1);

let r = await call('/auth/register', { method: 'POST', body: { username: A, displayName: 'Proba', password: 'lozinka123' } });
ok('registracija radi na MySQL-u', r.status === 200 && !!r.json.token, r.text.slice(0, 90));
const tokenA = r.json.token;
const tokenB = (await call('/auth/register', { method: 'POST', body: { username: B, password: 'lozinka123' } })).json.token;

r = await call('/auth/register', { method: 'POST', body: { username: A, password: 'lozinka123' } });
ok('duplo korisnicko ime se odbija (UNIQUE u bazi)', r.status === 409, `status ${r.status}`);

r = await call('/auth/login', { method: 'POST', body: { username: A, password: 'pogresna' } });
ok('pogresna lozinka se odbija', r.status === 401, `status ${r.status}`);

r = await call('/auth/login', { method: 'POST', body: { username: A, password: 'lozinka123' } });
ok('prijava radi (PBKDF2 hes iz baze)', r.status === 200 && !!r.json.token);

r = await call('/users');
ok('bez tokena -> 401', r.status === 401, `status ${r.status}`);

r = await call('/users', { token: tokenA });
ok('spisak korisnika se cita iz baze', Array.isArray(r.json) && r.json.some((u) => u.username === B));

const a = await connect(tokenA);
const b = await connect(tokenB);
await wait(300);

a.send({ type: 'msg', to: B, id: 'm1', text: 'zdravo iz MySQL verzije' });
await wait(400);
ok('poruka stize u realnom vremenu', last(b, 'msg')?.text === 'zdravo iz MySQL verzije', JSON.stringify(last(b, 'msg')));
ok('posiljalac dobija potvrdu', last(a, 'receipt')?.status === 'sent');

b.send({ type: 'ack_read', to: A, id: 'm1' });
await wait(300);
ok('potvrda o citanju stize nazad', last(a, 'receipt')?.status === 'read');

await call('/messages', { method: 'POST', token: tokenA, body: { clientId: 'm1', to: B, text: 'zdravo iz MySQL verzije' } });
r = await call(`/messages?peer=${B}`, { token: tokenA });
ok('poruka je sacuvana u MySQL bazi', r.json.some((m) => m.id === 'm1'), `${r.json.length} poruka`);

r = await call('/messages/status', { method: 'POST', token: tokenA, body: { clientId: 'm1', peer: B, status: 'read' } });
ok('status procitanosti se cuva', r.json?.status === 'read');

r = await call('/messages/edit', { method: 'POST', token: tokenA, body: { clientId: 'm1', peer: B, text: 'izmenjena poruka' } });
ok('izmena poruke se cuva', r.json?.text === 'izmenjena poruka' && r.json?.editedAt > 0);

r = await call('/messages/delete', { method: 'POST', token: tokenA, body: { clientId: 'm1', peer: B } });
ok('brisanje poruke se cuva', r.json?.deleted === true);

a.send({ type: 'create_group', name: 'proba grupa', members: [B] });
await wait(500);
const gid = last(a, 'group_created')?.id;
ok('grupa je napravljena', !!gid, JSON.stringify(last(a, 'group_created')));
ok('kreator je vlasnik', last(a, 'group_created')?.owner === A);

b.send({ type: 'add_members', group: gid, members: ['nepostojeci'] });
await wait(400);
ok('obican clan ne moze da dodaje (provera na serveru)',
   a.frames.concat(b.frames).some((f) => f.type === 'error' && f.reason === 'Not in this group'));

r = await call('/groups', { method: 'POST', token: tokenA,
  body: { id: gid, name: 'proba grupa', members: [A, B], admins: [A], owner: A } });
ok('clanstvo grupe se upisuje u bazu', r.json?.roles?.[A] === 'owner' && r.json?.roles?.[B] === 'member',
   JSON.stringify(r.json?.roles));

const form = new FormData();
form.append('file', new Blob([new Uint8Array(5000).fill(65)], { type: 'text/plain' }), 'proba.txt');
r = await call('/media', { method: 'POST', token: tokenA, form });
ok('otpremanje fajla radi', r.status === 200 && /^[a-f0-9]{32}$/.test(r.json?.id || ''), r.text.slice(0, 100));
const dl = await fetch(`${API}/media/${r.json.id}?token=${tokenA}`);
ok('preuzimanje fajla radi', dl.status === 200 && (await dl.blob()).size === 5000);

r = await call('/calls', { method: 'POST', token: tokenA,
  body: { peer: B, outgoing: true, type: 'video', status: 'ended', durationMs: 12000 } });
ok('dnevnik poziva se upisuje', r.status === 200 && r.json?.type === 'video');
r = await call('/calls', { token: tokenB });
ok('druga strana vidi poziv kao dolazni', r.json?.[0]?.outgoing === false && r.json?.[0]?.peer === A,
   JSON.stringify(r.json?.[0]));

a.ws.close();
b.ws.close();
await wait(200);
process.exit(0);
