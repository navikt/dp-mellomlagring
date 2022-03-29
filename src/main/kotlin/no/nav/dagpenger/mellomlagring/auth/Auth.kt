package no.nav.dagpenger.mellomlagring.auth

import io.ktor.auth.AuthenticationContext
import no.nav.dagpenger.mellomlagring.Config
import no.nav.security.token.support.ktor.TokenValidationContextPrincipal

internal fun AuthenticationContext.fnr(): String {
    val context = this.principal<TokenValidationContextPrincipal>()?.context
    val claims = context?.getClaims(Config.tokenxIssuerName) ?: context?.getClaims(Config.azureAdIssuerName)
    return claims?.subject ?: throw IllegalArgumentException("Fant ikke principal")
}
