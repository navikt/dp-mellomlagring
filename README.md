# dp-mellomlagring

Fillager for dp-* applikasjoner. Lagrer dokumenter i gs buckets.

##Manuelle tester i dev
Filoperasjoner mot gs er ikke alltid mulig å gjøre lokalt. Tester mot dev kan kjøres via E2E klassen i testmappa

##Nyttige gs-kommandoer:
* Liste alle bukets i prosjekt:
  1. Finn prosjektId. I [cloud console](https://console.cloud.google.com): -> logg inn med **nav-bruker** -> prosjektoversikt (øverst i høyre hjørne etter meny)
  2. `gcloud alpha storage ls --project=<prosjektId>`
* Liste innhold i bucket 
```gcloud alpha storage ls --recursive <bucket-navn>``` 
* For operasjoner på bucketobjekter se [gs-util](https://cloud.google.com/storage/docs/gsutil/)