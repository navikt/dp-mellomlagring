package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config

private val logger = KotlinLogging.logger { }

class S3Store(
    private val gcpStorage: Storage = Config.storage,
    private val bucketName: String = Config.bucketName,
) : Store {

    init {
        ensureBucketExists()
    }

    private fun ensureBucketExists() {
        when (gcpStorage.get(bucketName) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketName. Må provisjoneres")
            true -> logger.info("Bucket $bucketName funnet.")
        }
    }

    override fun lagre(vedleggHolder: Store.VedleggHolder) {
        gcpStorage.create(BucketInfo.of(vedleggHolder.soknadsId)).also {
            logger.info { it }
        }
    }

    override fun hent(soknadsId: String): List<VedleggMetadata> {
        TODO("Not yet implemented")
    }
}
