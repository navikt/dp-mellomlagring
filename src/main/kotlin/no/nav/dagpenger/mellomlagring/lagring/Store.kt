package no.nav.dagpenger.mellomlagring.lagring

internal interface Store {

    fun hent(storageKey: StorageKey): Result<Klump?>
    fun lagre(klump: Klump): Result<Int>
    fun slett(storageKey: StorageKey /* = kotlin.String */): Result<Boolean>
    fun hentKlumpInfo(storageKey: StorageKey /* = kotlin.String */): Result<KlumpInfo?>
    fun listKlumpInfo(keyPrefix: StorageKey /* = kotlin.String */): Result<List<KlumpInfo>>
}

internal data class KlumpInfo(val navn: String, val metadata: Map<String, String>)
internal class Klump(
    val innhold: ByteArray,
    val klumpInfo: KlumpInfo
)

internal typealias StorageKey = String
internal class StoreException(msg: String) : Throwable(msg)
