package no.nav.dagpenger.mellomlagring.vedlegg

import no.nav.dagpenger.mellomlagring.test.fileAsByteArray
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

internal class ByteArrayConverterTest {

    @Disabled
    @Test
    fun `slår sammen filer`() {
        listOf("/fisk1.jpg", "/fisk2.jpg", "/smallimg.jpg", "/Arbeidsforhold.pdf")
            .map { it.fileAsByteArray() }
            .let {
                ByteArrayConverter.konverterOgMerge(it)
            }.also {
                File("build/tmp/merge.pdf").writeBytes(it)
            }
    }
}
