package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import no.nav.dagpenger.mellomlagring.Config

class S3Store(
    private val storage: Storage = Config.storage
) : Store {

    override fun lagre(vedleggHolder: Store.VedleggHolder) {
        storage.create(BucketInfo.of(vedleggHolder.soknadsId))
    }

    override fun hent(soknadsId: String): List<VedleggMetadata> {
        TODO("Not yet implemented")
    }
}
