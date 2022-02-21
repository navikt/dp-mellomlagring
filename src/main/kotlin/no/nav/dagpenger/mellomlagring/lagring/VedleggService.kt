package no.nav.dagpenger.mellomlagring.lagring

import de.slub.urn.URN
import no.nav.dagpenger.mellomlagring.crypto.Crypto

internal class VedleggService(private val store: Store, private val crypto: Crypto) {

    fun lagre(soknadsId: String, fileName: String, filinnhold: ByteArray): Urn {
        val storageKey = createStoreKey(soknadsId, fileName)
        store.lagre(storageKey, filinnhold)
        return Urn("urn:vedlegg:$storageKey")
    }

    private fun createStoreKey(soknadsId: String, fileName: String): StorageKey {
        // TODO legge til fnr først
        return "$soknadsId/$fileName"
    }

    fun liste(key: StorageKey): List<Urn> {
        return store.list(key).map {
            Urn("urn:vedlegg:${it.filnavn}")
        }
    }

    fun hent(urn: Urn): StorageValue {
        val urn8141 = URN.rfc8141().parse(urn.urn)
        return store.hent(urn8141.namespaceSpecificString().toString())
    }

    internal data class Urn(val urn: String)
}
