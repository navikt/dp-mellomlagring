package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.Blob

internal interface Store {

    fun hent(storageKey: StorageKey): Result<Klump?>
    fun lagre(klump: Klump): Result<Int>
    fun slett(storageKey: StorageKey /* = kotlin.String */): Result<Boolean>
    fun hentKlumpInfo(storageKey: StorageKey /* = kotlin.String */): Result<KlumpInfo?>
    fun listKlumpInfo(keyPrefix: StorageKey /* = kotlin.String */): Result<List<KlumpInfo>>
}

internal data class KlumpInfo(
    val objektNavn: String,
    val originalFilnavn: String,
    val storrelse: Long,
    val eier: String? = null
) {

    companion object {
        fun fromBlob(blob: Blob) = KlumpInfo(
            objektNavn = blob.name,
            originalFilnavn = blob.metadata["originalFilnavn"] ?: blob.name,
            storrelse = blob.metadata["storrelse "]?.toLong() ?: 0,
            eier = blob.metadata["eier"]
        )
    }

    fun toMetadata(): Map<String, String> = mutableMapOf(
        "originalFilnavn" to originalFilnavn,
        "storrelse " to storrelse.toString(),
    ).also { map ->
        eier?.let { map["eier"] = it }
    }.toMap()
}

internal class Klump(
    val innhold: ByteArray,
    val klumpInfo: KlumpInfo
)

internal typealias StorageKey = String

internal class StoreException(msg: String) : Throwable(msg)
