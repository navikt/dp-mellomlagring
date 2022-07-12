package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr
import no.nav.dagpenger.mellomlagring.openapi.ApplicationSpec.Companion.pathfromRoute
import no.nav.security.mock.oauth2.http.objectMapper
import java.io.File

internal fun Routing.routesInApplication(): ApplicationSpec {
    val newPaths = mutableListOf<Path>()
    allRoutes(this).filter { it.selector is HttpMethodRouteSelector }
        .groupBy { it.parent }
        .forEach { route ->
            pathfromRoute(route)
            val methods = mutableListOf<String>()
            val path = route.key
                .toStr()
                .split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() }
            route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
            newPaths.add(Path(path, methods))
        }
    return ApplicationSpec(newPaths)
}

private fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

data class Path(val value: String, val methods: List<String>) {
    override fun equals(other: Any?): Boolean {
        require(other is Path)
        return this.value.compareTo(other.value) == 0
    }
}

internal class ApplicationSpec(val paths: List<Path>) {
    companion object {
        fun pathfromRoute(route: Map.Entry<Route?, List<Route>>): Path {
            val methods = mutableListOf<String>()
            val path = route.key.toStr().split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() + ":" }
            route.value.forEach { methods.add((it.selector as HttpMethodRouteSelector).method.value.lowercase()) }
            return Path(path, methods)
        }
    }
}

internal class OpenApiSpec(var paths: List<Path>) {
    infix fun `should contain the same paths as`(application: ApplicationSpec) {
        when {
            // application.paths.any { !this.paths.contains(it) } -> throw PathAssertionError("Openapi spec is missing ${missingPaths(application).size} paths")
            this.paths.any { !application.paths.contains(it) } -> throw PathAssertionError("Openapi spec contains ${notPresentPaths(application).size} path(s) that is not present in the application API")
        }
    }

    internal fun updatePaths(application: ApplicationSpec) {
        val pathsToBeAdded = missingPaths(application)
        val pathsToBeRemoved = notPresentPaths(application)
        this.paths = this.paths - pathsToBeRemoved + pathsToBeAdded
    }

    private fun missingPaths(application: ApplicationSpec): List<Path> = application.paths.filterNot { this.paths.contains(it) }
    private fun notPresentPaths(application: ApplicationSpec): List<Path> = paths.filterNot { application.paths.contains(it) }

    companion object {
        fun fromJson(openapiFilePath: String): OpenApiSpec {
            val paths = mutableListOf<Path>()
            objectMapper.readTree(File(openapiFilePath)).let {
                it["paths"].fields().forEachRemaining {
                    val methods = mutableListOf<String>()
                    it.value.fields().forEachRemaining {
                        methods.add(it.key)
                    }
                    paths.add(Path(it.key, methods))
                }
            }
            return OpenApiSpec(paths)
        }
    }
}

internal class AssertionSpecRecovery(
    val openApiSpec: OpenApiSpec,
    val application: ApplicationSpec,
    private val recoveryFilePath: String = "build/tmp/openapi.json"
) {
    private var pathAssertionRecovered = false
    private var methodAssertionRecovered = false
    fun pathAssertionError() {
        openApiSpec.updatePaths(application)
        pathAssertionRecovered = true
    }
    fun metohdAssertionError() {
        methodAssertionRecovered = true
    }

    fun writeToFile() {
        if (pathAssertionRecovered && methodAssertionRecovered) {
            println("skriv til fil $recoveryFilePath")
            File(recoveryFilePath).writeBytes("slks".toByteArray())
            throw MissingSpecContentError(recoveryFilePath)
        }
    }
}

internal class PathAssertionError(message: String) : AssertionError(message)
internal class MethodAssertionError(message: String) : AssertionError(message)
internal class MissingSpecContentError(filelocation: String) : AssertionError("Updated spec was written to $filelocation, but requires additional information")
