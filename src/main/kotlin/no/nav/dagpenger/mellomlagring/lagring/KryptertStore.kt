package no.nav.dagpenger.mellomlagring.lagring

import com.google.crypto.tink.Aead
import io.ktor.utils.io.core.toByteArray
import mu.KotlinLogging

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class KryptertStore(private val fnr: String, private val store: Store, private val aead: Aead) : Store {

    override fun hent(storageKey: StorageKey): Result<Klump?> {
        require(store.hentKlumpInfo(storageKey).erEier())
        return store.hent(storageKey).map { it?.decrypt(fnr) }
    }

    override fun lagre(klump: Klump): Result<Int> {
        return store.lagre(klump.encrypt(fnr))
    }

    override fun slett(storageKey: StorageKey): Result<Boolean> {
        require(store.hentKlumpInfo(storageKey).erEier())
        return store.slett(storageKey)
    }

    override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
        return store.hentKlumpInfo(storageKey).also {
            require(it.erEier())
        }
    }

    override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> {
        return store.listKlumpInfo(keyPrefix).map { klumpInfoList ->
            klumpInfoList.filter { it.erEier() }
        }
    }

    private fun KlumpInfo?.erEier(): Boolean {
        val kryptertEier = this?.metadata?.get("eier")
        return when (kryptertEier) {
            null -> {
                sikkerlogg.warn { "Fant ikke eier for klumpinfo: $this" }
                false
            }
            else -> {
                kryptertEier.decrypt() == fnr
            }
        }
    }

    private fun Result<KlumpInfo?>.erEier(): Boolean = this.fold(
        onSuccess = { klumpInfo: KlumpInfo? ->
            return when (klumpInfo) {
                null -> {
                    sikkerlogg.warn { "Fant ikke klumpinfo" }
                    false
                }
                else -> {
                    klumpInfo.erEier()
                }
            }
        },
        onFailure = {
            sikkerlogg.error("Kunne ikke hente info", it)
            false
        }
    )

    private fun Klump.encrypt(eier: String): Klump {
        val metadata = this.klumpInfo.metadata.toMutableMap().apply {
            this["eier"] = fnr.encrypt()
        }
        return Klump(
            innhold = aead.encrypt(this.innhold, eier.toByteArray()),
            klumpInfo = this.klumpInfo.copy(metadata = metadata.toMap())
        )
    }

    private fun Klump.decrypt(eier: String): Klump {
        return Klump(innhold = aead.decrypt(this.innhold, eier.toByteArray()), klumpInfo = this.klumpInfo)
    }

    private fun String.encrypt() = aead.encrypt(this.toByteArray(), fnr.toByteArray()).toString()
    private fun String.decrypt() = aead.decrypt(this.toByteArray(), fnr.toByteArray()).toString()
}
