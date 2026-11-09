# WhatsApp Clone — Erlang + Java backend, React web frontend

Klon WhatsApp-a napravljen onako kako je i original napravljen: **Erlang/OTP** nosi
razmenu poruka, **Spring Boot (Java)** nosi naloge, istoriju i medije, a **React**
je korisnički interfejs koji radi i na računaru i na telefonu kroz pregledač.

> Pored pokretanja, dokument objašnjava i **zašto** je sistem napravljen baš ovako —
> sekcija [Česta pitanja](#12-česta-pitanja) pokriva ključne odluke.

---

## Sadržaj

1. [Šta projekat radi](#1-šta-projekat-radi)
2. [Arhitektura](#2-arhitektura)
3. [Zašto baš ovakva podela](#3-zašto-baš-ovakva-podela)
4. [Tehnologije i verzije](#4-tehnologije-i-verzije)
5. [Pokretanje bez PowerShell skripti](#5-pokretanje-bez-powershell-skripti)
6. [Baza podataka](#6-baza-podataka)
7. [Protokol komunikacije](#7-protokol-komunikacije)
8. [REST API](#8-rest-api)
9. [Bezbednost](#9-bezbednost)
10. [Struktura projekta](#10-struktura-projekta)
11. [Scenario za demonstraciju](#11-scenario-za-demonstraciju)
12. [Česta pitanja](#12-česta-pitanja)
13. [Testiranje](#13-testiranje)
14. [Poznata ograničenja](#14-poznata-ograničenja)

---

## 1. Šta projekat radi

| Funkcionalnost | Opis |
|---|---|
| **Pojedinačni chat** | Razmena poruka u realnom vremenu između dva korisnika |
| **Grupni chat** | Grupe sa administratorima: dodavanje, uklanjanje, dodela admin uloge, napuštanje |
| **Potvrde o isporuci** | na čekanju → ✔ poslato → ✔✔ isporučeno → plavo ✔✔ pročitano (trajno sačuvano) |
| **Prisustvo** | Online status i „poslednji put viđen" |
| **Kucanje** | Indikator „typing…" |
| **Odgovori i reakcije** | Citiranje poruke i emodži reakcije |
| **Izmena i brisanje** | Izmena svoje poruke (oznaka „edited") i brisanje za sve |
| **Glasovne poruke** | Snimanje u pregledaču (WebM/Opus) |
| **Mediji** | Slike, video i fajlovi do **500 MB** |
| **Pozivi** | Audio i video pozivi preko **WebRTC** |
| **Ignorisanje (mute)** | Server odbacuje poruke te osobe — kao da ne postoji, samo za vas |
| **Emodži tastatura** | 260 emodžija u 6 kategorija |
| **Profilne slike** | Otpremanje slike naloga |
| **Notifikacije** | Obaveštenja pregledača i zvučni signal |
| **Istorija** | Trajno čuvanje u bazi, učitava se stranično dok skrolujete nagore |
| **Responzivnost** | Ispod 780 px prelazi u prikaz sa jednim panelom (telefon) |

---

## 2. Arhitektura

Tri odvojena servera koji **ne dele bazu** i **ne dele kod** — spaja ih samo
dogovoreni format poruka i jedna zajednička tajna.

```
                    +--------------------------+
                    |   React (Vite)  :5173    |
                    |   pregledač / telefon    |
                    +---+------------------+---+
             REST/HTTPS |                  | WebSocket
        (nalozi, medij, |                  | (žive poruke)
           istorija)    |                  |
                        v                  v
        +-----------------------+  +--------------------------+
        |  Spring Boot  :8080   |  |   Erlang/OTP   :8081     |
        |  ------------------   |  |   ------------------     |
        |  - registracija/login |  |  - rutiranje poruka      |
        |  - JWT (HS256)        |  |  - grupe, prisustvo      |
        |  - korisnici, profili |  |  - potvrde, reakcije     |
        |  - istorija poruka    |  |  - red čekanja (offline) |
        |  - medij fajlovi      |  |  - signalizacija poziva  |
        |  - dnevnik poziva     |  |                          |
        |          |            |  |  jedan proces po vezi    |
        |       +--v----+       |  |  + `registry` proces     |
        |       | MySQL |       |  |  (celo stanje rutiranja) |
        |       +-------+       |  |                          |
        +-----------------------+  +--------------------------+
                    ^                          |
                    +--------------------------+
                    Erlang čita grupe iz baze
                     samo pri pokretanju
                   (GET /api/internal/groups)
```

### Tok podataka pri slanju poruke

1. Korisnik ukuca poruku → React šalje WebSocket okvir `{"type":"msg","to":"stefan",...}`
2. Erlang `registry` proces nalazi primaoca:
   - **online** → odmah prosleđuje kroz njegov socket
   - **offline** → stavlja u red čekanja u memoriji, isporučuje pri sledećoj prijavi
   - **ignorisan** → tiho odbacuje (pošiljalac vidi samo jedan ✔)
3. Paralelno, React šalje `POST /api/messages` Javi da poruka ostane u bazi
4. Primalac vraća `ack_delivered`, pa `ack_read` kad otvori chat → pošiljalac vidi plavo ✔✔

### Ključna ideja: `registry` proces ne zna za transport

Isti `registry` opslužuje **dva različita klijenta**:

| | Web aplikacija | Desktop aplikacija (ranija faza, nije deo ovog repozitorijuma) |
|---|---|---|
| Interfejs | React u pregledaču | JavaFX |
| Transport | WebSocket `:8081` | čist TCP `:5555` |
| Prijava | korisničko ime + lozinka + JWT | samo korisničko ime |
| Pozivi | WebRTC | UDP peer-to-peer |

Zato je dodavanje web klijenta zahtevalo **samo novi transportni sloj** — logika
rutiranja, grupa, prisustva i potvrda ostala je nepromenjena. To je i razlog zašto
je `whatsapp_web.erl` relativno mali fajl: on je samo „prevodilac" između WebSocket
okvira i internih Erlang poruka.

---

## 3. Zašto baš ovakva podela

**Zašto Erlang za poruke?**
Pravi WhatsApp koristi Erlang i to nije slučajno. Erlang procesi su izuzetno jeftini
(nekoliko kilobajta memorije), pa jedan server može da drži ogroman broj istovremenih
veza. Ovde svaka konekcija dobija svoj proces, a `registry` proces prosleđuje poruke
preko ugrađenog Erlang mehanizma slanja poruka — bez zaključavanja i bez deljene
memorije, pa nema klasičnih problema sa konkurentnošću.

**Zašto Java za sve ostalo?**
Erlang je odličan za promet poruka, ali sve što mora da **preživi** prekid veze —
nalozi, lozinke, istorija, fajlovi — prirodno pripada klasičnom backend-u sa bazom.
Spring Data JPA daje objektno-relaciono mapiranje i automatsko kreiranje šeme.

**Zašto se serveri međusobno ne poznaju?**
Jedina veza je **deljena tajna** kojom Spring potpisuje JWT, a Erlang ga proverava.
Nema HTTP poziva između njih pri svakoj poruci, dakle nema uskog grla ni zavisnosti
jednog od drugog. (Jedini izuzetak je jedan poziv pri pokretanju Erlanga, kojim se
vraćaju grupe iz baze.)

**Zašto store-and-forward?**
Erlang čuva poruku samo dok se ne isporuči, pa je zaboravlja — kao pravi WhatsApp,
gde istorija živi na uređaju. Pošto pregledač nema pouzdanu trajnu memoriju, istoriju
čuva Java. Tako dobijamo i verno ponašanje i upotrebljivu aplikaciju.

---

## 4. Tehnologije i verzije

| Sloj | Tehnologija | Verzija | Zavisnosti |
|---|---|---|---|
| Poruke | Erlang/OTP | **29** | **nijedna** — ugrađen `json` modul (OTP 27+), ručno pisan WebSocket |
| Backend | Spring Boot | **3.3.5** | Web, Data JPA, Validation |
| Baza | **MySQL / MariaDB** | 5.7+ / 10.4 | JDBC drajver `mysql-connector-j` |
| Java | JDK | **17** | — |
| Frontend | React | **18.3** | — |
| Build alat | Vite | **5.4** | — |
| Node | Node.js | 18+ (testirano 22) | — |
| Maven | Apache Maven | 3.9.16 | preko Maven Wrapper-a (`mvnw`), ne mora da se instalira |

**Erlang deo nema nijednu spoljnu biblioteku.** WebSocket rukovanje (RFC 6455),
provera JWT potpisa, base64url kodiranje i JSON — sve je napisano ručno ili koristi
ugrađene OTP module. Nema `rebar3`, nema Cowboy-a.

---

## 5. Pokretanje bez PowerShell skripti

Projekat se pokreće iz **tri terminala** (četvrti samo ako testirate sa telefona).
Svaki server ide u svoj terminal jer sva tri rade istovremeno. Sve komande polaze iz
**korena repozitorijuma**.

Na Windows-u sve tri stvari pokreće jedna skripta: `.\run-web.ps1`.

### Preduslovi

```bash
java -version     # mora 17 ili noviji
node --version    # mora 18 ili noviji
mysql --version   # MySQL 5.7+ ili MariaDB (XAMPP)

# Erlang: `erl -version` ispisuje verziju BEAM emulatora, a ne OTP izdanje.
# Za OTP izdanje (treba 27+):
erl -noshell -eval "io:format(\"OTP ~s~n\",[erlang:system_info(otp_release)]),halt()."
```

Maven ne mora da bude instaliran — `web/backend-java/mvnw.cmd` (Maven Wrapper) pri prvom
pokretanju sam preuzme odgovarajuću verziju.
MySQL dolazi uz XAMPP (`C:\xampp\mysql`) — dovoljno ga je pokrenuti iz XAMPP kontrolne table.

### Korak 0 — jednokratna priprema

**a) Pokrenuti MySQL** — u XAMPP kontrolnoj tabli dugme *Start* pored *MySQL*.

> Ako *Start* prijavi da je port 3306 zauzet, negde već radi `mysqld` koji XAMPP
> nije pokrenuo. Ugasi se ovom komandom, pa se *Start* ponovi:
>
> ```bash
> C:\xampp\mysql\bin\mysqladmin.exe -u root shutdown
> ```

**b) Napraviti bazu** (samo prvi put):

```bash
C:\xampp\mysql\bin\mysql.exe -u root -e "CREATE DATABASE waclone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Tabele **ne treba** praviti ručno — Hibernate ih napravi sam iz Java klasa pri prvom
pokretanju aplikacije.

Ako vaš MySQL ima lozinku za `root`, postavite je pre pokretanja Spring-a:

```bash
set WACLONE_DB_USER=root
set WACLONE_DB_PASS=vasa_lozinka
```

**Deljena tajna za JWT** čita se iz promenljive `WACLONE_JWT_SECRET` — i u Javi i u Erlangu,
pa mora biti ista u oba terminala. Ako nije postavljena, koristi se razvojna podrazumevana
vrednost, što je u redu samo za rad na sopstvenom računaru:

```bash
set WACLONE_JWT_SECRET=neka-dugacka-nasumicna-niska
```

**c) Instalirati zavisnosti frontend-a:**

```bash
cd web\frontend
npm install
```

### Terminal 1 — Erlang (razmena poruka)

Mora se pokretati **iz korena projekta**, jer server tu traži foldere `certs\` i `media\`.

```bash
erlc -o server server\whatsapp_server.erl server\whatsapp_web.erl
erl -noshell -pa server -s whatsapp_web start
```

Ako `erl` nije na PATH-u, koristite pune putanje:

```bash
"C:\Program Files\Erlang OTP\bin\erlc.exe" -o server server\whatsapp_server.erl server\whatsapp_web.erl
"C:\Program Files\Erlang OTP\bin\erl.exe" -noshell -pa server -s whatsapp_web start
```

Očekivani ispis:

```
=============================================
 WhatsApp WebSocket server on ws://localhost:8081/
                     and on wss://localhost:8443/
=============================================
```

Ako nema `certs\` foldera, ispisaće da je `wss` isključen — to je uredu za rad na
sopstvenom računaru.

### Terminal 2 — Spring Boot (nalozi, istorija, mediji)

`JAVA_HOME` mora da pokazuje na **JDK 17 ili noviji**.

Command Prompt (`cmd.exe`):

```bash
cd web\backend-java
set JAVA_HOME=C:\Program Files\Java\jdk-17
mvnw.cmd spring-boot:run
```

PowerShell:

```powershell
cd web\backend-java
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd spring-boot:run
```

Na Linux-u i macOS-u: `./mvnw spring-boot:run`.

Čeka se poruka `Started BackendApplication in ... seconds`.

### Terminal 3 — React (korisnički interfejs)

```bash
cd web\frontend
npm run dev
```

### Otvaranje aplikacije

```
http://localhost:5173
```

Registrujte dva naloga — u **dva različita pregledača**, ili jedan običan i jedan
„incognito" prozor. (Nalog se pamti u `localStorage` po adresi, pa dva obična taba
istog pregledača dele isti nalog.)

### Terminal 4 — samo za testiranje sa telefona

```bash
cd web\frontend
npm run dev:https
```

### Pozivi sa telefona

Pregledači daju pristup mikrofonu i kameri **samo preko HTTPS-a ili sa localhost-a**.
Na vašem računaru pozivi rade odmah preko `http://localhost:5173`. Za telefon treba:

1. Napraviti sertifikat (jednom):

   ```bash
   node tools\make-cert.mjs
   ```

2. Na telefonu otvoriti `https://<ip>:8444/api/auth/login` i `https://<ip>:8443` i
   **prihvatiti upozorenje o sertifikatu** na svakoj adresi posebno
3. Zatim otvoriti `https://<ip>:5174`

IP adresu računara daje `ipconfig`. Telefon i računar moraju biti na istoj Wi-Fi mreži.

### Tabela portova

| Servis | HTTP | HTTPS |
|---|---|---|
| Erlang (poruke) | `ws://:8081` | `wss://:8443` |
| Spring Boot | `http://:8080` | `https://:8444` |
| React | `http://:5173` | `https://:5174` |

Stranica sama bira portove backend-a prema svom protokolu (`src/config.js`), pa
otvaranje preko HTTPS-a automatski koristi `:8444` i `:8443`.

### Zaustavljanje

`Ctrl+C` u svakom terminalu. Ako Erlang ostane da radi u pozadini:

```powershell
Get-Process erl | Stop-Process -Force
```

### Pregled baze

Preko **phpMyAdmin**-a (ako koristite XAMPP):

```
http://localhost/phpmyadmin     ->  baza `waclone`
```

Ili iz komandne linije:

```bash
C:\xampp\mysql\bin\mysql.exe -u root waclone
```

```sql
SHOW TABLES;
SELECT username, display_name, last_seen FROM users;
SELECT * FROM message ORDER BY sent_at DESC LIMIT 10;
```

Ovde se uživo vide sve tabele — korisno za demonstraciju baze.

---

## 6. Baza podataka

Model ima **8 entiteta** — Java klase u paketu `web/backend-java/src/main/java/com/waclone/model/`,
iz kojih Hibernate pravi tabele.

### Tabele

```
User(userId PK, username UNIQUE, displayName, passwordHash, role,
     lastSeen, online, avatarId)

Conversation(conversationId PK, type, title, externalId UNIQUE, createdAt)

Participant(userId FK→User, conversationId FK→Conversation,
            joinedAt, role, unreadCount,
            PRIMARY KEY(userId, conversationId))                -- veza M:N

Message(messageId PK, conversationId FK→Conversation, senderId FK→User,
        replyToId FK→Message NULL, clientId, body, type, status,
        encrypted, sentAt, editedAt, deleted,
        fileName, fileSizeBytes, storagePath, sha256)           -- mediji ovde

Reaction(reactionId PK, messageId FK→Message, userId FK→User, emoji, reactedAt)

CallLog(callId PK, callerId FK→User, calleeId FK→User,
        type, status, startedAt, endedAt)

Mute(muterId FK→User, mutedId FK→User, mutedAt,
     PRIMARY KEY(muterId, mutedId))                             -- veza M:N

KeyBundle(bundleId PK, userId FK→User UNIQUE, identityKey, signingKey,
          signedPreKey, signedPreKeySig, oneTimePreKeys)
```

### Zašto je svaka tabela tu

| Tabela | Razlog postojanja |
|---|---|
| `User` | Prijava, identitet pošiljaoca, „poslednji put viđen", profilna slika |
| `Conversation` | Razlikuje 1:1 razgovor od grupe; `externalId` povezuje sa ID-jem grupe iz Erlanga |
| `Participant` | Ko je u kom razgovoru i sa kojom ulogom (OWNER / ADMIN / MEMBER) |
| `Message` | Istorija razgovora — tekst **i** mediji (polja `fileName`, `storagePath`, `sha256`) |
| `Reaction` | Emodži reakcije: ko je čime reagovao |
| `CallLog` | Dnevnik poziva: ko, kada, koliko dugo, kako se završio |
| `Mute` | Funkcija ignorisanja korisnika (`User ignoriše User`) |
| `KeyBundle` | Ključevi za end-to-end enkripciju (koristila ih je ranija desktop verzija) |

### Dve odluke o modelovanju koje vredi objasniti

**1. Mediji nisu posebna tabela.** Slika ili fajl je samo `Message` čiji je `type`
`IMAGE` ili `FILE`, sa popunjenim poljima za fajl. Jedna poruka nosi najviše jedan
prilog, pa zasebna tabela `Attachment` ne bi donela ništa osim još jednog spajanja.

**2. Jednokratni ključevi su unutar `KeyBundle`** kao JSON kolekcija, umesto da budu
zasebna tabela. Tako model ostaje ispod 8 tabela. (Ako se traži čista normalizacija,
izdvaja se `OneTimePreKey` i model ima 9 tabela.)

### Enumeracije

| Enum | Vrednosti |
|---|---|
| `Role` | `ADMIN`, `USER` |
| `MemberRole` | `OWNER`, `ADMIN`, `MEMBER` |
| `ConversationType` | `DIRECT`, `GROUP` |
| `MessageType` | `TEXT`, `IMAGE`, `FILE`, `SYSTEM` |
| `MessageStatus` | `PENDING`, `SENT`, `DELIVERED`, `READ` |
| `CallType` | `VOICE`, `VIDEO` |
| `CallStatus` | `MISSED`, `ACCEPTED`, `REJECTED`, `ENDED` |

### Veze između tabela (srpski nazivi sa dijagrama)

| Srpski | Engleski | Između |
|---|---|---|
| poseduje | owns | User → KeyBundle |
| ima / sadrži | has / contains | User/Conversation → Participant |
| sadrži | contains | Conversation → Message |
| šalje | sends | User → Message |
| odgovara na | replies to | Message → Message |
| ima | has | Message → Reaction |
| postavlja | posts | User → Reaction |
| upućuje / prima | initiates / receives | User → CallLog |
| ignoriše | ignores | User → User |

`Participant` i `Mute` su dve **veze više-prema-više**; sve ostalo je jedan-prema-više.

---

## 7. Protokol komunikacije

Jedan JSON objekat po WebSocket okviru. Erlang nikada ne čuva sadržaj poruke.

### Klijent → Server

| Okvir | Značenje |
|---|---|
| `login` | Prijava sa JWT tokenom |
| `msg` / `gmsg` | Poruka pojedincu / grupi |
| `media` | Poruka sa prilogom |
| `msg_edit` / `msg_delete` | Izmena / brisanje svoje poruke |
| `react` | Emodži reakcija |
| `typing` | Korisnik kuca |
| `ack_delivered` / `ack_read` | Potvrda isporuke / čitanja |
| `create_group` | Kreiranje grupe |
| `add_members` / `remove_member` | Dodavanje / uklanjanje člana (samo admin) |
| `set_admin` | Dodela ili oduzimanje admin uloge (samo admin) |
| `leave_group` | Napuštanje grupe |
| `mute` / `unmute` | Ignorisanje korisnika |
| `call_offer` / `call_answer` / `call_ice` | WebRTC signalizacija |
| `call_reject` / `call_end` | Odbijanje / prekid poziva |

### Server → Klijent

| Okvir | Značenje |
|---|---|
| `login_ok` | Uspešna prijava + spisak korisnika, grupa i ignorisanih |
| `presence` | Neko se povezao ili otišao offline |
| `msg` / `gmsg` / `media` | Dolazna poruka |
| `msg_edit` / `msg_delete` | Neko je izmenio ili obrisao poruku |
| `receipt` | Promena statusa poruke |
| `reaction` | Dolazna reakcija |
| `typing` | Neko kuca |
| `group_created` / `group_updated` | Grupa napravljena ili promenjena |
| `mute_ok` | Potvrda ignorisanja |
| `call_*` | Signalizacija poziva |
| `call_failed` | Pozvani nedostupan |
| `session_replaced` | Nalog je otvoren na drugom mestu |
| `error` | Greška sa razlogom |

### Primer razmene

```json
// klijent šalje
{"type":"msg","to":"stefan","id":"a1b2","text":"Zdravo!"}

// pošiljalac odmah dobija
{"type":"receipt","id":"a1b2","peer":"stefan","status":"sent"}

// primalac dobija
{"type":"msg","from":"mira","id":"a1b2","text":"Zdravo!","ts":1785349158843}

// primalac potvrđuje, pošiljalac dobija
{"type":"receipt","id":"a1b2","peer":"stefan","status":"delivered"}
{"type":"receipt","id":"a1b2","peer":"stefan","status":"read"}
```

### Pozivi (WebRTC)

Erlang prosleđuje **samo** SDP ponudu/odgovor i ICE kandidate. Čim se pregledači
dogovore, zvuk i slika idu **direktno između njih** i nikada ne prolaze kroz server —
ista podela kao kod pravog WhatsApp-a.

```
pozivalac --call_offer  {to, sdp, video}--> Erlang --> pozvani   (zvoni)
pozvani   --call_answer {to, sdp}--------> Erlang --> pozivalac
oba       --call_ice    {to, candidate}--> Erlang --> druga strana
```

STUN server **nije podešen** jer za localhost i lokalnu mrežu nije potreban.
Za pozive preko interneta postavlja se promenljiva:

```
VITE_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]
```

---

## 8. REST API

Sve pod `/api` osim `/api/auth/*` zahteva zaglavlje `Authorization: Bearer <jwt>`.

| Metoda | Putanja | Namena |
|---|---|---|
| POST | `/api/auth/register` | Registracija → `{token, user}` |
| POST | `/api/auth/login` | Prijava → `{token, user}` |
| GET | `/api/me` | Sopstveni profil |
| PUT | `/api/me` | Izmena imena ili profilne slike |
| GET | `/api/users` | Spisak svih naloga |
| GET | `/api/messages` | Stranica istorije (`peer`/`group`, `before`, `limit`) |
| POST | `/api/messages` | Beleženje poslate poruke |
| POST | `/api/messages/edit` | Izmena svoje poruke |
| POST | `/api/messages/delete` | Brisanje svoje poruke |
| POST | `/api/messages/react` | Trajno beleženje reakcije |
| POST | `/api/messages/status` | Beleženje potvrde isporuke/čitanja |
| POST | `/api/media` | Otpremanje fajla (do 500 MB) |
| GET | `/api/media/{id}` | Preuzimanje fajla |
| POST | `/api/groups` | Upis članstva i uloga grupe u bazu |
| GET | `/api/groups/{id}` | Grupa sa ulogama članova |
| GET | `/api/calls` | Istorija poziva |
| POST | `/api/calls` | Beleženje završenog poziva |
| GET | `/api/internal/groups` | Samo za Erlang pri pokretanju (deljena tajna, ne JWT) |

---

## 9. Bezbednost

| Mehanizam | Kako je rešeno |
|---|---|
| **Lozinke** | PBKDF2-HMAC-SHA256, **120 000 iteracija**, nasumična so od 16 bajtova. Čuva se `iteracije:so:heš` — nikada sama lozinka |
| **Tokeni** | JWT HS256 sa rokom trajanja 72 sata; potpis se proverava i u Javi i u Erlangu |
| **Deljena tajna** | Promenljiva okruženja `WACLONE_JWT_SECRET`, ista za Javu i Erlang. Bez nje se koristi razvojna podrazumevana vrednost — **za svaku stvarnu upotrebu obavezno je postaviti** |
| **Zaštita ruta** | `AuthFilter` presreće svaki `/api` zahtev; bez ispravnog tokena vraća 401 |
| **Provera okvira** | WebSocket sloj proverava svako polje pre prosleđivanja — neispravan okvir dobija grešku umesto da sruši server |
| **Prava u grupama** | Proveravaju se **na serveru**, ne u interfejsu. Sakrivena dugmad su samo udobnost |
| **Vlasništvo nad porukom** | Izmenu i brisanje odbija i klijent i Java backend ako korisnik nije autor |
| **Putanje fajlova** | ID medija mora da odgovara obrascu od 32 heksadecimalna znaka — sprečava izlazak iz foldera (`../`) |
| **TLS** | Ceo sloj radi i preko HTTPS/WSS sa deljenim sertifikatom |

### Šta namerno nije urađeno

**End-to-end enkripcija u web verziji.** Ranija desktop verzija (nije u ovom
repozitorijumu) imala je punu implementaciju Signal protokola (X3DH + Double Ratchet) u Javi. Prebacivanje na pregledač zahteva
ponovno pisanje nad Web Crypto API-jem, što je svesno ostavljeno za kasnije. Web
verzija se oslanja na TLS — dakle saobraćaj jeste šifrovan u prenosu, ali server
tehnički može da vidi sadržaj.

---

## 10. Struktura projekta

```
server/
  whatsapp_server.erl       registry (rutiranje, grupe, prisustvo) + TCP server za desktop
  whatsapp_web.erl          WebSocket transport (RFC 6455) + provera JWT + WebRTC signalizacija

web/backend-java/
  pom.xml
  mvnw, mvnw.cmd            Maven Wrapper (preuzima Maven pri prvom pokretanju)
  src/main/java/com/waclone/
    model/                  8 entiteta (User, Conversation, Participant, Message,
                            Reaction, CallLog, Mute, KeyBundle) + Enums
    repo/                   Spring Data repozitorijumi
    security/               JwtService (HS256), PasswordService (PBKDF2), AuthFilter
    service/ChatService     razgovori, istorija, reakcije, grupe
    web/                    Auth, User, Message, Media, Group, Call, Internal kontroleri
    config/                 CORS i dodatni HTTP konektor
  src/main/resources/application.properties

web/frontend/
  src/
    useChat.js              WebSocket veza: dolazni okviri i akcije
    useCall.js              WebRTC: ponuda/odgovor/ICE, stanje poziva
    api.js                  REST pozivi i otpremanje sa prikazom napretka
    notify.js               notifikacije i zvučni signal
    emoji.js                skup emodžija
    config.js               biranje adresa backend-a prema protokolu
    components/             Login, Chat, Sidebar, Conversation, MessageBubble,
                            Composer, Avatar, NewGroupDialog, GroupInfoDialog, CallOverlay
    styles.css              tamna tema u stilu WhatsApp-a, responzivna

tools/
  make-cert.mjs             pravi zajednički razvojni sertifikat
  smoke-test.mjs            provera celog sistema (21 provera)
  sql/                      jednokratna izmena stranih ključeva (ON UPDATE CASCADE)

run-web.ps1                 pokreće sva tri servera (Windows)
certs/                      razvojni sertifikat (pravi se komandom, nije u repozitorijumu)
```

---

## 11. Scenario za demonstraciju

Predlog redosleda za prikaz — pokriva sve slojeve za desetak minuta.

1. **Pokretanje** — tri terminala, pokazati ispis svakog servera
2. **Registracija** dva naloga u dva pregledača
3. **Poruka** — poslati poruku i pokazati kako se kvačice menjaju:
   ✔ poslato → ✔✔ isporučeno → plavo ✔✔ kad druga strana otvori chat
4. **Kucanje i prisustvo** — pokazati „typing…" i online status
5. **Odgovor i reakcija** — citirati poruku, dodati emodži
6. **Izmena i brisanje** — izmeniti poruku (pojavi se „edited"), pa je obrisati za sve
7. **Grupa** — napraviti grupu, dodati člana, dodeliti mu admin ulogu, pa ga ukloniti
8. **Mediji** — poslati sliku ili fajl
9. **Glasovna poruka** — snimiti i poslati
10. **Poziv** — pokrenuti video poziv između dva pregledača
11. **Ignorisanje** — uključiti mute i pokazati da poruke te osobe ne stižu
12. **Baza** — otvoriti `http://localhost/phpmyadmin`, izabrati bazu `waclone` i pokazati tabele:
    ```sql
    SELECT * FROM USERS;
    SELECT * FROM MESSAGE ORDER BY SENT_AT DESC LIMIT 10;
    SELECT U.USERNAME, P.ROLE FROM PARTICIPANT P JOIN USERS U ON U.USER_ID = P.USER_ID;
    ```
13. **Offline isporuka** — zatvoriti jedan pregledač, poslati poruku (ostane jedan ✔),
    pa se ponovo prijaviti — poruka odmah stigne i dobije ✔✔
14. **Osvežavanje stranice** — pokazati da istorija i plave kvačice ostaju

---

## 12. Česta pitanja

**Zašto tri servera, zar nije moglo sve u Javi?**
Moglo je, ali cilj je bio da se razume zašto pravi WhatsApp koristi Erlang. Erlang
procesi su toliko jeftini da jedan server drži ogroman broj veza, a slanje poruka
između procesa je ugrađeno u jezik. Java bi za isto zahtevala thread pool-ove i
pažljivo zaključavanje. Podela je i praktična: svaki sloj radi ono u čemu je najbolji.

**Kako serveri znaju da je korisnik zaista prijavljen ako ne dele bazu?**
Preko deljene tajne. Spring pri prijavi izda JWT potpisan HS256 algoritmom. Kada se
klijent poveže na Erlang, šalje isti token, a Erlang ručno proverava potpis istom
tajnom i rok trajanja. Ako se potpis ne poklapa, veza se odbija. Nijedan server ne
zove drugi.

**Šta se dešava ako pošaljem poruku nekome ko nije online?**
Erlang je store-and-forward: poruka ide u red čekanja u memoriji vezan za to
korisničko ime. Pošiljalac vidi jednu kvačicu. Kad se primalac prijavi, red se
isprazni i poruke odmah stižu, a kvačice postanu dve. Server zatim zaboravlja poruku —
trajna kopija je u bazi.

**Zašto se poruka čuva na dva mesta?**
Ne čuva se dvaput. Erlang je drži samo privremeno, dok se ne isporuči. Trajna kopija
postoji samo u bazi i tamo je stavlja klijent koji je poruku poslao.

**Zašto MySQL, a ne neka ugrađena baza?**
Zato što je MySQL relacioni sistem koji se stvarno koristi i koji podržava sve što
model traži: strane ključeve, složene primarne ključeve (`Participant`, `Mute`),
`UNIQUE` ograničenja i transakcije. Aplikacija ne piše SQL ručno — Hibernate (Spring
Data JPA) mapira Java klase u tabele i sam pravi šemu, pa bi prelazak na PostgreSQL
ili neku drugu bazu značio promenu drajvera i jedne linije u konfiguraciji.

**Ko pravi tabele?**
Hibernate, iz anotacija na Java klasama u paketu `model` (`@Entity`, `@Table`,
`@ManyToOne`, `@IdClass`...). Podešavanje `spring.jpa.hibernate.ddl-auto=update`
znači da pri pokretanju uporedi klase sa postojećom šemom i doda šta nedostaje. Za
pravu produkciju bi se koristio alat za migracije (Flyway ili Liquibase), jer
`update` ne ume da uradi svaku izmenu šeme.

**Gde se čuvaju fajlovi?**
Na disku servera, u folderu `web/backend-java/media/`, pod nasumičnim imenom od 32
heksadecimalna znaka. U bazi ostaje samo opis (originalno ime, veličina, SHA-256,
putanja). Fajl se prenosi u komadima od 256 KB, pa fajl od pola gigabajta nikada nije
ceo u memoriji. Fajlovi nikada ne prolaze kroz WebSocket kojim idu poruke — isto kao
kod pravog WhatsApp-a, gde postoje zasebni serveri za medije.

**Kako radi ignorisanje korisnika?**
Server drži skup ignorisanih po korisniku. Kada A pošalje poruku B-u koji ga je
ignorisao, server je tiho odbaci: nikada se ne isporučuje i nikada ne ide u red
čekanja. A vidi samo jednu kvačicu i ne zna da je ignorisan. Isto važi za kucanje,
prisustvo, reakcije, izmene i pozive.

**Ko sme šta u grupi?**
Onaj ko napravi grupu je vlasnik i prvi administrator. Administratori mogu da dodaju
i uklanjaju članove i da dodeljuju admin ulogu. Vlasnik ne može biti degradiran —
inače bi dva administratora mogla da izbace osnivača iz sopstvene grupe. Ako vlasnik
napusti grupu, vlasništvo prelazi na najstarijeg administratora. Kada izađe poslednji
član, grupa se briše. **Sve ove provere su na serveru** — sakrivena dugmad u
interfejsu su samo udobnost, ne bezbednost.

**Da li poruke prolaze kroz server tokom poziva?**
Ne. Server prosleđuje samo dogovor o vezi (SDP i ICE kandidate). Zvuk i slika idu
direktno između dva pregledača. To se vidi u statistici WebRTC veze — tip kandidata
je `host`, dakle direktna veza.

**Zašto pozivi ne rade sa telefona preko obične adrese?**
Pregledači daju pristup kameri i mikrofonu samo u „bezbednom kontekstu" — HTTPS ili
localhost. Zato postoji i HTTPS varijanta na portu 5174 sa sopstvenim sertifikatom.

**Šta se desi ako se isti nalog prijavi dvaput?**
Nova prijava **preuzima** sesiju, a stara dobija poruku `session_replaced` i prekida
vezu — isto kao WhatsApp Web. Alternativa (odbijanje nove prijave) bi značila da vas
osvežavanje stranice zaključa iz sopstvenog naloga dok stara veza ne istekne.

**Šta se dešava kada se Erlang server restartuje?**
Grupe žive u memoriji, pa bi bile izgubljene. Zato Erlang pri pokretanju pozove
`GET /api/internal/groups` na Javi i vrati grupe sa članovima i ulogama. Taj poziv
je zaštićen deljenom tajnom, ne JWT-om, jer nije korisnički zahtev. ID-jevi grupa
nose vreme pokretanja (`g1785278716-2`) da nova grupa nikada ne bi „nasledila"
istoriju stare.

**Da li je aplikacija sigurna za pravu upotrebu?**
Nije bez dorade. Lozinke jesu ispravno heširane i saobraćaj ide preko TLS-a, ali
deljena tajna ima razvojnu podrazumevanu vrednost ako se ne postavi promenljiva
okruženja, sertifikat je samopotpisan, a web verzija nema end-to-end enkripciju. Za školski
projekat je to svesno prihvaćeno; u produkciji bi to bile prve tri stavke za popravku.

**Koliko koda ima?**
Erlang oko 1300 linija u dva fajla, Java oko 30 klasa, React oko 20 modula.
Erlang deo nema nijednu spoljnu biblioteku.

---

## 13. Testiranje

U projektu se nalazi automatska provera koja pokriva ceo sistem od kraja do kraja —
REST API, živu razmenu poruka preko WebSocket-a, grupe, medije i dnevnik poziva.

Pokretanje (dok sva tri servera rade):

```bash
node tools/smoke-test.mjs
```

Ispisuje **21 provere**, sve treba da budu `PASS`:

| Oblast | Šta se proverava |
|---|---|
| Nalozi | Registracija, odbijanje duplog imena (`UNIQUE`), pogrešna lozinka, prijava |
| Zaštita | Zahtev bez tokena vraća 401 |
| Žive poruke | Isporuka u realnom vremenu, potvrde `sent` i `read` |
| Trajnost | Poruka, status čitanja, izmena i brisanje ostaju u bazi |
| Grupe | Kreiranje, vlasnik, odbijanje neovlašćene izmene, upis uloga u bazu |
| Mediji | Otpremanje i preuzimanje fajla |
| Pozivi | Upis u dnevnik i prikaz iz ugla obe strane |

Test pravi naloge sa prefiksom `proba`, pa se posle pokretanja mogu obrisati:

```sql
DELETE FROM users WHERE username LIKE 'proba%';
```

Pored toga, sve funkcionalnosti su ručno proverene kroz stvarni interfejs u dva
pregledača — pozivi sa stvarnim WebRTC prenosom, glasovne poruke, izmena i brisanje
u realnom vremenu, i učitavanje starije istorije skrolovanjem.

---

## 14. Poznata ograničenja

- **Nema end-to-end enkripcije u web verziji** (ranija desktop verzija ju je imala) — traži prepisivanje
  Signal protokola nad Web Crypto API-jem
- **Nema grupnih poziva** — pozivi su samo 1:1
- **Pozivi sa telefona traže HTTPS** i prihvatanje samopotpisanog sertifikata
- **Istoriju beleži klijent koji šalje** — poruka poslata nečim drugim (npr. test
  skriptom) neće se videti posle osvežavanja
- **Grupe žive u memoriji Erlanga** i vraćaju se iz baze tek pri pokretanju
- **Šemu pravi Hibernate** (`ddl-auto=update`) — u produkciji bi išao alat za
  migracije poput Flyway-a, jer `update` ne pokriva svaku izmenu šeme

---

## Dodatno

- [`README.en.md`](README.en.md) — sažeta verzija ovog dokumenta na engleskom
