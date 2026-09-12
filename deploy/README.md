# BH Group production deployment

Ez a leírás a `bhstays.ro` egygépes production telepítéséhez készült Ubuntu
26.04 LTS, Docker Engine 29 és Docker Compose v5 környezetre. A development
`docker-compose.yml` változatlan marad; élesben mindig a
`compose.production.yml` fájlt kell explicit megadni.

## Architektúra

| Service | Belső kapcsolat | Host port | Perzisztens adat |
| --- | --- | --- | --- |
| `caddy` | frontend `3000`, backend `8080` | TCP `80`, `443` | `caddy_data`, `caddy_config` |
| `frontend` | Caddy felől `3000` | nincs | nincs |
| `backend` | Caddy felől `8080`, PostgreSQL felé `5432` | nincs | `uploads` |
| `postgres` | backend felől `5432` | nincs | `postgres_data` |

A frontend-, backend- és adatbázis-kapcsolat külön bridge hálózatot használ.
Az adatbázis-hálózat `internal`; a hostról kizárólag Caddy 80/443 TCP portjai
publikáltak. A 4 GB RAM-os VPS védelmére konténerenkénti memórialimit és
forgó Docker logok vannak beállítva.

## Első telepítés

### 1. DNS, hálózat és repository

A Hetzner firewallban kizárólag TCP 22, 80 és 443 maradjon nyitva. A
Cloudflare DNS-ben hozd létre az alábbi rekordokat, kezdetben **DNS-only**
(szürke felhő) módban:

- `A @ <VPS_PUBLIC_IPV4>`
- `CNAME www bhstays.ro`

Ne írj valós IP-címet repository-fájlba. A DNS propagáció után ellenőrizd,
hogy mindkét név a VPS-re oldódik fel.

Első klónozáskor:

```bash
git clone https://github.com/PaulHiticas/BH-Group.git /opt/bhgroup
cd /opt/bhgroup
git switch ops/production-deployment
git status --short
```

A további Git-frissítések kontrollált menetét lásd lejjebb. Ne deployolj
ismeretlen vagy módosított working tree-ből.

### 2. Production environment

```bash
cd /opt/bhgroup
umask 077
cp .env.production.example .env.production
chmod 600 .env.production
```

Privát terminálban generálj külön értéket minden titokhoz:

```bash
# PostgreSQL-jelszó (36 random byte Base64 formában)
openssl rand -base64 36 | tr -d '\n'; echo

# JWT secret: legalább 64 random byte, egyetlen Base64 sorban
openssl rand -base64 64 | tr -d '\n'; echo

# Egyedi kezdeti SUPER_ADMIN-jelszó
openssl rand -base64 36 | tr -d '\n'; echo
```

Írd az értékeket közvetlenül a csak root/deploy user által olvasható
`.env.production` fájlba. A következő négy változó kötelező, és a Compose
hiányuk esetén már konfiguráció-ellenőrzéskor leáll:

- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `SUPER_ADMIN_EMAIL`
- `SUPER_ADMIN_PASSWORD`

A `SUPER_ADMIN_EMAIL` egy kezdeti, ellenőrzött admin postafiók legyen. A
publikus URL-ek maradjanak `https://bhstays.ro` értéken, a
`REFRESH_COOKIE_SECURE` pedig `true`. Állítsd az `APP_IMAGE_TAG` értékét a
deployolt commit rövid SHA-jára (`git rev-parse --short=12 HEAD`); ne használj
`latest` taget.

Az SMTP, Anthropic, Google Maps és Stripe változók opcionálisak. Az üres SMTP
hitelesítő adatok letiltják a tényleges levélküldést, az üres Anthropic kulcs
a kapcsolatfelvételi fallbacket hagyja aktívan, az üres Stripe mezők pedig
kikapcsolják a kártyás fizetést. A böngészőbe kerülő `NEXT_PUBLIC_*` értékek
nem titkok; a Google Maps kulcsot ettől függetlenül HTTP referrer alapján
korlátozni kell a szolgáltatói konzolban.

A placeholder ellenőrzése után validáld a konfigurációt úgy, hogy a renderelt
(titkokat is tartalmazó) Compose konfigurációt nem írod ki:

```bash
if grep -Eq 'REPLACE_WITH|example\.invalid' .env.production; then
  echo 'STOP: production placeholder maradt az env fájlban' >&2
  exit 1
fi
docker compose --env-file .env.production -f compose.production.yml config --quiet
```

### 3. Build, migráció és indítás

```bash
docker compose --env-file .env.production -f compose.production.yml build --pull
docker compose --env-file .env.production -f compose.production.yml up -d
docker compose --env-file .env.production -f compose.production.yml ps
```

