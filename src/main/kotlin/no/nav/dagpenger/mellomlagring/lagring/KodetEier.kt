package no.nav.dagpenger.mellomlagring.lagring

import org.apache.commons.codec.net.BCodec
import org.apache.commons.codec.net.QCodec
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * GCS returnerer binær eier-metadata RFC 2047-kodet (=?UTF-8?Q?...?=) og gjør samtidig om både \n og \r til \r\n.
 * Dekodingen er derfor ikke entydig: hver \r\n kan opprinnelig ha vært \n, \r eller \r\n.
 * [kandidater] gir mulige opprinnelige verdier, mest sannsynlige først. Kalleren må verifisere med AEAD.
 */
internal object KodetEier {
    private val latin1 = Charset.forName("ISO-8859-1")
    private val kodetOrd = Regex("""=\?([^?]+)\?([QqBb])\?([^?]*)\?=""")
    private const val MAKS_TVETYDIGE = 6
    internal const val MAKS_KANDIDATER = 32

    fun erKodet(verdi: String?): Boolean = verdi?.startsWith("=?") == true

    fun kandidater(kodet: String): List<ByteArray> {
        val bytes = dekod(kodet) ?: return emptyList()
        val deler = splittPåCrLf(bytes)
        val tvetydige = deler.size - 1
        if (tvetydige > MAKS_TVETYDIGE) return emptyList()
        return valg(tvetydige)
            .take(MAKS_KANDIDATER)
            .map { valg -> sett(deler, valg) }
            .toList()
    }

    private fun dekod(kodet: String): ByteArray? {
        val ord = kodetOrd.findAll(kodet).toList()
        if (ord.isEmpty()) return null
        // Mellomrom mellom kodede ord skal ignoreres (RFC 2047 avsnitt 6.2). Annet innhold er ukjent format.
        if (kodetOrd.replace(kodet, "").isNotBlank()) return null
        val tekst =
            runCatching {
                ord.joinToString("") { treff ->
                    when (treff.groupValues[2].uppercase()) {
                        "Q" -> QCodec().decode(treff.value)
                        else -> BCodec().decode(treff.value)
                    }
                }
            }.getOrNull() ?: return null
        // Opprinnelig verdi er ISO-8859-1, så hvert tegn må ligge i U+0000–U+00FF.
        if (tekst.any { it.code > 0xFF }) return null
        return tekst.toByteArray(latin1)
    }

    private fun splittPåCrLf(bytes: ByteArray): List<ByteArray> {
        val deler = mutableListOf<ByteArray>()
        var start = 0
        var i = 0
        while (i < bytes.size - 1) {
            if (bytes[i] == CR && bytes[i + 1] == LF) {
                deler.add(bytes.copyOfRange(start, i))
                start = i + 2
                i += 2
            } else {
                i++
            }
        }
        deler.add(bytes.copyOfRange(start, bytes.size))
        return deler
    }

    // Rekkefølge: færrest \r\n-par først, deretter færrest \r. Ekte \r\n-par i ciphertext er sjeldne.
    private fun valg(antall: Int): Sequence<List<ByteArray>> {
        val alternativer = listOf(byteArrayOf(LF), byteArrayOf(CR), byteArrayOf(CR, LF))
        var alle = listOf(emptyList<Int>())
        repeat(antall) { alle = alle.flatMap { prefiks -> alternativer.indices.map { prefiks + it } } }
        return alle
            .sortedWith(compareBy({ v -> v.count { it == 2 } }, { v -> v.count { it == 1 } }))
            .asSequence()
            .map { v -> v.map { alternativer[it] } }
    }

    private fun sett(
        deler: List<ByteArray>,
        skilletegn: List<ByteArray>,
    ): ByteArray {
        val ut = ByteArrayOutputStream()
        ut.write(deler[0])
        skilletegn.forEachIndexed { i, s ->
            ut.write(s)
            ut.write(deler[i + 1])
        }
        return ut.toByteArray()
    }

    private const val CR: Byte = 0x0d
    private const val LF: Byte = 0x0a
}
