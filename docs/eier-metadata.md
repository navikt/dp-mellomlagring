# Eier-metadata og RFC 2047-koding fra GCS

Hver fil har eieren (ident) lagret kryptert i GCS-metadata. Fra 21. oktober 2026 returnerte GCS i en periode feltet `eier` kodet og med endrede linjeskift. Dette dokumentet forklarer hva som skjedde, og hvorfor vi har to eierfelt.

## Slik lagres eieren

`KryptertStore` krypterer fødselsnummeret med Tink (KMS envelope-AEAD). Fødselsnummeret brukes også som associated data. Resultatet er 141 eller 142 tilfeldige bytes, og de lagres i to metadatafelt:

| Felt | Format | Skrives av |
|---|---|---|
| `eier` | Rå bytes tolket som ISO-8859-1-streng | Alle versjoner |
| `eier_v2` | Base64 av de samme bytene | Fra 21. oktober 2026 kl. 11.38 |

Filer fra før det tidspunktet har bare `eier`. Eierskapssjekken bruker `eier_v2` når feltet finnes, og ellers `eier`.

## Hva som skjedde

Fra 21. oktober 2026 kl. 01.25 UTC gjorde GCS to ting med binær metadata før den ble returnert:

1. Både `\n` og `\r` ble til `\r\n`.
2. Resultatet ble RFC 2047-kodet, for eksempel `=?UTF-8?Q?=00=00=00c=0D=0A=24...?=`.

Steg 1 kan ikke reverseres, så AEAD-dekrypteringen feilet og appen svarte 403 for gamle filer. Vi la til `eier_v2` for nye filer og dekoding med kandidater for gamle filer.

Google bekreftet 16. november 2026 at endringen var ment å gjøre XML-API-et RFC 9110-kompatibelt, der metadata returneres som `x-goog-meta-*`-headere. Den lå i en felles komponent og traff derfor også JSON-API-et. Google har bekreftet dette:

- JSON-API-et og gRPC returnerer metadata som før, også med linjeskift.
- Kodingen er slått av til den bare gjelder XML-API-et.
- Fremtidige endringer i XML-API-et blir varslet i release notes.

16. november 2026 returnerte dev-bucketen `eier` rått for alle 4945 filer uten `eier_v2`. Dekodingen er derfor fjernet.

## Hvorfor vi beholder `eier_v2`

Base64 tåler at GCS endrer håndteringen av binær metadata igjen. Det gjelder også hvis vi tar i bruk XML-API-et. Ikke fjern `eier_v2` eller begynn å lese bare `eier` på nytt.

## Hvis det skjer igjen

Kommer `eier` kodet tilbake igjen, får gamle filer 403 med `Fnr matcher ikke fødselsnummer på ressurs` i sikkerloggen. Dekodingen som fantes, ligger i git-historikken (`KodetEier.kt`).

## Veien videre

- Slutte å skrive `eier` når alle filer har `eier_v2`.
