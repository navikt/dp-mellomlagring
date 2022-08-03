## Kryptering og sånt.

Vi bruker [Google Tink](https://github.com/google/tink) til å kryptere filer og metadata som lagres i bucket store.

Master key håndteres
av [Google KMS](https://codelabs.developers.google.com/codelabs/encrypt-and-decrypt-data-with-cloud-kms#0)

### Manuell setup

Dette må gjøres pr prosjekt som appen deployes. Les dev og prod.

#### Sette riktig prosjekt

1. Liste prosjekter: ```gcloud projects list ```
2. Sette prosjekt for videre bruk ``` export GOOGLE_CLOUD_PROJECT=PROJECT_ID```

#### Enable KMS api

1. Du må gi deg selv rollen ```roles/serviceusage.serviceUsageAdmin``` for å kunne enable/disable APIs.

```gcloud projects add-iam-policy-binding "${GOOGLE_CLOUD_PROJECT}" --member user:XXX@nav.no --role roles/serviceusage.serviceUsageAdmin --condition="expression=request.time < timestamp('$(date -v '+1H' -u +'%Y-%m-%dT%H:%M:%SZ')'),title=temp_access" ```

```gcloud services enable cloudkms.googleapis.com --project "${GOOGLE_CLOUD_PROJECT}" ```

2. Sjekk at API er enabled ```gcloud kms keyrings list --location europe-north1 --project $GOOGLE_CLOUD_PROJECT```


#### Lage keyring og keys

1. ``` gcloud kms keyrings create dp-mellomlagring --location europe-north1 --project $GOOGLE_CLOUD_PROJECT```
2. ```gcloud kms keys create "dp-mellomlagring" --location europe-north1 --purpose "encryption" --keyring "dp-mellomlagring" --project $GOOGLE_CLOUD_PROJECT```

Sjekk at keyring og keys ble laget riktig

```gcloud kms keys list --location europe-north1 --keyring "dp-mellomlagring" --project $GOOGLE_CLOUD_PROJECT```

Bør få noe ala:

```
NAME                                                                                                            PURPOSE          ALGORITHM                    PROTECTION_LEVEL  LABELS  PRIMARY_ID  PRIMARY_STATE
projects/$GOOGLE_CLOUD_PROJECT/locations/europe-north1/keyRings/dp-mellomlagring/cryptoKeys/dp-mellomlagring  ENCRYPT_DECRYPT  GOOGLE_SYMMETRIC_ENCRYPTION  SOFTWARE                  1           ENABLED
```