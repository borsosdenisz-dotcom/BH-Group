# Fluxul public OTA-first

## Scop

În etapa OTA-first, `bhstays.ro` prezintă proprietățile și colectează solicitări, iar disponibilitatea și rezervarea finală se verifică pe platforma externă configurată pentru proprietate (Airbnb și/sau Booking.com). Rezervarea publică directă rămâne dezactivată implicit până când plățile și sincronizarea inventarului sunt validate pentru producție.

Această etapă nu adaugă integrare directă cu API-urile Airbnb sau Booking.com și nu automatizează emailul de check-in.

## Feature flag pentru rezervarea directă

Backendul citește:

```text
PUBLIC_DIRECT_BOOKING_ENABLED=false
```

Valoarea implicită este `false`. Prezența cheilor Stripe nu activează automat rezervarea directă. Endpointul public de configurare raportează rezervarea directă drept activă numai dacă flagul este `true` și Stripe este configurat. Serviciul de rezervări verifică flagul separat, astfel încât apelurile API manuale nu îl pot ocoli.

Când flagul este oprit:

- crearea publică a unei rezervări și checkout-ul public răspund cu codul stabil `DIRECT_BOOKING_DISABLED`;
- formularul public de cumpărare și CTA-urile de rezervare directă nu sunt afișate;
- proprietățile publicate și tarifele orientative rămân vizibile;
- rezervările existente, operațiunile administrative și rezervările introduse manual nu sunt modificate.

## Configurarea linkurilor OTA

În dashboard, deschide formularul de creare sau editare al proprietății și completează secțiunea **Platforme de rezervare**:

- **Link anunț Airbnb** pentru pagina publică a anunțului Airbnb;
- **Link anunț Booking.com** pentru pagina publică a anunțului Booking.com.

Câmpurile sunt opționale și nu sunt completate automat. Sunt acceptate numai URL-uri HTTPS de pe domeniile oficiale validate pentru platforma corespunzătoare. URL-urile relative, schemele `javascript:`/`data:`, domeniile asemănătoare și URL-urile cu autentificare încorporată sunt respinse atât în formular, cât și în backend.

Pentru o proprietate publicată, pagina publică afișează linkurile configurate. Dacă nu există niciun link, CTA-ul **Solicită disponibilitatea** deschide formularul de lead existent. Proprietățile demo nu afișează linkuri externe de rezervare.

## Calendar și iCal

iCal este o sincronizare periodică, nu un channel manager cu inventar aproape în timp real. Un calendar iCal nu garantează disponibilitate real-time și nu trebuie folosit singur pentru activarea rezervării directe. Disponibilitatea și condițiile finale se verifică pe platforma externă indicată.

## Condiții înainte de activarea rezervării directe

Flagul poate fi activat ulterior numai după validarea cumulativă a următoarelor condiții:

1. Stripe este configurat în modul production cu cheile și setările corecte.
2. Webhook-ul Stripe este configurat, semnătura este verificată și evenimentele sunt procesate idempotent.
3. Un channel manager este configurat pentru canalele folosite.
4. Sincronizarea disponibilității a fost validată în scenarii concurente și cu întârzieri/reîncercări.
5. A fost executat cap-coadă un test de rezervare, plată, confirmare, anulare și refund.
6. Proprietățile eligibile sunt active, non-demo și au reguli/prețuri verificate.

După îndeplinirea lor, setează explicit `PUBLIC_DIRECT_BOOKING_ENABLED=true` în configurația mediului autorizat și repornește backendul. Nu salva valori reale de producție în repository.

## Rollback

Pentru a opri imediat inițierea unor noi rezervări publice directe:

1. setează `PUBLIC_DIRECT_BOOKING_ENABLED=false` în configurația mediului;
2. repornește backendul și verifică endpointul public de configurare;
3. confirmă că POST-ul public de rezervare răspunde cu `DIRECT_BOOKING_DISABLED` și că UI-ul afișează opțiunile OTA/lead;
4. monitorizează separat rezervările și plățile deja inițiate — rollback-ul flagului nu le șterge și nu le modifică;
5. investighează și validează remedierea înainte de o eventuală reactivare.

Rollback-ul flagului nu necesită rollback-ul migrării: coloanele OTA sunt nullable și compatibile cu proprietățile existente.