A backend induláskor, az alkalmazás kiszolgálása előtt automatikusan futtatja
a verziózott Flyway migrációkat. A PostgreSQL healthcheck sikeréig a backend,
a backend healthcheck sikeréig a frontend és Caddy nem indul tovább. Sikertelen
migrációnál ne futtass kézzel ad-hoc SQL-t: vizsgáld meg a migráció hibáját,
majd javított commitból vagy dokumentált restore/rollback alapján folytasd.

Elvárt állapot: `postgres`, `backend` és `frontend` `healthy`, `caddy` pedig
`running`. Külső ellenőrzések:

```bash
curl --fail --show-error --head https://bhstays.ro/
curl --fail --show-error --head https://www.bhstays.ro/
curl --silent --output /dev/null --write-out '%{http_code}\n' https://bhstays.ro/actuator/health
curl --silent --output /dev/null --write-out '%{http_code}\n' https://bhstays.ro/v3/api-docs
```

A `www` kérés permanens átirányítást ad a gyökérdomainre, a két tiltott backend
útvonal pedig kívülről `404` legyen. A Caddy automatikusan szerzi és újítja a
tanúsítványokat a perzisztens `caddy_data` volume-ban.

Mivel az SMTP opcionális, annak Actuator health indicátora productionben ki
van kapcsolva; különben a szándékosan üres SMTP-konfiguráció az egész backendet
`DOWN` állapotúnak jelölné. SMTP bekapcsolásakor küldj kontrollált tesztlevelet,
és a szolgáltatói kézbesítési naplóban is ellenőrizd az eredményt.

### 4. Logok biztonságos ellenőrzése

```bash
docker compose --env-file .env.production -f compose.production.yml logs --tail=100 postgres backend frontend caddy
```

A parancs nem írja ki a konténerek environmentjét. Ne használd hibajegyben vagy
megosztott terminálon a `docker compose config` nem-quiet változatát, a
`docker inspect` environment kimenetét, illetve az `.env.production` tartalmát.
Mielőtt logrészletet továbbítasz, ellenőrizd, hogy nincs benne e-mail, token,
foglalási adat vagy más személyes adat.

### 5. Cloudflare TLS és kliens-IP

Az első tanúsítvány sikeres kiadásáig a rekordok maradjanak DNS-only módban.
A Cloudflare SSL/TLS módja proxied használat előtt kizárólag **Full (strict)**
lehet; a **Flexible** mód tilos.

A stock Caddy konfiguráció nem állít be `trusted_proxies` forrást, ezért Caddy
figyelmen kívül hagyja a kliens által küldött `X-Forwarded-*` értékeket, és a
közvetlen peerből építi fel őket. Az alternatív `Forwarded`, `X-Real-IP` és
Cloudflare kliens-IP fejléceket a proxy eltávolítja. Ez megakadályozza az
IP-hamisítást. Proxied módban viszont a backend így a Cloudflare edge IP-jét
látná, ami gyengítené az IP-alapú rate limit pontosságát.
Ezért a rekordok a tanúsítvány kiadása után is maradjanak DNS-only módban
mindaddig, amíg egy ellenőrzött megoldás kizárólag a Cloudflare aktuális,
hivatalos proxy tartományaitól fogadja el a kliens-IP fejlécet, és ezt staging
környezetben tesztelték. A proxied mód csak ezután opcionális, Full (strict)
beállítással.

## Kontrollált frissítés

Minden deploy előtt rögzítsd az aktuális SHA-t és készíts adatbázis-backupot.
Csak fast-forward frissítést fogadj el:

```bash
cd /opt/bhgroup
git branch --show-current
git status --short
git rev-parse HEAD
./deploy/backup-postgres.sh

git fetch --prune origin
git log --oneline HEAD..origin/ops/production-deployment
git diff --stat HEAD..origin/ops/production-deployment
git merge --ff-only origin/ops/production-deployment
```

Ha a branch vagy a working tree nem a várt állapotú, állj meg. A merge után
írd az új rövid SHA-t az `.env.production` `APP_IMAGE_TAG` mezőjébe, majd:

```bash
docker compose --env-file .env.production -f compose.production.yml config --quiet
docker compose --env-file .env.production -f compose.production.yml build --pull
docker compose --env-file .env.production -f compose.production.yml up -d
docker compose --env-file .env.production -f compose.production.yml ps
```

## Backup és restore

A `deploy/backup-postgres.sh` custom-format `pg_dump` fájlt hoz létre atomikusan
a Gitből kizárt `backups/` könyvtárban. Nem tartalmaz hardcoded jelszót, nem
ír ki titkot, hiba vagy üres dump esetén non-zero kóddal tér vissza. Alapból
14 napot tart meg; például napi futtatás root crontabból:

