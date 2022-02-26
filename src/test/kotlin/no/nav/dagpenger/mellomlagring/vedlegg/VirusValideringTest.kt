package no.nav.dagpenger.mellomlagring.vedlegg

import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.mellomlagring.av.AntiVirus
import org.junit.jupiter.api.Test

internal class VirusValideringTest {
    @Test
    fun `Riktig validgerings resultat`() {
        VirusValidering(
            mockk<AntiVirus>().also {
                coEvery { it.infisert("infisert", any()) } returns true
                coEvery { it.infisert("ok", any()) } returns false
            }
        ).let {
            runBlocking {
                it.valider("infisert", "".toByteArray()) should beInstanceOf<FilValideringResultat.Ugyldig>()
                it.valider("ok", "".toByteArray()) should beInstanceOf<FilValideringResultat.Gyldig>()
            }
        }
    }
}
