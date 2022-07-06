package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.Config
import no.nav.dagpenger.mellomlagring.GoogleCloudStorageTestcontainer
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.lagring.Store
import no.nav.dagpenger.mellomlagring.lagring.StoreException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

private const val s = "eier"

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
            aead = Config.aead
        )
    }

    @Test
    fun `happy path lagre,listing, henting og sletting av filer`() {

        runBlocking {
            mediator.lagre("id", "hubba", "hubba".toByteArray(), "eier").urn shouldBe "urn:vedlegg:id/hubba"
            mediator.lagre("id", "bubba", "bubba".toByteArray(), "eier").urn shouldBe "urn:vedlegg:id/bubba"

            mediator.liste("id", "eier") shouldContainExactlyInAnyOrder listOf(
                VedleggUrn("id/hubba"),
                VedleggUrn("id/bubba"),
            )

            mediator.hent(VedleggUrn("id/hubba"), "eier").also {
                it shouldNotBe null
                it?.let { klump ->
                    klump.klumpInfo.navn shouldBe "id/hubba"
                    String(klump.innhold) shouldBe "hubba"
                }
            }

            mediator.slett(VedleggUrn("id/hubba"), "eier") shouldBe true
            shouldThrow<NotFoundException> { mediator.hent(VedleggUrn("id/hubba"), "eier") }
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
                    coEvery { it.valider("infisert", any()) } returns FilValideringResultat.Ugyldig("infisert", "virus")
                    coEvery { it.valider("OK", any()) } returns FilValideringResultat.Gyldig("gyldig")
                }
            ),
            aead = Config.aead
        )

        runBlocking {
            shouldNotThrow<Throwable> {
                mockedMediator.lagre("id", "OK", "infisert".toByteArray(), "eier")
            }

            shouldThrow<UgyldigFilInnhold> {
                mockedMediator.lagre("id", "infisert", "infisert".toByteArray(), "eier")
            }

            shouldThrow<Throwable> {
                mockedMediator.lagre("id", "exception", "infisert".toByteArray(), "eier")
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
            Config.aead
        )
        runBlocking() {
            shouldThrow<StoreException> {
                mockedMediator.hent(VedleggUrn("id"), "eier")
            }

            shouldThrow<StoreException> {
                mockedMediator.liste("id", "eier")
            }

            shouldThrow<StoreException> {
                mockedMediator.lagre("id", "filnavn", "innhold".toByteArray(), "eier")
            }

            shouldThrow<StoreException> {
                mockedMediator.slett(VedleggUrn("nss"), "eier")
            }
        }
    }
}
