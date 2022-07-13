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
                val openApiSpec = OpenApiSpec.fromJson(System.getProperty("user.dir") + "/doc/openapi.json")
                val recovery = SpecAssertionRecovery(openApiSpec, application)
//                withRecovery(recovery) {
                openApiSpec `should contain the same paths as` application
//                }
                // openApiSpec `paths should have the same methods as` application
            }
        }
    }
}
