# BH Group — Property Management Platform

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
- **Plăți** — tranzacții manuale, Stripe și Netopia, webhook-uri de confirmare
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
# editează .env și schimbă SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD și JWT_SECRET
# MAIL_* trebuie schimbate doar când folosești un server SMTP real
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Local email inbox (Mailpit): http://localhost:8025

În configurația locală implicită, emailurile sunt capturate de Mailpit și pot fi
inspectate în browser. Pentru un mediu găzduit, înlocuiește valorile `MAIL_*` cu
serverul SMTP real și activează autentificarea/TLS după cerințele furnizorului.

La primul start, dacă `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` sunt setate și nu
există încă niciun cont `SUPER_ADMIN`, backend-ul creează automat primul cont de
administrator al platformei. Nu există înregistrare publică — conturile de staff se
creează exclusiv prin invitație de la un administrator, iar oaspeții nu au cont, doar
rezervări identificate prin email/token.

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
