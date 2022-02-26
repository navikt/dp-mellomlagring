package no.nav.dagpenger.mellomlagring.vedlegg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

internal class VedleggUrnTest {
    @Test
    fun equality() {
        VedleggUrn("nss") shouldBe VedleggUrn("nss")
        VedleggUrn("nss") shouldNotBe VedleggUrn("nsss")
        VedleggUrn("nss") shouldNotBe null
    }

    @Test
    fun stringify() {
        VedleggUrn("1").toString() shouldBe "urn:vedlegg:1"
        VedleggUrn("id/1").toString() shouldBe "urn:vedlegg:id/1"
    }

    @Test
    fun asJson() {
        jacksonObjectMapper().writeValueAsString(VedleggUrn("ss")) shouldBe """{"urn":"urn:vedlegg:ss"}"""
    }

    @Test
    fun validation() {
        shouldThrow<IllegalArgumentException> { VedleggUrn("``") }
    }
}
