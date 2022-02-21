package no.nav.dagpenger.mellomlagring.lagring

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

    override fun hent(storageKey: StorageKey): StorageValue {
        return gcpStorage.get(BlobId.of(bucketName, storageKey)).getContent() ?: throw RuntimeException("FIXME")
    }

    override fun list(keyPrefix: StorageKey): List<VedleggMetadata> {
        return gcpStorage.list(
            bucketName,
            Storage.BlobListOption.prefix(keyPrefix)
        )?.values?.map { v: Blob ->
            val eier = v.metadata?.let { it["eier"] ?: "" } ?: ""
            VedleggMetadata(v.name, eier)
        } ?: emptyList()
    }

    override fun lagre(storageKey: StorageKey, storageValue: StorageValue, eier: String) {
        val blobInfo =
            BlobInfo.newBuilder(BlobId.of(bucketName, storageKey))
                .setContentType("application/octet-stream")
                .setMetadata(
                    mapOf(
                        "eier" to eier
                    )
                )
                .build() // todo contentType?

        kotlin.runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(storageValue, 0, storageValue.size))
            }
        }.onFailure { e ->
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }.onSuccess {
            logger.info("Lagret fil med blobid:  ${blobInfo.blobId.name} og bytes: $it")
        }
    }
}
