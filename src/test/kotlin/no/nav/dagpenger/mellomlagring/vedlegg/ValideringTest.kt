package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.av.AntiVirus
import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import no.nav.dagpenger.mellomlagring.vedlegg.FilValideringResultat.Gyldig
import no.nav.dagpenger.mellomlagring.vedlegg.FilValideringResultat.Ugyldig
import org.junit.jupiter.api.Test

internal class ValideringTest {
    @Test
    fun `Antivirus riktig validgerings resultat`() {
        AntiVirusValidering(
            mockk<AntiVirus>().also {
                coEvery { it.infisert("infisert", any()) } returns true
                coEvery { it.infisert("ok", any()) } returns false
            }
        ).let {
            runBlocking {
                it.valider("infisert", "".toByteArray()) should beInstanceOf<Ugyldig>()
                it.valider("ok", "".toByteArray()) should beInstanceOf<Gyldig>()
            }
        }
    }

    @Test
    fun `Filtype riktig validerings resultat`() {
        runBlocking {
            FiltypeValidering.valider("jpg", "/fisk1.jpg".fileAsByteArray()) should beInstanceOf<Gyldig>()
            FiltypeValidering.valider("pdf", "/Arbeidsforhold.pdf".fileAsByteArray()) should beInstanceOf<Gyldig>()
            FiltypeValidering.valider("png", "/cloud.png".fileAsByteArray()) should beInstanceOf<Gyldig>()
            FiltypeValidering.valider("txt", "/test.txt".fileAsByteArray()) should beInstanceOf<Ugyldig>()
        }
    }

    @Test
    fun `PDF riktig validerings resultat`() {
        runBlocking {
            PdfValidering.valider("txt", "/test.txt".fileAsByteArray()) should beInstanceOf<Gyldig>()
            PdfValidering.valider("pdf", "/Arbeidsforhold.pdf".fileAsByteArray()) should beInstanceOf<Gyldig>()
            PdfValidering.valider("protected", "/protected.pdf".fileAsByteArray()) should beInstanceOf<Ugyldig>()
        }
    }
}
