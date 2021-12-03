package no.nav.dagpenger.mellomlagring.lagring

class VedleggService(val store: Store) {

    fun lagre(soknadsId: String, fileName: String, filinnhold: ByteArray) {
        store.lagre(createStoreKey(soknadsId, fileName), filinnhold)
    }

    private fun createStoreKey(soknadsId: String, fileName: String): StorageKey {
        // TODO legge til fnr først
        return "$soknadsId/$fileName"
    }

    fun hent(key: StorageKey): Any {
        return store.hent(key)
    }
}
