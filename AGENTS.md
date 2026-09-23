# AGENTS.md — navikt/dp-mellomlagring

## Repository Overview

Fillager for dp-*-applikasjoner. Ktor-app som lagrer krypterte dokumenter (vedlegg, PDF-bundler)
i Google Cloud Storage, med per-bruker feltnivå-kryptering via Google Tink og GCP KMS. Ingen
database eller Kafka — kun HTTP-API mot GCS.

## Tech Stack

- Kotlin (JVM toolchain 25), Ktor (CIO-engine)
- Google Cloud Storage + Google Tink (kryptering) + GCP KMS
- ClamAV (antivirus-skanning av opplastede filer)
- Gradle multi-modul (rot + `openapi`-submodul)

## Build & Test Commands

```bash
./gradlew build                                                       # Bygg
./gradlew test                                                        # Kjør alle tester
./gradlew test --tests "no.nav.dagpenger.mellomlagring.vedlegg.MediatorTest"  # Kjør én testklasse
docker-compose up                                                     # Start fake-gcs-server lokalt (port 50000)
```

- Lint/format: `ktlint` kjøres automatisk (`ktlintFormat` er `dependsOn` for `KotlinCompile`) —
  ingen egen lint-kommando trengs før commit.
- Manuelle tester mot dev kjøres via `E2E`-klassen i testmappa (ikke del av vanlig testkjøring).

## Arkitektur

- **`vedlegg/`** — HTTP-API og `Mediator` som orkestrerer filvalidering, lagring og henting.
  `VedleggUrn` er nøkkelen klienter bruker til å referere en fil (`urn:vedlegg:<søknadsId>/<uuid>`).
- **`lagring/`** — `Store`-interfacet dekoreres: `S3Store` (ekte GCS-tilgang) wrappes av
  `KryptertStore`, som legger til Tink-kryptering av filinnhold og eier-metadata, samt
  eierskapskontroll. All tilgang går via `MediatorImpl.kryptertStore(eier)`.
- **`pdf/`** — `BundleMediator` bygger sammensatte PDF-er fra flere vedlegg; wrapper `Mediator`.
- **`av/`** — `AntiVirus` (ClamAV) er én av flere `Mediator.FilValidering`-implementasjoner som
  kjøres parallelt via coroutines.
- **`auth/`** — to JWT-strategier montert samtidig: `azureAd` (app-til-app, eier fra `X-Eier`-
  header) og `tokenX` (bruker-OBO, eier fra `pid`-claim).
- **`Config.kt`** — miljø bestemmes av `NAIS_CLUSTER_NAME`. Ekte KMS-kryptering i dev/prod,
  lokal AES128-nøkkel i `LOCAL`.
- **`openapi`-modulen** — egen Gradle-submodul som eier `mellomlagring-api.yaml`, servert via
  `swaggerUI` på `/openapi`.

## Code Standards

- Domeneklasser er `internal` — offentlig flate er kun det som eksponeres via Ktor-routing.
- `Store`, `Mediator` og `FilValidering` er interfaces for å kunne dekoreres/mockes — følg dette
  mønsteret for nye lagrings- eller valideringsbehov.
- Feilhåndtering med `Result<T>` internt, mappet til dedikerte exceptions
  (`NotFoundException`, `NotOwnerException`, `UgyldigFilInnhold`) og videre til RFC 7807-responser
  via `StatusPages` i `App.kt`.
- Tester bruker `TestApplication.withMockAuthServerAndTestApplication(...)` for å starte
  `testApplication` med ekte `ktorFeatures()` og `MockOAuth2Server`.

## Deployment

- Plattform: Nais (Kubernetes på GCP)
- Manifester i `.nais/`
- Nødvendige endepunkter: `/isalive`, `/isready`, `/metrics`

## Boundaries

### ✅ Always
- Følg eksisterende dekoratørmønster for `Store`/`Mediator`/`FilValidering`
- Kjør tester før commit
- Behold `internal`-synlighet på domeneklasser

### ⚠️ Ask First
- Endringer i autentiseringsoppsettet (`auth/Auth.kt`)
- Endringer i KMS-/krypteringsoppsett (`Config.Crypto`)
- Nye eksterne avhengigheter

### 🚫 Never
- Committe hemmeligheter/credentials
- Lagre filinnhold ukryptert (utenom `LOCAL`-miljø)
- Omgå eierskapskontrollen i `KryptertStore`
