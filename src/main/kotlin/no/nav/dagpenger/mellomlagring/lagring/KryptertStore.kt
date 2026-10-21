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
        internal const val MAKS_NYE_FORSØK = 15
    }

    override fun hent(storageKey: StorageKey): Result<Klump?> =
        requireEier(storageKey) {
            store.hent(storageKey).map { it?.decrypt(fnr) }
        }

    override fun lagre(klump: Klump): Result<Int> = store.lagre(klump.encrypt(fnr))

    override fun slett(storageKey: StorageKey): Result<Boolean> = requireEier(storageKey) { store.slett(storageKey) }

    override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> {
        val klumpInfo = hentKlumpInfoMedRetry(storageKey)
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

    override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> {
        val sjekket = mutableSetOf<String>()
        val egne = linkedMapOf<String, KlumpInfo>()
        var forsøk = 0
        while (true) {
            val liste = store.listKlumpInfo(keyPrefix).getOrElse { return Result.failure(it) }
            liste
                .filter { it.objektNavn !in sjekket && !it.harKodetEier() }
                .forEach {
                    sjekket.add(it.objektNavn)
                    if (it.erEier()) egne[it.objektNavn] = it
                }
            val gjenstårKodet = liste.any { it.objektNavn !in sjekket }
            if (!gjenstårKodet || forsøk >= MAKS_NYE_FORSØK) {
                if (forsøk > 0) registrerRetry("listing av $keyPrefix", forsøk, lyktes = !gjenstårKodet)
                return Result.success(egne.values.toList())
            }
            forsøk++
        }
    }

    // GCS returnerer av og til binær eier-metadata RFC 2047-kodet (=?UTF-8?Q?...?=), som ikke kan dekrypteres.
    // Samme objekt gir ofte rå verdi ved neste kall. Gjelder bare objekter uten eier_v2. Henter bare metadata, ikke innhold.
    private fun hentKlumpInfoMedRetry(storageKey: StorageKey): Result<KlumpInfo?> {
        var resultat = store.hentKlumpInfo(storageKey)
        var forsøk = 0
        while (forsøk < MAKS_NYE_FORSØK && resultat.getOrNull()?.harKodetEier() == true) {
            forsøk++
            resultat = store.hentKlumpInfo(storageKey)
        }
        if (forsøk > 0) registrerRetry(storageKey, forsøk, lyktes = resultat.getOrNull()?.harKodetEier() != true)
        return resultat
    }

    private fun registrerRetry(
        hva: String,
        forsøk: Int,
        lyktes: Boolean,
    ) {
        logger.info { "Eier-metadata var RFC 2047-kodet ved $hva. Nye forsøk: $forsøk, lyktes: $lyktes" }
        Metrics.eierMetadataRetryCounter.labelValues(if (lyktes) "ok" else "feilet").inc()
    }

    private fun KlumpInfo.harKodetEier(): Boolean = eierV2 == null && eier?.startsWith("=?") == true

    private inline fun <T> requireEier(
        storageKey: StorageKey,
        resultSupplier: () -> Result<T>,
    ): Result<T> =
        hentKlumpInfoMedRetry(storageKey).fold(
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
        val kryptertEier: ByteArray? =
            when {
                this == null -> null
                eierV2 != null -> runCatching { Base64.getDecoder().decode(eierV2) }.getOrNull()
                else -> eier?.toByteArray(charset)
            }
        if (kryptertEier == null) {
            sikkerlogg.warn { "Fant ikke gyldig eier for klumpinfo: ${this?.objektNavn}" }
            return false
        }
        return try {
            aead.decrypt(kryptertEier, fnr.toByteArray()).toString(charset) == fnr
        } catch (e: GeneralSecurityException) {
            sikkerlogg.warn { "Fnr matcher ikke fødselsnummer på ressurs" }
            false
        }
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
