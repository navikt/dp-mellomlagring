package no.nav.dagpenger.mellomlagring.lagring

import com.google.cloud.storage.Blob
import java.time.ZoneId
import java.time.ZonedDateTime

internal interface Store {
    fun hent(storageKey: StorageKey): Result<Klump?>

    fun lagre(klump: Klump): Result<Int>

    fun slett(storageKey: StorageKey): Result<Boolean>

    fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?>

    fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>>
}

internal data class KlumpInfo(
    val objektNavn: String,
    val originalFilnavn: String,
    val storrelse: Long,
    val eier: String? = null,
    val filContentType: String = "application/octet-stream",
    val tidspunkt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Oslo")),
) {
    companion object {
        fun fromBlob(blob: Blob): KlumpInfo {
            val tidspunkt: ZonedDateTime =
                blob.metadata?.get("tidspunkt")?.let { ZonedDateTime.parse(it) } ?: blob.createTimeOffsetDateTime.let {
                    ZonedDateTime.of(
                        it.toLocalDateTime(),
                        ZoneId.of("Europe/Oslo"),
                    )
                }
            return KlumpInfo(
                objektNavn = blob.name,
                originalFilnavn = blob.metadata?.get("originalFilnavn") ?: blob.name,
                storrelse = blob.metadata?.get("storrelse ")?.toLong() ?: 0,
                filContentType = blob.contentType ?: "application/octet-stream",
                eier = blob.metadata?.get("eier"),
                tidspunkt = tidspunkt,
            )
        }
    }

    fun toMetadata(): Map<String, String> =
        mutableMapOf(
            "originalFilnavn" to originalFilnavn,
            "storrelse " to storrelse.toString(),
            "tidspunkt" to tidspunkt.toString(),
        ).also { map ->
            eier?.let { map["eier"] = it }
        }.toMap()
}

internal class Klump(
    val innhold: ByteArray,
    val klumpInfo: KlumpInfo,
)

internal typealias StorageKey = String

internal class StoreException(msg: String) : Throwable(msg)
