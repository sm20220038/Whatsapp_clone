# WhatsApp Clone — Erlang + Java backend, React web frontend

A WhatsApp clone built the way the real one is built: an **Erlang/OTP** messaging
core (one lightweight process per connection, a registry process routing via native
message passing, store-and-forward offline queues), a **Spring Boot** backend for
accounts, history and media on **MySQL**, and a **React** frontend that works in a
desktop browser and on a phone.

*The main, more detailed documentation is in Serbian: [README.md](README.md).*

---

## Requirements

| Tool | Version |
|---|---|
| Erlang/OTP | 27+ (uses the built-in `json` module, no dependencies) |
| JDK | 17+ |
| Node.js | 18+ |
| MySQL / MariaDB | 5.7+ / 10.4 (XAMPP works) |
| Maven | not needed — `web/backend-java/mvnw` (Maven Wrapper) downloads it |

## Run it

1. Create the database once (tables are created by Hibernate on first start):

   ```bash
   mysql -u root -e "CREATE DATABASE waclone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   ```

2. Optional environment variables (defaults are fine on your own machine):

   | Variable | Default | Used by |
   |---|---|---|
   | `WACLONE_DB_USER` / `WACLONE_DB_PASS` | `root` / empty | Spring Boot |
   | `WACLONE_JWT_SECRET` | a development placeholder | Spring Boot **and** Erlang — must be identical in both |

   **Set `WACLONE_JWT_SECRET` to a long random string for any real deployment.**

3. Install the frontend dependencies: `cd web/frontend && npm install`

4. Start everything. On Windows:

   ```powershell
   .\run-web.ps1
   ```

   Or by hand, in three terminals, from the repository root:

   ```bash
   erlc -o server server/whatsapp_server.erl server/whatsapp_web.erl
   erl -noshell -pa server -s whatsapp_web start
   ```

   ```bash
   cd web/backend-java && ./mvnw spring-boot:run      # mvnw.cmd on Windows
   ```

   ```bash
   cd web/frontend && npm run dev
   ```

| Tier | Plain | TLS | Job |
|---|---|---|---|
| Erlang | `ws://:8081` | `wss://:8443` | live messaging |
| Spring Boot | `http://:8080` | `https://:8444` | register/login, users, history, media |
| React (Vite) | `http://:5173` | `https://:5174` | the UI |

Open `http://localhost:5173` and register two accounts — in two different browsers,
or one normal and one private window (the session is kept in `localStorage` per
origin). localhost counts as a secure context, so calls work there over plain HTTP.
The TLS ports are for other devices; the page picks matching backend ports from its
own protocol (`config.js`).

Tests: `cd web/backend-java && ./mvnw test` (166 JUnit tests, H2 in memory), and with
all three servers running, `node tools/smoke-test.mjs` (21 end-to-end checks).

## How the three tiers split the work

**Erlang does messaging, and only messaging.** Routing, groups, presence, typing,
receipts, reactions, mute, and the offline queue. It is store-and-forward: a
message lives in memory only until it is delivered. For calls it relays the SDP
and ICE and nothing else — the audio and video never pass through it.

**Java does everything with a lifetime longer than a socket.** Accounts and
passwords (PBKDF2), the JWT, the user directory, chat history, and media files.

**They share one secret, not one database.** Spring signs an HS256 JWT with
`WACLONE_JWT_SECRET`; Erlang verifies the same token with the same secret before
accepting a login. Neither calls the other. The WebSocket protocol (RFC 6455) and
the JWT check are hand-written in Erlang, with no external libraries.

```
React ──login/register──> Spring Boot ──JWT──> React
React ──{"type":"login","token":<jwt>}──> Erlang  (verifies signature + exp)
React <──live frames──> Erlang
React ──history / media──> Spring Boot ──> MySQL + disk
```

## What it supports

1:1 and group chat with **group admins**, the full receipt chain (🕘 pending →
✔ sent → ✔✔ delivered → blue ✔✔ read, **persisted** so a reload keeps the blue
ticks), presence and last-seen, typing indicators, replies with quoting, emoji
reactions and an **emoji picker**, **edit and delete-for-everyone**, **voice
messages**, mute/ignore, media up to 500 MB, **voice and video calls** (WebRTC),
**profile pictures**, **desktop notifications with a sound**, day separators,
unread badges, search, **paginated history** that loads as you scroll up,
automatic reconnect with backoff, and a responsive layout that becomes a
one-pane phone UI under 780 px.

Logging in a second time **takes over** the older session (it is told
`session_replaced`) rather than refusing, so a page refresh never locks you out.

## Groups and admins

Whoever creates a group **owns** it and is its first admin. Tap the group name in
the chat header to open group info: the member list with `owner` / `admin` badges,
and — if you are an admin — **Add members** plus make/dismiss admin on anyone else.

| Action | Who may | Frame |
|---|---|---|
| Create a group | anyone | `create_group` |
| Add members afterwards | admins only | `add_members` |
| Make / dismiss an admin | admins only | `set_admin` |
| Remove a member | admins only (never the owner) | `remove_member` |
| Leave the group | anyone, including the owner | `leave_group` |
| Demote the owner | **nobody** | — |

