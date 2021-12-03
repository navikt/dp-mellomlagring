package no.nav.dagpenger.mellomlagring.lagring

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import java.nio.ByteBuffer

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

    override fun lagre(storageKey: StorageKey, storageValue: StorageValue) {
        val blobInfo =
            BlobInfo.newBuilder(BlobId.of(bucketName, storageKey)).setContentType("application/octet-stream").build()
        kotlin.runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(storageValue, 0, storageValue.size))
            }
        }.onFailure { e ->
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }
    }

    override fun hent(storageKey: StorageKey): List<VedleggMetadata> {
        val list: Page<Blob>? = gcpStorage.list(bucketName, Storage.BlobListOption.prefix(storageKey))
        list?.values?.forEach {
            println(it.name)
        }
        return emptyList()
    }
}
