package no.nav.dagpenger.mellomlagring.lagring

import com.google.crypto.tink.Aead
import io.ktor.utils.io.core.toByteArray

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

internal class KryptertStore(private val fnr: String, private val store: Store, private val aead: Aead) : Store {
    override fun hent(storageKey: StorageKey): Result<Klump?> {
        return store.hent(storageKey).let { result ->
            result.map {
                it?.let {
                     Klump(aead.decrypt(it.innhold,fnr.toByteArray()),it.klumpInfo)
                }
            }
        }
    }

    override fun lagre(klump: Klump): Result<Int> {
        TODO("Not yet implemented")
    }

    override fun slett(storageKey: StorageKey): Result<Boolean> {
        TODO("Not yet implemented")
    }

    override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
        TODO("Not yet implemented")
    }

    override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> {
        TODO("Not yet implemented")
    }
}