When the **owner leaves**, ownership passes to the oldest admin (or, if there are
none, the first remaining member). When the **last member** leaves, the group is
deleted.

The rules are enforced **in the Erlang router**, not in the UI: a non-admin who
sends `add_members` gets `Not in this group` back, so the hidden buttons are a
convenience, not the security boundary.

**Groups survive an Erlang restart.** The router keeps groups in memory, but on
start it calls `GET /api/internal/groups` on the Java backend (authenticated with
the shared secret, retrying while Spring boots) and reloads every stored group with
its members and roles. Group ids carry the boot timestamp (`g1785278716-2`) so a
group created after a restart never collides with a stored one.

## Calls (WebRTC)

Open a 1:1 chat and press 📞 for voice or 🎥 for video. Erlang relays five frames
and then gets out of the way:

```
caller ──call_offer  {to, sdp, video}──> Erlang ──> callee     (rings)
callee ──call_answer {to, sdp}────────> Erlang ──> caller
both   ──call_ice    {to, candidate}──> Erlang ──> other side  (trickle ICE)
either ──call_reject {to, reason} / call_end {to}
```

Once ICE completes, the media is **peer-to-peer**. Calling someone offline (or who
muted you) answers `call_failed`; calling someone already on a call answers
`call_reject` with reason `busy`. Every call is written to the call log by the
**caller**.

No STUN server is configured, because host candidates are all you need on localhost
or one LAN. To call across networks, set one:

```
VITE_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]
```

### Calls from a phone (HTTPS)

Browsers only expose the camera and microphone on **HTTPS or localhost**, so the
stack can serve TLS using one shared self-signed certificate: run
`node tools/make-cert.mjs` (it writes `certs/`, which is not committed), then
`npm run dev:https` in `web/frontend`. On the phone, visit
`https://<ip>:8444/api/auth/login` and `https://<ip>:8443` once to accept the
certificate, then open `https://<ip>:5174`.

## Project layout

```
server/whatsapp_web.erl          WebSocket transport (RFC 6455, hand-written) + JWT check
                                 + WebRTC signaling relay
server/whatsapp_server.erl       the registry/router: groups, presence, offline queue
web/backend-java/
    mvnw, mvnw.cmd               Maven Wrapper
    src/main/java/com/waclone/
        model/                   8 JPA entities (User, Conversation, Participant, Message,
                                 Reaction, CallLog, Mute, KeyBundle)
        repo/                    Spring Data repositories (no hand-written SQL)
        security/                JwtService (HS256), PasswordService (PBKDF2), AuthFilter
        service/ChatService      conversations, history, reactions, groups
        web/                     Auth, User, Message, Media, Group, Call, Internal controllers
    src/main/resources/application.properties
    src/test/                    166 JUnit tests (H2 in memory, MockMvc)
web/frontend/src/
    useChat.js                   the socket: frames in, actions out
    useCall.js                   WebRTC: offer/answer/ICE, media, call state machine
    api.js                       REST calls + upload with progress
    notify.js                    notifications + the synthesized beep
    components/                  Login, Chat, Sidebar, Conversation, MessageBubble, Composer,
                                 Avatar, NewGroupDialog, GroupInfoDialog, CallOverlay, Notice
    styles.css                   WhatsApp-style dark theme, responsive
tools/make-cert.mjs              generates the shared self-signed dev certificate
tools/smoke-test.mjs             end-to-end check across all three servers
run-web.ps1                      starts all three tiers (Windows)
```

## REST API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | create an account → `{token, user}` |
| POST | `/api/auth/login` | sign in → `{token, user}` |
| GET | `/api/me`, `/api/users` | own profile, account directory |
| PUT | `/api/me` | change display name / profile picture |
| GET/POST | `/api/messages` | history page (`before`, `limit`) / record a sent message |
| POST | `/api/messages/status` | persist a delivered/read receipt |
| POST | `/api/messages/edit`, `/api/messages/delete` | amend your own message |
| POST | `/api/messages/react` | persist a reaction |
| POST | `/api/media` | upload (streamed, ≤ 500 MB) → id, size, sha256 |
| GET | `/api/media/{id}` | download; accepts `?token=` so `<img>`/`<audio>` work |
| POST | `/api/groups` | mirror a group's membership and roles into the database |
| GET | `/api/groups/{id}` | stored group with each member's role |
| GET | `/api/internal/groups` | all groups, for the Erlang restart reload (shared secret, not JWT) |
| GET/POST | `/api/calls` | call history / record a finished call |

Everything under `/api` except `/api/auth/*` and `/api/internal/*` needs
`Authorization: Bearer <jwt>`.

## Known limitations

- **No end-to-end encryption** — traffic is protected by TLS, not end-to-end.
- **Group calls** are not supported — calls are 1:1.
- The **offline queue lives in Erlang's memory**; a restart loses undelivered
  messages (they remain in the database history if the sender recorded them).
- History is recorded by the sending client, so a message sent by something other
  than this React app (a test script, say) will not appear after a reload.
- The schema is managed by Hibernate (`ddl-auto=update`); a production system would
  use migrations (Flyway or Liquibase).
- Development defaults (shared secret, self-signed certificate) must be replaced for
  any real deployment.
