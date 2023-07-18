package no.nav.dagpenger.mellomlagring.pdf

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import no.nav.dagpenger.mellomlagring.pdf.ImageProcessor.tilPdf
import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import org.junit.jupiter.api.Test
import java.io.File

internal class ImageProcessorTest {

    private val skrivTilFil = true

    @Test
    fun `Konverterer gyldige filer til pdf`() {
        shouldNotThrowAny {
            "/fisk1.jpg".fileAsByteArray().tilPdf().takeIf { skrivTilFil }?.let {
                File("build/tmp/fisk1.pdf").writeBytes(it)
            }

            "/cloud.png".fileAsByteArray().tilPdf().takeIf { skrivTilFil }?.let {
                File("build/tmp/cloud.pdf").writeBytes(it)
            }

            "/Arbeidsforhold.pdf".fileAsByteArray().tilPdf().takeIf { skrivTilFil }?.let {
                File("build/tmp/arbeidsforhold.pdf").writeBytes(it)
            }
        }

        shouldThrow<IllegalArgumentException> {
            "/test.txt".fileAsByteArray().tilPdf()
        }
    }

    @Test
    fun `slår sammen pdfer`() {
        shouldNotThrowAny() {
            ImageProcessor.mergePdf("/fisk1.jpg".fileAsByteArray().tilPdf(), "/fisk2.jpg".fileAsByteArray().tilPdf())
                .takeIf { skrivTilFil }
                ?.let {
                    File("build/tmp/jpgOgJpg.pdf").writeBytes(it)
                }

            ImageProcessor.mergePdf(
                "/Arbeidsforhold.pdf".fileAsByteArray().tilPdf(),
                "/fisk2.jpg".fileAsByteArray().tilPdf(),
            )
                .takeIf { skrivTilFil }
                ?.let {
                    File("build/tmp/PdfOgJpg.pdf").writeBytes(it)
                }

            ImageProcessor.mergePdf(
                "/Arbeidsforhold.pdf".fileAsByteArray().tilPdf(),
                "/Arbeidsforhold.pdf".fileAsByteArray().tilPdf(),
            )
                .takeIf { skrivTilFil }
                ?.let {
                    File("build/tmp/PdfOgPdf.pdf").writeBytes(it)
                }
        }
    }

    @Test
    fun `Exception ved merge av filer som ikke er pdf`() {
        shouldThrow<IllegalArgumentException> {
            ImageProcessor.mergePdf("/Arbeidsforhold.pdf".fileAsByteArray(), "/fisk1.jpg".fileAsByteArray())
        }
    }
}
