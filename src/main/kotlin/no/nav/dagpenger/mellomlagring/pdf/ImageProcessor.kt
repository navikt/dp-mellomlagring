package no.nav.dagpenger.mellomlagring.pdf

import mu.KotlinLogging
import no.nav.dagpenger.io.Detect.isJpeg
import no.nav.dagpenger.io.Detect.isPdf
import no.nav.dagpenger.io.Detect.isPng
import no.nav.dagpenger.pdf.ImageConverter
import no.nav.dagpenger.pdf.ImageScaler
import no.nav.dagpenger.pdf.PDFDocument
import java.awt.Dimension
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val sikkerlogg = KotlinLogging.logger("tjenestekall")
object ImageProcessor {
    fun convertAndMerge(accumulator: ByteArray, currentValue: ByteArray): ByteArray {
        sikkerlogg.info { "Kjører convert and merge, bytearray størrelse er ${accumulator.size}" }
        return PDFDocument.merge(listOf(accumulator.tilPdf(), currentValue.tilPdf())).use { pdf ->
            ByteArrayOutputStream().use { os ->
                pdf.save(BufferedOutputStream(os))
                os.toByteArray()
            }
        }.also {
            sikkerlogg.info { "Convert and merge fullført, bytearray størrelse er ${accumulator.size}" }
        }
    }

    private fun ByteArray.tilPdf(): ByteArray {
        return when {
            this.isJpeg() -> {
                scaleImage(this, "jpeg")
            }
            this.isPng() -> {
                scaleImage(this, "png")
            }
            this.isPdf() -> {
                this
            }
            else -> {
                throw IllegalArgumentException("ukjent format")
            }
        }
    }

    private fun scaleImage(it: ByteArray, format: String): ByteArray {
        return ByteArrayOutputStream().use { os ->
            ImageIO.write(
                ImageScaler.scale(
                    it,
                    Dimension(1140, 1654),
                    ImageScaler.ScaleMode.SCALE_TO_FIT_INSIDE_BOX
                ),
                format, os
            )
            ImageConverter.toPDF(os.toByteArray())
        }
    }
}
