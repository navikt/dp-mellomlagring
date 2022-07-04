package no.nav.dagpenger.mellomlagring.vedlegg

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
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

internal class BundleFileUploadHandler(private val mediator: Mediator) {

    suspend fun handleFileupload(multiPartData: MultiPartData, soknadsId: String): Pair<String, VedleggUrn> {
        val parts = multiPartData.readAllParts()
        val filnavn = parts.filterIsInstance<PartData.FormItem>().first { it.name == "bundleFilnavn" }.value

        val fileItems = parts.filterIsInstance<PartData.FileItem>()
        if (fileItems.isEmpty()) throw IllegalArgumentException("Body mangler innhold")
        return fileItems
            .map { it.streamProvider().readBytes() }
            .let { ByteArrayConverter.konverterOgMerge(it) }
            .let {
                mediator.lagre(soknadsId, filnavn, it)
            }.let {
                Pair(filnavn, it)
            }
    }
}

object ByteArrayConverter {
    fun konverterOgMerge(bytearrays: List<ByteArray>): ByteArray =
        bytearrays.map {
            when {
                it.isJpeg() -> {
                    scaleImage(it, "jpeg")
                }
                it.isPng() -> {
                    scaleImage(it, "png")
                }
                it.isPdf() -> {
                    it
                }
                else -> {
                    throw IllegalArgumentException("ukjent format")
                }
            }
        }
            .let {
                PDFDocument.merge(it)
            }
            .use { pdf ->
                ByteArrayOutputStream().use { os ->
                    pdf.save(BufferedOutputStream(os))
                    os.toByteArray()
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
