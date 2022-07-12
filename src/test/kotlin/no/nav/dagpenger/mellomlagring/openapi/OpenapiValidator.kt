package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.application.plugin
import io.ktor.server.routing.Routing
import io.mockk.mockk
import no.nav.dagpenger.mellomlagring.TestApplication
import no.nav.dagpenger.mellomlagring.vedlegg.vedleggApi
import org.junit.jupiter.api.Test

class OpenapiValidator {
    @Test
    fun validerOpenapi() {
        TestApplication.withMockAuthServerAndTestApplication({
            vedleggApi(mockk(relaxed = true))
        }) {
            this.application {
                val application = plugin(Routing).routesInApplication()
                val openApiSpec = Spec.fromJson(System.getProperty("user.dir") + "/doc/openapi.json")
                openApiSpec `should have same number of paths as` application
            }
        }
    }
}
