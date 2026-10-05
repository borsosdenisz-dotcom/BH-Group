# BH Stays — Property Management Platform

Platformă de Property Management pentru închirieri pe termen scurt (Airbnb, Booking.com
și rezervări directe), construită pentru scalare internațională.

## Stack tehnologic

**Frontend**: Next.js 15 (App Router), React 19, TypeScript, Tailwind CSS v4, shadcn/ui,
Framer Motion, TanStack React Query, React Hook Form + Zod, Zustand.

**Backend**: Java 21, Spring Boot 3, Spring Security, JWT + Refresh Token, PostgreSQL,
Flyway, MapStruct, Maven, Docker, springdoc-openapi (Swagger).

**Arhitectură**: Clean Architecture / layered (entity → repository → service →
controller), DTO pattern, global exception handling, audit logging, environment-based
configuration, Docker Compose.

## Module implementate

- **Autentificare & securitate** — login/refresh/logout cu JWT (refresh token rotit și
  stocat hash-uit), 2FA obligatoriu (TOTP, coduri de recuperare hash-uite), invitații de
  cont, resetare parolă, RBAC pe 7 roluri (`SUPER_ADMIN`, `ADMINISTRATOR`, `OWNER`,
  `CLEANER`, `MAINTENANCE`, `ACCOUNTANT`, `SUPPORT_AGENT`), rate limiting pe
  endpoint-urile sensibile, audit log
- **Properties** — CRUD proprietăți, facilități, fotografii, documente, prețuri, adresă
  cu control de confidențialitate publică, configurare late checkout
- **Rezervări** — creare/editare, calendar, protecție la suprapunere (constraint la
  nivel de bază de date), cod de acces check-in, late checkout, mesagerie oaspete-staff
- **Booking engine public** — căutare și disponibilitate publică, cerere de rezervare
  fără cont, gestionare rezervare prin link cu token dedicat
- **Pricing engine** — tarife sezoniere/weekend, taxă de curățenie, taxă oaspete
  suplimentar, discount săptămânal/lunar, validare min/max nopți
- **Curățenie** — sarcini de curățenie legate de rezervări, portal dedicat pentru
  cleaneri
- **Mentenanță** — tichete de mentenanță, portal dedicat pentru echipa de mentenanță
- **Plăți** — plată cu cardul prin Stripe Checkout găzduit (formularul de card stă pe
  domeniul Stripe, datele cardului nu ajung pe serverele noastre), cu webhook verificat
  prin semnătură care confirmă rezervarea, idempotent la livrări repetate, plus rambursări
  automate la anulare conform politicii; alternativ, evidență manuală a tranzacțiilor
  (`ManualPaymentGateway`) pentru transfer bancar / plată la sosire. Plata cu cardul se
  activează doar dacă `STRIPE_SECRET_KEY` e setat — altfel rămâne doar varianta manuală
- **Cheltuieli** — înregistrare cheltuieli pe proprietate, atașare chitanțe
- **Decontări proprietari** — generare și urmărire deconturi (owner statements)
- **Portal proprietari** — acces la proprietățile, rezervările, cheltuielile și
  deconturile proprii
- **Sincronizare iCal** — import/export calendare Airbnb și Booking.com
- **Lead-uri** — capturare lead-uri și cereri de estimare venit din site-ul public
- **Rapoarte financiare & dashboard** — panou central cu indicatori agregați
- **Notificări** — notificări in-app pentru evenimente relevante pe rol
- **GDPR** — căutare, export și anonimizare a datelor unui oaspete la cerere
  (drepturile persoanei vizate)

## Rulare locală

### Cu Docker Compose (recomandat)

