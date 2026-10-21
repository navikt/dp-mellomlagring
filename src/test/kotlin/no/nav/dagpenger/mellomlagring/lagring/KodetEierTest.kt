package no.nav.dagpenger.mellomlagring.lagring

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class KodetEierTest {
    @Test
    fun `gjenkjenner kodet verdi`() {
        KodetEier.erKodet("=?UTF-8?Q?abc?=") shouldBe true
        KodetEier.erKodet("abc") shouldBe false
        KodetEier.erKodet(null) shouldBe false
    }

    @Test
    fun `entydig verdi gir én kandidat`() {
        val bytes = byteArrayOf(0x00, 0x20, 0x41, 0x5f, 0x3d, 0x3f, 0x7f, 0x80.toByte(), 0xff.toByte())

        KodetEier.kandidater(gcsKod(bytes)).map { it.toList() } shouldBe listOf(bytes.toList())
    }

    @Test
    fun `hver tvetydig posisjon gir tre alternativer, færrest CRLF og CR først`() {
        val kandidater = KodetEier.kandidater(gcsKod(byteArrayOf(0x41, 0x0a, 0x42)))

        kandidater.map { it.toList() } shouldBe
            listOf(
                byteArrayOf(0x41, 0x0a, 0x42),
                byteArrayOf(0x41, 0x0d, 0x42),
                byteArrayOf(0x41, 0x0d, 0x0a, 0x42),
            ).map { it.toList() }
    }

    @Test
    fun `opprinnelig verdi er alltid blant kandidatene for tilfeldige byte`() {
        val random = Random(42)
        repeat(5_000) {
            val bytes = random.nextBytes(141)
            val tvetydige = bytes.count { it == 0x0a.toByte() || it == 0x0d.toByte() }
            val kandidater = KodetEier.kandidater(gcsKod(bytes))
            if (tvetydige <= 3) {
                kandidater.any { it.contentEquals(bytes) } shouldBe true
            }
        }
    }

    @Test
    fun `for mange tvetydige posisjoner gir ingen kandidater`() {
        KodetEier.kandidater(gcsKod(ByteArray(7) { 0x0a })).shouldBeEmpty()
    }

    @Test
    fun `antall kandidater er begrenset`() {
        KodetEier.kandidater(gcsKod(ByteArray(6) { 0x0a })) shouldHaveSize KodetEier.MAKS_KANDIDATER
    }

    @Test
    fun `B-koding og flere kodede ord støttes`() {
        KodetEier.kandidater("=?UTF-8?B?QUI=?= =?ISO-8859-1?Q?=FF?=").single().toList() shouldBe
            byteArrayOf(0x41, 0x42, 0xff.toByte()).toList()
    }

    @Test
    fun `ugyldig eller ukjent format gir ingen kandidater`() {
        KodetEier.kandidater("ikke kodet").shouldBeEmpty()
        KodetEier.kandidater("=?UTF-8?Q?abc?= ekstra").shouldBeEmpty()
        KodetEier.kandidater("=?UTF-8?Q?=ZZ?=").shouldBeEmpty()
        KodetEier.kandidater("=?UTF-8?Q?abc=?=").shouldBeEmpty()
        KodetEier.kandidater("=?UKJENT-CHARSET?Q?abc?=").shouldBeEmpty()
        // U+0100 kan ikke komme fra en ISO-8859-1-verdi
        KodetEier.kandidater("=?UTF-8?Q?=C4=80?=").shouldBeEmpty()
    }
}
