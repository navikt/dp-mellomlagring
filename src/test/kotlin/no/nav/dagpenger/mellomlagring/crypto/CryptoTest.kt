package no.nav.dagpenger.mellomlagring.crypto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class CryptoTest {
    @Test
    fun crypto() {
        val crypto = AESCrypto(passphrase = "jubba", iv = "hubba")
        crypto.encrypt("12345678910").also {
            crypto.decrypt(it) shouldBe "12345678910"
        }
    }
}
