# dp-mellomlagring

Fillager for dp-* applikasjoner. Lagrer dokumenter i gs buckets.

## [Kryptering med Google Tink](docs/encryption.md)

## Manuelle tester i dev
Filoperasjoner mot gs er ikke alltid mulig å gjøre lokalt. Tester mot dev kan kjøres via E2E klassen i testmappa

## Nyttige gs-kommandoer:
* Liste alle bukets i prosjekt:
  1. Finn prosjektId. I [cloud console](https://console.cloud.google.com): -> logg inn med **nav-bruker** -> prosjektoversikt (øverst i høyre hjørne etter meny)
  2. `gcloud alpha storage ls --project=<prosjektId>`
* Liste innhold i bucket 
```gcloud alpha storage ls --recursive <bucket-navn>``` 
* For operasjoner på bucketobjekter se [gs-util](https://cloud.google.com/storage/docs/gsutil/)

* Hente ut kryptert fil
Se [eksempel](src/test/kotlin/no/nav/dagpenger/mellomlagring/HentKryptertFilEksempel.kt)