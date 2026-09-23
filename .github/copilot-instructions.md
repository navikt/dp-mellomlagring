# dp-mellomlagring

Fillager for dp-*-applikasjoner. Lagrer krypterte dokumenter (vedlegg, PDF-bundler) i Google Cloud
Storage, med per-bruker feltnivå-kryptering via Google Tink og GCP KMS.

## Build, test og lint

- Bygg: `./gradlew build`
- Kjør alle tester: `./gradlew test`
- Kjør én testklasse: `./gradlew test --tests "no.nav.dagpenger.mellomlagring.vedlegg.MediatorTest"`
- Kjør én testmetode: `./gradlew test --tests "no.nav.dagpenger.mellomlagring.vedlegg.MediatorTest.should lagre vedlegg"`
- Lint/format: ktlint kjøres automatisk (`ktlintFormat` er en `dependsOn` for `KotlinCompile`), ingen
  egen lint-kommando trengs før commit.
- Filoperasjoner mot Google Cloud Storage er ikke alltid mulig lokalt uten `docker-compose up`
  (starter `fake-gcs-server` på port 50000→4443, se `Config.storage` for `Env.LOCAL`).
- Manuelle tester mot dev-miljøet kjøres via `E2E`-klassen i testmappa (ikke del av vanlig testkjøring).

## Arkitektur

Ktor-app (CIO-engine, port 8080) satt sammen i `App.kt` → `mellomLagring(mediator)`. Lagene:

- **`vedlegg/`** – HTTP-API (`VedleggApi.kt`) og `Mediator`, som orkestrerer filvalidering,
  lagring og henting. `VedleggUrn` er nøkkelen klienter bruker til å referere en fil (URN-format
  `urn:vedlegg:<søknadsId>/<uuid>`).
- **`lagring/`** – `Store`-interfacet har to implementasjoner som *dekorerer* hverandre:
  `S3Store` (faktisk GCS-tilgang) wrappes av `KryptertStore`, som legger til Tink-kryptering av
  filinnhold *og* eier-metadata, samt eierskapskontroll (kaster `NotOwnerException` hvis
  `fnr` i requesten ikke matcher kryptert eier på objektet). All tilgang går via
  `MediatorImpl.kryptertStore(eier)` — det finnes ingen ukryptert vei til lagring i praksis.
- **`pdf/`** – `BundleMediator` bygger sammensatte PDF-er fra flere vedlegg (via `ImageProcessor`
  for bildekonvertering); wrapper `Mediator` i stedet for å implementere lagring selv.
  `PdfApi.kt` eksponerer bundling-endepunktene.
- **`av/`** – `AntiVirus` (ClamAV) er én av flere `Mediator.FilValidering`-implementasjoner
  (sammen med filtype- og PDF-validering) som kjøres parallelt i `MediatorImpl.valider()` via
  coroutines; feil samles i en `UgyldigFilInnhold` med alle feiltyper, ikke bare den første.
- **`auth/`** – to separate JWT-strategier montert samtidig: `azureAd` (app-til-app, eier hentes
  fra `X-Eier`-header via `azureAdEier()`) og `tokenX` (bruker-OBO, eier hentes fra `pid`-claim
  via `oboEier()`). Hvilken som brukes avgjøres av hvilken route som `authenticate(...)`-blokken
  gjelder for, ikke av en global policy.
- **`Config.kt`** – miljø bestemmes av `NAIS_CLUSTER_NAME` (`dev-gcp`/`prod-gcp`/ellers `LOCAL`).
  `Crypto.aead` bruker envelope-kryptering mot en GCP KMS-nøkkel i prod/dev (se `docs/encryption.md`
  for manuelt KMS-oppsett per prosjekt), og en ren lokal AES128-nøkkel i `LOCAL`.
- **`openapi`-modulen** – separat Gradle-submodul (`java-library`) som eier
  `mellomlagring-api.yaml`; serveres via `swaggerUI` på `/openapi` i hovedappen.

## Konvensjoner

- Alle domeneklasser er `internal` — offentlig API-flate er kun det som eksponeres via Ktor-routing.
- `Store`, `Mediator` og `FilValidering` er interfaces nettopp for å kunne dekorere
  (`KryptertStore` rundt `S3Store`) eller mocke i tester — følg dette mønsteret ved nye lagrings-
  eller valideringsbehov i stedet for å utvide en konkret klasse.
- Feilhåndtering skjer med `Result<T>` internt i `lagring`/`vedlegg`, men mappes til dedikerte
  exceptions (`NotFoundException`, `NotOwnerException`, `UgyldigFilInnhold`) i `getOrThrow()`-
  extensions og videre til `HttpProblem`/RFC 7807-responser i `StatusPages`-installasjonen i `App.kt`.
- Tester bruker `TestApplication.withMockAuthServerAndTestApplication(...)` for å starte en
  `testApplication` med ekte `ktorFeatures()` og en `MockOAuth2Server`; bruk denne i stedet for å
  bygge opp autentisering manuelt i nye API-tester.
