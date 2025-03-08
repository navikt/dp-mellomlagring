package no.nav.dagpenger.mellomlagring.lagring

import com.google.crypto.tink.Aead
import io.ktor.utils.io.core.toByteArray
import mu.KotlinLogging
import no.nav.dagpenger.mellomlagring.vedlegg.NotFoundException
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import java.nio.charset.Charset
import java.security.GeneralSecurityException

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

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
        store.listKlumpInfo(keyPrefix).map { klumpInfoList ->
            klumpInfoList.filter { it.erEier() }
        }

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

    private fun KlumpInfo?.erEier(): Boolean =
        when (val kryptertEier = this?.eier) {
            null -> {
                sikkerlogg.warn { "Fant ikke eier for klumpinfo: $this" }
                false
            }
            else -> {
                try {
                    kryptertEier.decrypt() == fnr
                } catch (e: GeneralSecurityException) {
                    sikkerlogg.warn { "Fnr matcher ikke fødselsnummer på ressurs" }
                    false
                }
            }
        }

    private fun Klump.encrypt(eier: String): Klump {
        val klumpInfo1 = this.klumpInfo.copy(eier = this.klumpInfo.eier.encrypt())
        return Klump(
            innhold = aead.encrypt(this.innhold, eier.toByteArray()),
            klumpInfo = klumpInfo1,
        )
    }

    private fun Klump.decrypt(eier: String): Klump =
        Klump(innhold = aead.decrypt(this.innhold, eier.toByteArray()), klumpInfo = this.klumpInfo)

    private fun String?.encrypt() = this?.let { aead.encrypt(this.toByteArray(charset), fnr.toByteArray()).toString(charset) }

    private fun String.decrypt() = aead.decrypt(this.toByteArray(charset), fnr.toByteArray()).toString(charset)
}
