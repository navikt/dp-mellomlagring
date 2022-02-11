package no.nav.dagpenger.mellomlagring

import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.features.CallLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.mellomlagring.api.health
import no.nav.dagpenger.mellomlagring.api.vedleggApi
import no.nav.dagpenger.mellomlagring.crypto.AESCrypto
import no.nav.dagpenger.mellomlagring.lagring.S3Store
import no.nav.dagpenger.mellomlagring.lagring.VedleggService
import no.nav.security.token.support.ktor.tokenValidationSupport

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CallLogging) {
            disableDefaultColors()
        }
        install(Authentication) {
            tokenValidationSupport(config = environment.config)
        }

        health()
        vedleggApi(
            VedleggService(
                store = S3Store(Config.storage),
                crypto = AESCrypto(
                    passphrase = Config.crypto.passPhrase,
                    iv = Config.crypto.salt
                )
            )
        )
    }.start(wait = true)
}