```bash
cp .env.example .env
# editează .env și completează SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD, JWT_SECRET, MAIL_*
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html

La primul start, dacă `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` sunt setate și nu
există încă niciun cont `SUPER_ADMIN`, backend-ul creează automat primul cont de
administrator al platformei. Nu există înregistrare publică — conturile de staff se
creează exclusiv prin invitație de la un administrator, iar oaspeții nu au cont, doar
rezervări identificate prin email/token.

## Migrări de bază de date

Fișierele din `backend/src/main/resources/db/migration/` sunt **imuabile odată aplicate**.
Flyway reține un checksum pentru fiecare migrare rulată; dacă un fișier deja aplicat e
modificat — fie și doar un comentariu — validarea eșuează și backend-ul refuză să
pornească. De aceea migrările vechi încă spun „BH Group" în anteturi: e istoric, nu o
scăpare de la redenumire. Orice schimbare de schemă se face într-o migrare **nouă**, cu
următorul număr liber.

## Producție (bhstays.ro)

Față de rularea locală, în producție diferă strict configurarea — nu codul:

| Variabilă | Local | bhstays.ro |
|---|---|---|
| `APP_BASE_URL` | `http://localhost:3000` | `https://bhstays.ro` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | `https://bhstays.ro` |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080/api/v1` | `https://bhstays.ro/api/v1` |
| `UPLOAD_PUBLIC_BASE_URL` | `http://localhost:8080/uploads` | `https://bhstays.ro/uploads` |
| `REFRESH_COOKIE_SECURE` | `false` | **`true`** (altfel sesiunea nu se păstrează pe HTTPS) |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |

Stripe: cheile `sk_live_`/`pk_live_` se pun doar când chiar vrei să încasezi bani reali
(cu chei `sk_test_` plățile sunt simulate). Webhook-ul trebuie înregistrat în dashboard-ul
Stripe către `https://bhstays.ro/api/v1/public/payments/webhook/stripe`, iar
`STRIPE_WEBHOOK_SECRET` trebuie să fie secretul acelui endpoint — altfel livrările sunt
respinse cu 400 la verificarea semnăturii și rezervările plătite nu se confirmă singure.
Endpointul trebuie abonat la evenimentele `checkout.session.completed`,
`checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`,
`checkout.session.expired` și `payment_intent.payment_failed`.

Rezervarea directă plătită cu cardul se confirmă automat **doar** din webhook-ul semnat
(`payment_status=paid`, sumă și monedă identice cu cele calculate pe server) — revenirea
clientului pe `/plata/succes` nu confirmă nimic. Administratorii primesc notificarea
„Rezervare nouă plătită și confirmată” abia după confirmare.

Rezervarea publică este **exclusiv cu cardul**: backend-ul respinge (400) orice altă metodă
trimisă de client (`BANK_TRANSFER`, `ON_ARRIVAL` etc.). Fără Stripe configurat — sau dacă
Stripe nu poate deschide sesiunea — nu se creează niciun HOLD și nicio rezervare (503), iar
site-ul afișează că rezervarea online nu este momentan disponibilă, cu datele de contact.
Plățile manuale (transfer, numerar, POS) rămân disponibile doar administratorilor autentificați.

Dacă mediul a fost creat **înainte** de redenumirea în BH Stays, baza de date încă se
numește `bhgroup_pms` cu userul `bhgroup`. Schimbarea variabilelor din compose nu
redenumește nimic (au efect doar la prima inițializare), deci rulează o singură dată:

```bash
./scripts/rename-db-to-bhstays.sh   # face backup, redenumește baza și rolul
docker compose up -d --build
```

### Rulare separată (dezvoltare)

**Backend**:

```bash
cd backend
./mvnw spring-boot:run
```

Necesită o instanță PostgreSQL locală (vezi `docker-compose.yml` pentru variabilele de
mediu așteptate) sau `docker compose up postgres`.

**Frontend**:

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
```

## Structura proiectului

```
backend/    Spring Boot API (Java 21, Maven)
frontend/   Next.js 15 App Router (TypeScript)
docs/       Documentație tehnică
```

## Note de securitate

- Parolele sunt hash-uite cu BCrypt (cost factor 12)
- Refresh token-urile sunt rotite la fiecare folosire și stocate hash-uit (SHA-256) în
  baza de date, nu în clar
- 2FA este obligatoriu pentru toate conturile de staff, folosește TOTP (RFC 6238,
  compatibil cu Google Authenticator / Authy) și coduri de recuperare hash-uite,
  cu resetare disponibilă doar de către un `SUPER_ADMIN`
- Toate secretele (JWT, DB, SMTP) se configurează exclusiv prin variabile de mediu —
  nu există secrete hardcodate în cod
