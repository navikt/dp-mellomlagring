package no.nav.dagpenger.mellomlagring.lagring

import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

class S3Store(
    host: String,
    credentials: Credentials = GoogleCredentials.getApplicationDefault()
) : Store {

    private val storage: Storage = StorageOptions.newBuilder()
        .setCredentials(credentials)
        .setHost(host)
        .setProjectId("dagpenger") // TODO
        .build()
        .service

    override fun lagre(vedleggHolder: Store.VedleggHolder) {
        storage.create(BucketInfo.of(vedleggHolder.soknadsId))
    }

    override fun hent(soknadsId: String): List<VedleggMetadata> {
        TODO("Not yet implemented")
    }
}
