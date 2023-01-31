package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.Config.Crypto
import no.nav.dagpenger.mellomlagring.GoogleCloudStorageTestcontainer
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.StoreException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Because we are using fixedPort testcontainer
class MediatorTest {
    companion object {
        private const val FIXED_HOST_PORT = 44444
    }

    private val gcsFixedHost by lazy {
        GoogleCloudStorageTestcontainer.createAndStart(FIXED_HOST_PORT)
    }

    private val mediator by lazy {
        MediatorImpl(
            store = S3Store(gcpStorage = Config.localStorage("http://${gcsFixedHost.host}:$FIXED_HOST_PORT", true)),
            filValideringer = listOf(
                mockk<Mediator.FilValidering>().also {
                    coEvery { it.valider(any(), any()) } returns FilValideringResultat.Gyldig("filnavn")
                }
            ),
            aead = Crypto.aead,
            uuidGenerator = fixedUUIDGenerator()
        )
    }

    private val fixedUUIDGenerator: () -> () -> UUID = {
        val iterator = listOf<UUID>(
            UUID.fromString("f9ece50c-e833-43c6-996e-aa70ddbc9870"),
            UUID.fromString("7170c6c2-17ca-11ed-861d-0242ac120002")
        ).iterator();
        { iterator.next() }
    }

    @Test
    fun `happy path lagre,listing, henting og sletting av filer`() {

        runBlocking {
            mediator.lagre("id", "hubba", "hubba".toByteArray(), "application/octet-stream", "eier").let { klumpinfo ->
                klumpinfo.objektNavn shouldBe "id/f9ece50c-e833-43c6-996e-aa70ddbc9870"
                klumpinfo.originalFilnavn shouldBe "hubba"
            }
            mediator.lagre("id", "hubba bubba", "hubba bubba".toByteArray(), "application/octet-stream", "eier")
                .let { klumpinfo ->
                    klumpinfo.objektNavn shouldBe "id/7170c6c2-17ca-11ed-861d-0242ac120002"
                    klumpinfo.originalFilnavn shouldBe "hubba bubba"
                }

            mediator.liste("id", "eier").let { klumpInfos ->
                klumpInfos.size shouldBe 2

                klumpInfos.find { it.objektNavn == "id/f9ece50c-e833-43c6-996e-aa70ddbc9870" }.let {
                    it shouldNotBe null
                    it!!.originalFilnavn shouldBe "hubba"
                }

                klumpInfos.find { it.objektNavn == "id/7170c6c2-17ca-11ed-861d-0242ac120002" }.let {
                    it shouldNotBe null
                    it!!.originalFilnavn shouldBe "hubba bubba"
                }
            }

            mediator.hent(VedleggUrn("id/f9ece50c-e833-43c6-996e-aa70ddbc9870"), "eier").also {
                it shouldNotBe null
                it?.let { klump ->
                    klump.klumpInfo.objektNavn shouldBe "id/f9ece50c-e833-43c6-996e-aa70ddbc9870"
                    klump.klumpInfo.originalFilnavn shouldBe "hubba"
                    String(klump.innhold) shouldBe "hubba"
                }
            }

            mediator.slett(VedleggUrn("id/f9ece50c-e833-43c6-996e-aa70ddbc9870"), "eier") shouldBe true
            shouldThrow<NotFoundException> {
                mediator.hent(
                    VedleggUrn("id/f9ece50c-e833-43c6-996e-aa70ddbc9870"),
                    "eier"
                )
            }
        }
    }

    @Test
    fun `Hente, slette liste vedlegg som ikke finnes`() {
        runBlocking {
            mediator.liste("finnesIkke", "eier") shouldBe emptyList()

            shouldThrow<NotFoundException> { mediator.hent(VedleggUrn("finnesIkke"), "eier") }
            shouldThrow<NotFoundException> { mediator.slett(VedleggUrn("finnesIkke"), "eier") }
        }
    }

    @Test
    fun filValidering() {
        val mockedMediator: Mediator = MediatorImpl(
            mockk<Store>(relaxed = true).also {
                coEvery { it.lagre(any()) } returns Result.success(1)
            },
            filValideringer = listOf(
                mockk<Mediator.FilValidering>().also {
                    coEvery { it.valider("exception", any()) } throws Throwable("En feil")
                    coEvery { it.valider("infisert", any()) } returns FilValideringResultat.Ugyldig("infisert", "virus", FeilType.FILE_VIRUS)
                    coEvery { it.valider("OK", any()) } returns FilValideringResultat.Gyldig("gyldig")
                }
            ),
            aead = Crypto.aead
        )

        runBlocking {
            shouldNotThrow<Throwable> {
                mockedMediator.lagre("id", "OK", "infisert".toByteArray(), "application/octet-stream", "eier")
            }

            shouldThrow<UgyldigFilInnhold> {
                mockedMediator.lagre("id", "infisert", "infisert".toByteArray(), "application/octet-stream", "eier")
            }

            shouldThrow<Throwable> {
                mockedMediator.lagre("id", "exception", "infisert".toByteArray(), "application/octet-stream", "eier")
            }
        }
    }

    @Test
    fun feilhåndtering() {
        val mockedMediator: Mediator = MediatorImpl(
            mockk<Store>().also {
                every { it.hent(any()) } returns Result.failure(Throwable("feil"))
                every { it.hentKlumpInfo(any()) } returns Result.failure(Throwable("feil"))
                every { it.lagre(any()) } returns Result.failure(Throwable("feil"))
                every { it.slett(any()) } returns Result.failure(Throwable("feil"))
                every { it.listKlumpInfo(any()) } returns Result.failure(Throwable("feil"))
            },
            Crypto.aead
        )
        runBlocking() {
            shouldThrow<StoreException> {
                mockedMediator.hent(VedleggUrn("id"), "eier")
            }

            shouldThrow<StoreException> {
                mockedMediator.liste("id", "eier")
            }

            shouldThrow<StoreException> {
                mockedMediator.lagre("id", "filnavn", "innhold".toByteArray(), "application/octet-stream", "eier")
            }
            shouldThrow<StoreException> {
                mockedMediator.slett(VedleggUrn("nss"), "eier")
            }
        }
    }
}
