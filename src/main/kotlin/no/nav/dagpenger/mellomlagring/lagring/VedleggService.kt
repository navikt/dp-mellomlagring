package no.nav.dagpenger.mellomlagring.lagring

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

    fun hent(key: StorageKey): List<VedleggMetadata> {
        return store.list(key)
    }

    internal data class Urn(val urn: String)
}
