package no.nav.dagpenger.mellomlagring.pdf

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class BundleRequestDeserializerTest {
    @Test
    fun `deserialiserer riktig`() {
        val bundleRequest = ObjectMapper().readValue(bundleJson, BundleRequest::class.java)
        bundleRequest.bundleNavn shouldBe "bundle.pdf"
        bundleRequest.soknadId shouldBe "f48b82fc-face-4479-af52-3ff8bc6a2f72"
        bundleRequest.filer.size shouldBe 2
    }
}

@Language("JSON")
private val bundleJson = """{
  "bundleNavn": "bundle.pdf",
  "soknadId": "f48b82fc-face-4479-af52-3ff8bc6a2f72",
  "filer": [
    {
      "urn": "urn:vedlegg:f48b82fc-face-4479-af52-3ff8bc6a2f72/smallimg.jpg"
    },
    {
      "urn": "urn:vedlegg:f48b82fc-face-4479-af52-3ff8bc6a2f72/Arbeidsforhold.pdf"
    }
  ]
}"""
