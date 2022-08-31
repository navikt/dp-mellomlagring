package no.nav.dagpenger.mellomlagring

import org.skyscreamer.jsonassert.JSONAssert

internal infix fun String.shouldBeJson(other: String?) {
    JSONAssert.assertEquals(this, other, false)
}
