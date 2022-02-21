package no.nav.dagpenger.mellomlagring.av

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ScanResultTest {
    @Test
    fun infisert() {
        ScanResult("saf", "ok").infisert() shouldBe false
        ScanResult("saf", "OK").infisert() shouldBe false
        ScanResult("saf", "ikke").infisert() shouldBe true
    }
}
