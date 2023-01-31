package no.nav.dagpenger.mellomlagring.api

import java.net.URI

data class HttpProblem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank"),
    val errorType: String? = null
)
