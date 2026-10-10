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
- **Rapoarte financiare & dashboard** — panou central cu indicatori agregați; venitul
  proprietăților separat de venitul BH Stays din comisioane (vezi mai jos)
- **Notificări** — notificări in-app pentru evenimente relevante pe rol
- **GDPR** — căutare, export și anonimizare a datelor unui oaspete la cerere
  (drepturile persoanei vizate)

## Venitul proprietăților vs. venitul BH Stays

Apartamentele aparțin proprietarilor; BH Stays păstrează doar comisionul de administrare,
configurat separat pe fiecare proprietate (`0.00–100.00%`, maximum două zecimale) din pagina
proprietății sau din formularul de editare — fără deployment. Doar `SUPER_ADMIN` și
`ADMINISTRATOR` îl pot modifica; fiecare modificare apare în audit log (doar procentele).
Rapoartele, `/finance` și deconturile le pot vedea `SUPER_ADMIN`, `ADMINISTRATOR` și
`ACCOUNTANT`; proprietarul își vede doar propriile proprietăți și deconturi.

**O singură formulă, peste tot.** Raportul proprietății, dashboardul, `/finance`, deconturile
proprietarilor și portalul proprietarului iau cifrele din același calcul
(`PropertyCommissionCalculator` prin `PropertyCommissionReportService`). Se folosesc doar bani
încasați (plăți `SUCCEEDED` / `PARTIALLY_REFUNDED` / `REFUNDED`; pending, failed, cancelled și
hold-urile nu contează), separat pe fiecare monedă, fără conversii. Pentru fiecare proprietate,
perioadă și monedă:

```
venit net proprietate = încasat − refunduri reușite
bază comisionabilă    = partea de cazare din încasat, după refunduri
venit BH Stays        = bază comisionabilă × procentul salvat pe rezervare / 100
sumă proprietar       = venit net proprietate − venit BH Stays
net de plată (decont) = sumă proprietar − cheltuieli facturate proprietarului
```

**Clasificarea componentelor**, salvate pe rezervare din cotația sistemului și reconciliate exact
cu totalul (CHECK în baza de date):

| Componentă | Comisionabilă |
|---|---|
| Cazare: tarif de bază, weekend, sezonier, dynamic pricing, minus discountul săptămânal/lunar | da |
| Taxa de curățenie | nu |
| Taxa pentru oaspeți suplimentari | nu |
| Late checkout | nu |
| Taxe | nu |
| Addon-uri | nu |

Motorul de prețuri nu include late checkout, taxe sau addon-uri în totalul rezervării, deci
acestea sunt 0 în snapshot; o plată separată pentru late checkout, peste o rezervare plătită
integral, nu este comisionată (partea de cazare e plafonată la valoarea din snapshot). Un refund
parțial reduce baza proporțional.

**Procentul se salvează pe rezervare.** La crearea rezervării (staff, rezervare publică, import
iCal) procentul proprietății se copiază în `management_commission_percent_snapshot`. Rapoartele și
deconturile folosesc acest snapshot, nu procentul curent: dacă procentul proprietății se schimbă
din 20% în 25%, rezervările existente rămân la 20% și doar cele noi folosesc 25%.

**Perioada după tranzacție.** O încasare intră în perioada în care a fost capturată, un refund în
perioada în care a reușit (data din ledgerul `payment_transactions`), iar reducerea comisionului
intră în aceeași perioadă cu refundul. Un refund ulterior nu modifică retroactiv luna încasării:
apare ca ajustare negativă în perioada lui. Perioadele sunt zile calendaristice în ora României.
Cheltuielile intră în perioadă după data cheltuielii.

**Istoric.** Rezervările fără defalcare verificabilă sau fără snapshot de procent (create înainte
de snapshot, cu un total diferit de cotația sistemului, sau cât timp proprietatea nu avea procent)
apar ca „fără defalcare”: banii încasați intră în venitul net și în suma proprietarului, dar nu se
calculează comision pe ei, și sunt numărați separat în rapoarte și deconturi. Nu se estimează și nu
se completează automat niciun procent istoric.

**Deconturi.** Un decont emis nu se mai modifică; un refund făcut după emitere apare, cu reducerea
comisionului, în decontul perioadei în care a fost făcut. Un decont nu poate acoperi zile deja
incluse într-un alt decont al aceluiași proprietar în aceeași monedă, ca nicio tranzacție să nu fie
numărată de două ori. Deconturile emise înainte de această formulă sunt marcate `LEGACY_GROSS` și
rămân exact cum au fost emise. Fiecare decont acoperă o singură monedă.

**Mai multe monede.** Sursa oficială în API sunt listele pe monede (`revenueByCurrency`,
`totalRevenueByCurrency`, `totals`); RON și EUR nu se adună niciodată. Câmpurile vechi cu o singură
valoare sunt deprecated: cu o singură monedă conțin valoarea și codul ei, cu mai multe monede sunt
`null` — nu se alege implicit RON și nu se returnează un total mixt.

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