```cron
0 3 * * * cd /opt/bhgroup && BACKUP_RETENTION_DAYS=14 ./deploy/backup-postgres.sh >> /var/log/bhgroup-backup.log 2>&1
```

Restore előtt készíts új backupot, azonosítsd egyértelműen a visszaállítandó
fájlt, és számolj kieséssel. A következő művelet destruktív: a dumpban szereplő
adatbázis-objektumokra cseréli az aktuálisakat.

```bash
cd /opt/bhgroup
BACKUP_FILE=/opt/bhgroup/backups/bhgroup_postgres_YYYYMMDDTHHMMSSZ.dump
test -r "$BACKUP_FILE"
./deploy/backup-postgres.sh
docker compose --env-file .env.production -f compose.production.yml stop caddy frontend backend
docker compose --env-file .env.production -f compose.production.yml exec -T postgres \
  sh -c 'exec pg_restore --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --clean --if-exists --no-owner --no-acl --exit-on-error' \
  < "$BACKUP_FILE"
docker compose --env-file .env.production -f compose.production.yml up -d
docker compose --env-file .env.production -f compose.production.yml ps
```

Az ugyanazon a VPS-en tárolt backup **önmagában nem elegendő**: szerver- vagy
lemezhiba esetén az eredeti adatokkal együtt elveszhet. A következő külön
üzemeltetési feladat egy titkosított, hozzáférés-korlátozott off-site másolat
beállítása és rendszeres próba-restore. Ez a konfiguráció most nem kapcsol be
fizetős storage szolgáltatást. Az adatbázis-dump az `uploads` volume fájljait
nem tartalmazza; azokról is külön off-site másolat szükséges.

## Rollback

Alkalmazás-rollbackhez válassz ellenőrzött, ismert jó commitot, és előbb
ellenőrizd, hogy annak kódja kompatibilis-e a már lefutott Flyway sémával.
A Flyway migrációk előremenők; egy régebbi image indítása nem vonja vissza az
adatbázis-módosításokat.

Sürgős visszaálláskor, friss backup után ideiglenesen checkoutolható az ismert
jó SHA, az `APP_IMAGE_TAG` ugyanerre a SHA-ra állítható, majd újra buildelhető
és indítható a stack. A javítás után térj vissza az
`ops/production-deployment` branchre. Ha a séma nem kompatibilis, az alkalmazás
rollback önmagában tilos; a fent dokumentált, kieséssel járó adatbázis-restore
és az uploads kompatibilitásának külön ellenőrzése szükséges.

```bash
KNOWN_GOOD_SHA=<ellenorzott-production-commit-SHA>
git cat-file -e "${KNOWN_GOOD_SHA}^{commit}"
git switch --detach "$KNOWN_GOOD_SHA"
export APP_IMAGE_TAG="$(git rev-parse --short=12 HEAD)"
docker compose --env-file .env.production -f compose.production.yml config --quiet
docker compose --env-file .env.production -f compose.production.yml build
docker compose --env-file .env.production -f compose.production.yml up -d
docker compose --env-file .env.production -f compose.production.yml ps
```

A helyreállítás után commitold és deployold a javítást a kijelölt branchen;
ne merge-elj vagy pusholj közvetlenül a production VPS-ről.

## Stripe későbbi bekapcsolása

Alapállapotban mindhárom Stripe változó üres, ezért a kártyás fizetés ki van
kapcsolva. Bekapcsoláskor a Stripe Dashboardban a webhook URL:

```text
https://bhstays.ro/api/v1/public/payments/webhook/stripe
```

Az endpoint számára legalább a `checkout.session.completed`,
`payment_intent.succeeded` és `payment_intent.payment_failed` eseményeket
állítsd be. A Dashboardból származó live `STRIPE_SECRET_KEY`,
`STRIPE_PUBLISHABLE_KEY` és az adott endpoint `STRIPE_WEBHOOK_SECRET` értéke
csak `.env.production` fájlba kerülhet. Ezután kontrolláltan hozd újra létre a
backendet, és teszteld egy Stripe által aláírt test eseménnyel; ne küldj kézzel
aláíratlan payloadot productionre.

## A bootstrap admin jelszó kezelése

Az első sikeres SUPER_ADMIN belépés után azonnal állíts be új, egyedi jelszót
az alkalmazásban, és engedélyezd az MFA-t. Ezután cseréld le az
`.env.production` `SUPER_ADMIN_PASSWORD` értékét egy új, hosszú random,
sehol máshol nem használt értékre. A változó a Compose validáció miatt nem
lehet üres, de meglévő SUPER_ADMIN esetén a bootstrap kód nem használja.
Ezzel az eredeti kezdeti jelszó nem marad újrahasználható titokként a szerveren.
