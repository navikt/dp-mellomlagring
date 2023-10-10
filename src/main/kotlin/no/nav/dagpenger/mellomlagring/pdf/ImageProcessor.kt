package no.nav.dagpenger.mellomlagring.pdf

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

internal object ImageProcessor {
    fun mergePdf(
        accumulator: ByteArray,
        currentValue: ByteArray,
    ): ByteArray {
        return PDFDocument.merge(listOf(accumulator, currentValue)).use { pdf ->
            ByteArrayOutputStream().use { os ->
                pdf.save(BufferedOutputStream(os))
                os.toByteArray()
            }
        }
    }

    fun ByteArray.tilPdf(): ByteArray {
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

    private fun scaleImage(
        it: ByteArray,
        format: String,
    ): ByteArray {
        return ByteArrayOutputStream().use { os ->
            ImageIO.write(
                ImageScaler.scale(
                    it,
                    Dimension(1140, 1654),
                    ImageScaler.ScaleMode.SCALE_TO_FIT_INSIDE_BOX,
                ),
                format,
                os,
            )
            ImageConverter.toPDF(os.toByteArray())
        }
    }
}
