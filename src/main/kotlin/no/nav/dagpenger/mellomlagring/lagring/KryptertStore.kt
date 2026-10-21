package no.nav.dagpenger.mellomlagring.lagring

import com.google.crypto.tink.Aead
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.toByteArray
import no.nav.dagpenger.mellomlagring.monitoring.Metrics
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import java.util.Base64

private val sikkerlogg = KotlinLogging.logger("tjenestekall")
private val logger = KotlinLogging.logger {}

internal class KryptertStore(
    private val fnr: String,
    private val store: Store,
    private val aead: Aead,
) : Store {
    companion object {
        private val charset = Charset.forName("ISO-8859-1")
    }

    override fun hent(storageKey: StorageKey): Result<Klump?> =
        requireEier(storageKey) {
            store.hent(storageKey).map { it?.decrypt(fnr) }
        }

    override fun lagre(klump: Klump): Result<Int> = store.lagre(klump.encrypt(fnr))

    override fun slett(storageKey: StorageKey): Result<Boolean> = requireEier(storageKey) { store.slett(storageKey) }

    override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
        val klumpInfo = store.hentKlumpInfo(storageKey)
        return klumpInfo.fold(
            onSuccess = {
                when (it) {
                    null -> Result.failure(NotFoundException(storageKey))
                    else -> {
                        if (!it.erEier()) {
                            Result.failure(NotOwnerException("Forsøk på å hente ut $storageKey med feil eier"))
                        } else {
                            klumpInfo
                        }
                    }
                }
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> =
        store.listKlumpInfo(keyPrefix).map { liste -> liste.filter { it.erEier() } }

    private inline fun <T> requireEier(
        storageKey: StorageKey,
        resultSupplier: () -> Result<T>,
    ): Result<T> =
        store.hentKlumpInfo(storageKey).fold(
            onSuccess = {
                when (it) {
                    null -> Result.failure(NotFoundException(storageKey))
                    else -> {
                        if (!it.erEier()) {
                            Result.failure(NotOwnerException("Forsøk på å hente ut $storageKey med feil eier"))
                        } else {
                            resultSupplier()
                        }
                    }
                }
            },
            onFailure = {
                Result.failure(it)
            },
        )

    private fun KlumpInfo?.erEier(): Boolean {
        if (this == null) return false
        if (eierV2 == null && KodetEier.erKodet(eier)) return erEierAvKodet(eier!!)
        val kryptertEier: ByteArray? =
            when {
                eierV2 != null -> runCatching { Base64.getDecoder().decode(eierV2) }.getOrNull()
                else -> eier?.toByteArray(charset)
            }
        if (kryptertEier == null) {
            sikkerlogg.warn { "Fant ikke gyldig eier for klumpinfo: $objektNavn" }
            return false
        }
        return dekrypterOgSammenlign(kryptertEier).also {
            if (!it) sikkerlogg.warn { "Fnr matcher ikke fødselsnummer på ressurs" }
        }
    }

    // Gamle objekter uten eier_v2: GCS returnerer eier RFC 2047-kodet og tvetydig (se KodetEier).
    // AEAD avviser alle kandidater som ikke er nøyaktig den opprinnelige ciphertexten for dette fnr.
    private fun KlumpInfo.erEierAvKodet(kodet: String): Boolean {
        val kandidater = KodetEier.kandidater(kodet)
        val treff = kandidater.indexOfFirst { dekrypterOgSammenlign(it) }
        val lyktes = treff >= 0
        logger.info {
            "Eier-metadata var RFC 2047-kodet ved $objektNavn. Kandidater: ${kandidater.size}, forsøk: ${if (lyktes) treff + 1 else kandidater.size}, lyktes: $lyktes"
        }
        Metrics.eierMetadataDekodetCounter.labelValues(if (lyktes) "ok" else "feilet").inc()
        return lyktes
    }

    private fun dekrypterOgSammenlign(kryptertEier: ByteArray): Boolean =
        try {
            aead.decrypt(kryptertEier, fnr.toByteArray()).toString(charset) == fnr
        } catch (e: GeneralSecurityException) {
            false
        }

    private fun Klump.encrypt(eier: String): Klump {
        val kryptertEier = this.klumpInfo.eier?.let { aead.encrypt(it.toByteArray(charset), fnr.toByteArray()) }
        return Klump(
            innhold = aead.encrypt(this.innhold, eier.toByteArray()),
            klumpInfo =
                this.klumpInfo.copy(
                    eier = kryptertEier?.toString(charset),
                    eierV2 = kryptertEier?.let { Base64.getEncoder().encodeToString(it) },
                ),
        )
    }

    private fun Klump.decrypt(eier: String): Klump =
        Klump(innhold = aead.decrypt(this.innhold, eier.toByteArray()), klumpInfo = this.klumpInfo)
}
