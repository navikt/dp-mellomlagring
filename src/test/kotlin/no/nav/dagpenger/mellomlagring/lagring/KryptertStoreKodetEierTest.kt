package no.nav.dagpenger.mellomlagring.lagring

import com.google.crypto.tink.Aead
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.vedlegg.NotOwnerException
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.util.Base64

internal class KryptertStoreKodetEierTest {
    private val fnr = "17659988198"
    private val annetFnr = "17659988196"
    private val nøkkel = "soknad/fil"

    // Etterligner GCS: gamle objekter mangler eier_v2, og eier kommer RFC 2047-kodet tilbake.
    private class FakeGcs(
        private val gammeltObjekt: Boolean,
        private val kodet: Boolean,
    ) : Store {
        val lagret = linkedMapOf<String, Klump>()
        var antallInnholdKall = 0

        private fun metadata(klumpInfo: KlumpInfo): KlumpInfo {
            var info = if (gammeltObjekt) klumpInfo.copy(eierV2 = null) else klumpInfo
            if (kodet) info = info.copy(eier = info.eier?.let { gcsKod(it.toByteArray(Charset.forName("ISO-8859-1"))) })
            return info
        }

        override fun hent(storageKey: StorageKey): Result<Klump?> {
            antallInnholdKall++
            return Result.success(lagret[storageKey])
        }

        override fun lagre(klump: Klump): Result<Int> {
            lagret[klump.klumpInfo.objektNavn] = klump
            return Result.success(201)
        }

        override fun slett(storageKey: StorageKey): Result<Boolean> = Result.success(lagret.remove(storageKey) != null)

        override fun hentKlumpInfo(storageKey: StorageKey): Result<KlumpInfo?> =
            Result.success(lagret[storageKey]?.klumpInfo?.let { metadata(it) })

        override fun listKlumpInfo(keyPrefix: StorageKey): Result<List<KlumpInfo>> =
            Result.success(lagret.values.filter { it.klumpInfo.objektNavn.startsWith(keyPrefix) }.map { metadata(it.klumpInfo) })
    }

    // Gir ciphertext med valgte byte på faste posisjoner, slik at testen dekker \n, \r og \r\n i eier.
    private class FastCiphertextAead(
        private val ekte: Aead,
        private val innsatt: Map<Int, Byte>,
    ) : Aead by ekte {
        private val prefiks = 32

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray {
            val fyll = ByteArray(prefiks) { 0x41 }
            innsatt.forEach { (pos, b) -> fyll[pos] = b }
            return fyll + ekte.encrypt(plaintext, associatedData + fyll)
        }

        override fun decrypt(
            ciphertext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray {
            if (ciphertext.size < prefiks) throw java.security.GeneralSecurityException("for kort")
            val fyll = ciphertext.copyOfRange(0, prefiks)
            return ekte.decrypt(ciphertext.copyOfRange(prefiks, ciphertext.size), associatedData + fyll)
        }
    }

    private fun gcs(
        gammeltObjekt: Boolean = true,
        kodet: Boolean = true,
        aead: Aead = Crypto.aead,
        vararg nøkler: String = arrayOf(nøkkel),
    ) = FakeGcs(gammeltObjekt, kodet).also { store ->
        nøkler.forEach { lagreFil(store, it, aead) }
    }

    private fun lagreFil(
        store: Store,
        objektNavn: String,
        aead: Aead,
    ) {
        KryptertStore(fnr, store, aead)
            .lagre(
                Klump(
                    innhold = "innhold".toByteArray(),
                    klumpInfo = KlumpInfo(objektNavn = objektNavn, originalFilnavn = "fil", storrelse = 7, eier = fnr),
                ),
            ).getOrThrow()
    }

    private fun KryptertStore.hentTekst() = hent(nøkkel).getOrThrow()!!.innhold.decodeToString()

    @Test
    fun `lagring skriver samme kryptert eier i eier og eier_v2`() {
        val info =
            FakeGcs(
                gammeltObjekt = false,
                kodet = false,
            ).also { lagreFil(it, nøkkel, Crypto.aead) }.lagret.getValue(nøkkel).klumpInfo

        info.eierV2 shouldNotBe null
        Base64.getDecoder().decode(info.eierV2) shouldBe info.eier!!.toByteArray(Charset.forName("ISO-8859-1"))
        info.toMetadata()["eier_v2"] shouldBe info.eierV2
    }

    @Test
    fun `gammelt objekt med kodet eier gir tilgang`() {
        KryptertStore(fnr, gcs(), Crypto.aead).hentTekst() shouldBe "innhold"
    }

    @Test
    fun `gammelt objekt med kodet eier og feil fnr gir NotOwner uten å hente innhold`() {
        val store = gcs()

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `kodet eier med LF, CR og CRLF i ciphertext gir tilgang`() {
        val aead = FastCiphertextAead(Crypto.aead, mapOf(3 to 0x0a, 10 to 0x0d, 20 to 0x0d, 21 to 0x0a))
        val store = gcs(aead = aead)

        KryptertStore(fnr, store, aead).hentTekst() shouldBe "innhold"
        KryptertStore(annetFnr, store, aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
    }

    @Test
    fun `gammelt objekt med rå eier fungerer fortsatt`() {
        KryptertStore(fnr, gcs(kodet = false), Crypto.aead).hentTekst() shouldBe "innhold"
        KryptertStore(annetFnr, gcs(kodet = false), Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
    }

    @Test
    fun `nytt objekt bruker eier_v2 selv om eier er kodet`() {
        KryptertStore(fnr, gcs(gammeltObjekt = false), Crypto.aead).hentTekst() shouldBe "innhold"
    }

    @Test
    fun `nytt objekt med feil eier gir NotOwner`() {
        val store = gcs(gammeltObjekt = false)

        KryptertStore(annetFnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
        store.antallInnholdKall shouldBe 0
    }

    @Test
    fun `ugyldig base64 i eier_v2 gir NotOwner`() {
        val store = gcs(gammeltObjekt = false)
        store.lagret[nøkkel] = store.lagret.getValue(nøkkel).let { Klump(it.innhold, it.klumpInfo.copy(eierV2 = "ikke base64!")) }

        KryptertStore(fnr, store, Crypto.aead).hent(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
    }

    @Test
    fun `hentKlumpInfo og slett fungerer med kodet eier`() {
        KryptertStore(fnr, gcs(), Crypto.aead).hentKlumpInfo(nøkkel).isSuccess shouldBe true
        KryptertStore(fnr, gcs(), Crypto.aead).slett(nøkkel).getOrThrow() shouldBe true
        KryptertStore(annetFnr, gcs(), Crypto.aead).slett(nøkkel).exceptionOrNull() should beInstanceOf<NotOwnerException>()
    }

    @Test
    fun `listing filtrerer på eier med kodet eier`() {
        val store = gcs(nøkler = arrayOf("soknad/a", "soknad/b"))

        KryptertStore(fnr, store, Crypto.aead)
            .listKlumpInfo("soknad")
            .getOrThrow()
            .map { it.objektNavn } shouldContainExactlyInAnyOrder listOf("soknad/a", "soknad/b")
        KryptertStore(annetFnr, store, Crypto.aead).listKlumpInfo("soknad").getOrThrow() shouldBe emptyList()
    }
}
