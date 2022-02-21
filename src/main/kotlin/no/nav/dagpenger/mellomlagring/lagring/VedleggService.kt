package no.nav.dagpenger.mellomlagring.lagring

import de.slub.urn.URN
import io.ktor.features.NotFoundException
import no.nav.dagpenger.mellomlagring.crypto.Crypto

internal class VedleggService(private val store: Store, private val crypto: Crypto) {

    fun lagre(soknadsId: String, fileName: String, filinnhold: ByteArray, eier: String): Urn {
        val storageKey = createStoreKey(soknadsId, fileName)
        store.lagre(storageKey, filinnhold, crypto.encrypt(eier))
        return Urn("urn:vedlegg:$storageKey")
    }

    private fun createStoreKey(soknadsId: String, fileName: String): StorageKey {
        // TODO legge til fnr først
        return "$soknadsId/$fileName"
    }

    fun liste(key: StorageKey, eier: String): List<Urn> {
        return store.list(key)
            .filter {
                crypto.decrypt(it.eier) == eier
            }
            .map {
                Urn("urn:vedlegg:${it.filnavn}")
            }
    }

    fun hent(urn: Urn, eier: String): StorageValue {
        val urn8141 = URN.rfc8141().parse(urn.urn).namespaceSpecificString().toString()
        val metadata = store.list(urn8141).firstOrNull() ?: throw NotFoundException()
        if (eier != crypto.decrypt(metadata.eier)) throw OwnerException(eier.substring(0, 6), urn8141)
        return store.hent(urn8141)
    }

    internal data class Urn(val urn: String)
}

class OwnerException(eier: String, urn: String) : Throwable("$eier er ikke eier av $urn")
