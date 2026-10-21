# Eier-metadata og RFC 2047-koding fra GCS

Hver fil har eieren(ident) lagret kryptert i GCS-metadata. Siden 21. oktober 2026 returnerer GCS feltet `eier` kodet og med endrede linjeskift. Derfor kan vi ikke lese eieren direkte for filer som er lastet opp før 21. oktober 2026. Dette dokumentet forklarer hvorfor, hvordan appen håndterer det, og hva du gjør når en fil likevel feiler.

## Slik lagres eieren

`KryptertStore` krypterer fødselsnummeret med Tink (KMS envelope-AEAD). Fødselsnummeret brukes også som associated data. Resultatet er 141 eller 142 tilfeldige bytes, og de lagres i to metadatafelt:

| Felt | Format | Skrives av |
|---|---|---|
| `eier` | Rå bytes tolket som ISO-8859-1-streng | Alle versjoner |
| `eier_v2` | Base64 av de samme bytene | Fra 21. oktober 2026 kl. 11.38 |

Filer fra før det tidspunktet har bare `eier`.

## Hva GCS gjør med `eier`

Når GCS returnerer binær metadata, skjer to ting med verdien:

1. Både `\n` og `\r` blir til `\r\n`.
2. Resultatet blir RFC 2047-kodet, for eksempel `=?UTF-8?Q?=00=00=00c=0D=0A=24...?=`.

Steg 2 kan reverseres. Steg 1 kan ikke, fordi vi ikke vet om en `\r\n` opprinnelig var `\n`, `\r` eller `\r\n`.

Vi har testet JSON-, XML- og gRPC-API-et mot filer der fasiten ligger i `eier_v2`. Alle tre returnerte samme kodede verdi, og ingen av dem returnerte de opprinnelige bytene.

Vi så kodede verdier første gang 21. oktober 2026. Om formiddagen gjaldt det noen av kallene for gamle filer. Fra ettermiddagen gjaldt det alle kallene vi sjekket. Vi vet ikke om endringen er permanent.

## Slik løser appen det

`eier_v2` er base64 og blir ikke endret av GCS. Nye filer bruker det feltet.

For gamle filer uten `eier_v2` gjør [`KodetEier`](../src/main/kotlin/no/nav/dagpenger/mellomlagring/lagring/KodetEier.kt) dette:

1. Den dekoder RFC 2047 med `QCodec` eller `BCodec` fra commons-codec.
2. Den deler verdien på hver `\r\n` og lager alle mulige originaler, de såkalte kandidatene. Hvert tvetydig sted kan være `\n`, `\r` eller `\r\n`, så n steder gir 3ⁿ kandidater.
3. Den sorterer kandidatene med færrest `\r\n` først og deretter færrest `\r`. Ekte `\r\n` i ciphertext er sjeldent.

`KryptertStore.erEierAvKodet` prøver kandidatene med `aead.decrypt(kandidat, fnr)`. Bare den nøyaktig riktige originalen kan dekrypteres med fødselsnummeret til den som spør. Feil person kan derfor aldri få tilgang, uansett hvor mange kandidater som prøves.

Eksempel: originalen `A B \n C D` kommer tilbake som `A B \r\n C D`. Kandidatene blir da `A B \n C D`, `A B \r C D` og `A B \r\n C D`.

### Grenser

Hver kandidat koster ett KMS-kall. Derfor har koden to grenser:

- `MAKS_TVETYDIGE = 6`: Har filen flere tvetydige steder enn det, gir vi opp med en gang.
- `MAKS_KANDIDATER = 32`: Vi prøver maksimalt 32 kandidater.

I prod har riktig kandidat stort sett vært nummer 1, og det høyeste vi har sett, er nummer 6. Når riktig person spør, gir dette omtrent 25 % flere KMS-kall for gamle filer. Når feil person spør, prøves alle kandidatene, opptil 32 KMS-kall.

### Når det feiler

Finner vi ingen kandidat, svarer appen 403, som før.

Vi anslår at dette gjelder rundt 0,2 % av de gamle filene som blir lest. Tallet er regnet ut, ikke målt. Utregningen bygger på at omtrent 130 av de 141 bytene er tilfeldige, og at hver av dem har sannsynlighet 2/256 for å være `\n` eller `\r`. Byte 4 er alltid `\n`.

| Tvetydige steder | Andel filer | Resultat |
|---|---|---|
| 1–5 | ~99,6 % | Alle kandidater uten `\r\n` blir prøvd |
| 6 | ~0,33 % | Bare 32 av 64 blir prøvd, så omtrent halvparten feiler |
| 7 eller flere | ~0,07 % | Vi gir opp |

## Overvåking

- Metrikk: `dp_mellomlagring_eier_metadata_dekodet_count{resultat="ok"|"feilet"}`
- Logglinje per sjekk, uten fødselsnummer: `Eier-metadata var RFC 2047-kodet ved <objekt>. Kandidater: X, forsøk: Y, lyktes: true|false`

## Manuell fiks for en fil som feiler

Med fødselsnummeret til eieren kan vi finne riktig kandidat utenfor appen, uten grensene, og skrive den til `eier_v2`. Fødselsnummeret finner vi ut fra søknad-ID-en, som er første del av objektnavnet.

1. Finn objektnavnet i loggen (`lyktes: false`).
2. Finn fødselsnummeret via søknad-ID-en.
3. Lag alle kandidatene og dekrypter hver av dem med KMS-nøkkelen `dp-mellomlagring` og fødselsnummeret som associated data. Bare én kandidat dekrypterer.
4. Skriv den kandidaten base64-kodet til `eier_v2`:

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{"metadata":{"eier_v2":"<base64>"}}' \
  "https://storage.googleapis.com/storage/v1/b/teamdagpenger-mellomlagring-prod/o/<url-kodet objektnavn>"
```

5. Sjekk at filen kan hentes.

> [!CAUTION]
> Fødselsnummeret skal bare ligge i minnet. Det skal ikke inn i filer, logger eller shell-historikk. Steg 3 krever decrypt-tilgang til KMS-nøkkelen i prod.

Det finnes ikke noe ferdig verktøy for steg 3 ennå. Fire filer ble fikset 21. oktober 2026 ved å prøve kandidat 1 og 2 direkte.

## Veien videre

- Slutte å skrive `eier` når alle filer har `eier_v2`.
- Få bekreftet fra Nais eller Google om endringen er permanent.
