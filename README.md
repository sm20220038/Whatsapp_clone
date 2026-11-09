# WhatsApp Clone

A real-time chat app I built for my bachelor's thesis at FON (University of Belgrade, 2026).

I wanted to understand how WhatsApp actually works, so I split the backend the same way they do.
An Erlang server handles the live connections and passes messages around, and a Spring Boot
server takes care of accounts, chat history and files. The frontend is a React app that works in
a desktop browser and on a phone.

## Features

- 1:1 and group chats
- Message ticks: sending, sent, delivered, read (blue)
- Messages to offline users wait on the server and arrive when they log in
- Groups with an owner and admins who can add, remove and promote members
- Online status, "last seen" and "typing..."
- Replies, emoji reactions, editing and deleting messages for everyone
- Photos, files (up to 500 MB) and voice messages
- Voice and video calls with WebRTC
- Muting people, profile pictures, browser notifications
- Older messages load as you scroll up
- Works on mobile (single pane layout under 780px)

## How it works

There are three programs running at the same time:

| Part | Tech | Port | What it does |
|---|---|---|---|
| Message server | Erlang/OTP | 8081 (ws), 8443 (wss) | Keeps a WebSocket open for every user and routes messages |
| Backend | Java 17, Spring Boot | 8080 (http), 8444 (https) | Accounts, login, chat history, files, call log |
| Frontend | React, Vite | 5173 (http), 5174 (https) | The UI |

The data is stored in MySQL. Only the Spring Boot server talks to the database.

When you send a message, the browser sends it over the WebSocket to Erlang, which delivers it
right away (or queues it if the other person is offline). At the same time the browser sends it
to Spring Boot over REST so it gets saved. This way delivery never waits for the database.

The two servers don't call each other. When you log in, Spring Boot gives you a JWT signed with a
shared secret. You send that same token to Erlang, and Erlang checks the signature itself using
the same secret. The only time Erlang talks to Spring Boot is on startup, to load the groups back
from the database.

Some things I'm happy with:

- Every connection is its own Erlang process, and one `registry` process holds all the routing
  state, so there are no locks anywhere.
- I wrote the WebSocket protocol (RFC 6455) and the JWT check by hand in Erlang, so that part has
  no external libraries at all.
- Calls are peer to peer. The server only forwards the WebRTC offer, answer and ICE candidates,
  the actual audio and video never go through it.

## Running it

You need:

- Erlang/OTP 27 or newer
- JDK 17 or newer
- Node.js 18 or newer
- MySQL or MariaDB (XAMPP works fine)

You don't need Maven installed, the backend comes with the Maven Wrapper (`mvnw`).

### 1. Create the database

Only once. The tables get created automatically when the backend starts.

```bash
mysql -u root -e "CREATE DATABASE waclone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 2. Environment variables (optional)

| Variable | Default | Used by |
|---|---|---|
| `WACLONE_DB_USER` | `root` | backend |
| `WACLONE_DB_PASS` | empty | backend |
| `WACLONE_JWT_SECRET` | a dev placeholder | backend and Erlang, has to be the same in both |

The defaults are fine for running it on your own machine. If you ever put this online, set
`WACLONE_JWT_SECRET` to something long and random.

### 3. Install frontend packages

```bash
cd web/frontend
npm install
```

### 4. Start everything

On Windows there's a script that starts all three:

```powershell
.\run-web.ps1
```

Or start them yourself in three terminals, from the root of the repo:

```bash
erlc -o server server/whatsapp_server.erl server/whatsapp_web.erl
erl -noshell -pa server -s whatsapp_web start
```

```bash
cd web/backend-java
./mvnw spring-boot:run
```

(On Windows use `mvnw.cmd spring-boot:run`.)

```bash
cd web/frontend
npm run dev
```

Then open http://localhost:5173 and register two accounts. Use two different browsers, or one
normal window and one incognito window, because the login is saved per browser.

### Calls from a phone

Browsers only allow the camera and microphone on HTTPS or localhost. On your own computer calls
just work on localhost. For a phone on the same Wi-Fi:

1. Make a self-signed certificate: `node tools/make-cert.mjs`
2. Start the HTTPS frontend: `cd web/frontend` and `npm run dev:https`
3. On the phone, open `https://<your-pc-ip>:8444/api/auth/login` and `https://<your-pc-ip>:8443`
   and accept the certificate warning on both
4. Then open `https://<your-pc-ip>:5174`

No STUN server is set up because it isn't needed on a local network. To call across networks you
can set one with `VITE_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]`.

## Tests

The backend has 166 JUnit tests. They use an H2 in-memory database, so they don't touch MySQL:

```bash
cd web/backend-java
./mvnw test
```

There's also an end-to-end check that runs against all three servers (start them first):

```bash
node tools/smoke-test.mjs
```

It prints 21 checks and they should all say PASS. It creates users starting with `proba`, which
you can delete afterwards with `DELETE FROM users WHERE username LIKE 'proba%';`.

## Project structure

```
server/
  whatsapp_server.erl     registry: routing, groups, presence, offline queue
  whatsapp_web.erl        WebSocket server, JWT check, call signaling
web/backend-java/
  src/main/java/com/waclone/
    model/                JPA entities (8 tables)
    repo/                 Spring Data repositories
    security/             JWT, password hashing, auth filter
    service/              ChatService
    web/                  REST controllers
  src/test/               JUnit tests
web/frontend/src/
  useChat.js              WebSocket connection and chat state
  useCall.js              WebRTC calls
  api.js                  REST calls
  components/             React components
tools/
  make-cert.mjs           makes the dev certificate
  smoke-test.mjs          end-to-end test
run-web.ps1               starts everything on Windows
```

## REST API

Everything under `/api` needs an `Authorization: Bearer <token>` header, except `/api/auth/*`.
There's also `/api/internal/groups`, which only Erlang uses on startup, and it's protected with
the shared secret instead of a token.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Log in, returns a token |
| GET, PUT | `/api/me` | Your profile |
| GET | `/api/users` | All users |
| GET, POST | `/api/messages` | Load history (paged) / save a message |
| POST | `/api/messages/status` | Save delivered/read status |
| POST | `/api/messages/edit`, `/api/messages/delete` | Edit or delete your message |
| POST | `/api/messages/react` | Save a reaction |
| POST | `/api/media` | Upload a file |
| GET | `/api/media/{id}` | Download a file |
| POST | `/api/groups` | Save group members and roles |
| GET | `/api/groups/{id}` | Get a group |
| GET, POST | `/api/calls` | Call history / save a call |

## Known limitations

- No end-to-end encryption, only TLS.
- Calls are 1:1 only, no group calls.
- The offline queue lives in Erlang's memory, so if the Erlang server restarts, messages that
  weren't delivered yet are lost from the queue (they're still in the database history).
- Read receipts only work in 1:1 chats, group messages stop at "sent".
- History is saved by the browser that sends the message, so a message sent some other way (like a
  test script) won't show up after a refresh.
- The database schema is managed by Hibernate (`ddl-auto=update`). For a real app I'd use
  migrations instead.
