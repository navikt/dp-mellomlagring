package no.nav.dagpenger.mellomlagring.pdf

import io.kotest.assertions.throwables.shouldNotThrowAny
import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import org.junit.jupiter.api.Test
import java.io.File

internal class ImageProcessorTest {

    private val skrivTilFil = false

    @Test
    fun `slår sammen filer av ulike typer`() {
        shouldNotThrowAny() {
            ImageProcessor.convertAndMerge("/fisk1.jpg".fileAsByteArray(), "/fisk2.jpg".fileAsByteArray()).let {
                if (skrivTilFil) {
                    File("build/tmp/jpgOgJpg.pdf").writeBytes(it)
                }
            }

            ImageProcessor.convertAndMerge("/Arbeidsforhold.pdf".fileAsByteArray(), "/fisk2.jpg".fileAsByteArray()).let {
                if (skrivTilFil) {
                    File("build/tmp/PdfOgJpg.pdf").writeBytes(it)
                }
            }

            ImageProcessor.convertAndMerge("/Arbeidsforhold.pdf".fileAsByteArray(), "/Arbeidsforhold.pdf".fileAsByteArray()).let {
                if (skrivTilFil) {
                    File("build/tmp/PdfOgPdf.pdf").writeBytes(it)
                }
            }
        }
    }
}
