package no.nav.dagpenger.mellomlagring.auth

import io.ktor.auth.AuthenticationContext
import no.nav.dagpenger.mellomlagring.Config
import no.nav.security.token.support.ktor.TokenValidationContextPrincipal

internal fun AuthenticationContext.fnr(): String =
    this.principal<TokenValidationContextPrincipal>()?.context?.getClaims(Config.tokenxIssuerName)?.subject ?: throw IllegalArgumentException("Fant ikke principal")
