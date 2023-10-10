package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.Blob.BlobSourceOption
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.Config
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger { }

internal class S3Store(
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

    override fun hent(storageKey: StorageKey): Result<Klump?> {
        return kotlin.runCatching {
            gcpStorage.get(BlobId.of(bucketName, storageKey))?.let { blob ->
                Klump(
                    innhold = blob.getContent(),
                    klumpInfo = KlumpInfo.fromBlob(blob),
                )
            }
        }.onFailure {
            logger.error(it) { "Feilet å hente fil: $storageKey" }
        }
    }

    override fun lagre(klump: Klump): Result<Int> {
        val blobInfo =
            BlobInfo.newBuilder(BlobId.of(bucketName, klump.klumpInfo.objektNavn))
                .setContentType(klump.klumpInfo.filContentType)
                .setMetadata(klump.klumpInfo.toMetadata())
                .build() // todo contentType?

        return kotlin.runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(klump.innhold, 0, klump.innhold.size))
            }
        }.onFailure { e ->
            logger.warn(e) { "Feilet med å lagre fil med id: ${blobInfo.blobId.name}" }
        }.onSuccess {
            logger.info("Lagret fil med blobid: ${blobInfo.blobId.name} og bytes: $it")
        }
    }

    override fun slett(storageKey: StorageKey): Result<Boolean> {
        return kotlin.runCatching {
            gcpStorage.get(BlobId.of(bucketName, storageKey))?.delete(BlobSourceOption.generationMatch()) ?: false
        }.onFailure { e ->
            logger.warn("Feilet å slette fil med id: $storageKey", e)
        }.onSuccess {
            logger.info("Fil $storageKey slettet med resultat: $it ")
        }
    }

    override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
        return kotlin.runCatching {
            gcpStorage.get(BlobId.of(bucketName, storageKey))
                ?.let { KlumpInfo.fromBlob(blob = it) }
        }.onSuccess {
            logger.debug { "Listet klumpinfo for fil: $storageKey" }
        }.onFailure {
            logger.warn(it) { "Feilet å liste klumpinfo for fil: $storageKey " }
        }
    }

    override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> {
        return kotlin.runCatching {
            gcpStorage.list(bucketName, Storage.BlobListOption.prefix(keyPrefix))
                ?.values
                ?.map(KlumpInfo.Companion::fromBlob)
                ?: emptyList()
        }.onSuccess {
            logger.debug { "Listet klumpinfo for path: $keyPrefix" }
        }.onFailure {
            logger.warn(it) { "Feilet å liste klumpinfo for path: $keyPrefix " }
        }
    }
}
