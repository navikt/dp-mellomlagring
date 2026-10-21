package no.nav.dagpenger.mellomlagring.lagring

// Etterligner det GCS returnerer for binær metadata: \n og \r blir \r\n, deretter UTF-8 Q-koding med _ for mellomrom.
internal fun gcsKod(bytes: ByteArray): String {
    val tekst =
        buildString {
            bytes.forEach { b ->
                when (b.toInt()) {
                    0x0a, 0x0d -> append("\r\n")
                    else -> append((b.toInt() and 0xff).toChar())
                }
            }
        }
    val kodet =
        tekst.toByteArray(Charsets.UTF_8).joinToString("") { b ->
            val c = b.toInt() and 0xff
            when {
                c == 0x20 -> "_"
                c in 0x21..0x7e && c.toChar() !in "=?_" -> c.toChar().toString()
                else -> "=%02X".format(c)
            }
        }
    return "=?UTF-8?Q?$kodet?="
}
