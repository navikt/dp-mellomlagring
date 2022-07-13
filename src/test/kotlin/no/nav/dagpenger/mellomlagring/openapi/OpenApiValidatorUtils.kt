package no.nav.dagpenger.mellomlagring.openapi

import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.mockk.InternalPlatformDsl.toStr
import java.io.File

internal fun Routing.routesInApplication(): ApplicationSpec {
    val newPaths = mutableListOf<Path.ApplicationPath>()
    allRoutes(this).filter { it.selector is HttpMethodRouteSelector }
        .groupBy { it.parent }
        .forEach { route ->
            val (path, methods) = ApplicationSpec.pathFromRoute(route)
            newPaths.add(Path.ApplicationPath(path, methods))
        }
    return ApplicationSpec(newPaths)
}

private fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

internal class ApplicationSpec(val paths: List<Path.ApplicationPath>) {
    companion object {
        internal fun pathFromRoute(route: Map.Entry<Route?, List<Route>>): Pair<String, MutableList<Method>> {
            val methods = mutableListOf<Method>()
            val path = route.key
                .toStr()
                .split("/(authenticate azureAd)", "/(authenticate tokenX)")
                .let { it.first() + it.last() }
            route.value.forEach { methods.add(Method((it.selector as HttpMethodRouteSelector).method.value.lowercase())) }
            return Pair(path, methods)
        }
    }
}

internal class OpenApiSpec(var paths: List<Path>, private val serDer: OpenApiSerDer) {
    infix fun `should contain the same paths as`(application: ApplicationSpec) {
        when {
            application.paths.any { !this.paths.contains(it) } -> throw PathAssertionError(
                "Openapi spec is missing ${
                missingPathsInSpec(
                    application
                ).size
                } paths"
            )
            this.paths.any { !application.paths.contains(it) } -> throw PathAssertionError(
                "Openapi spec contains ${
                pathsNotInApplication(
                    application
                ).size
                } path(s) that is not present in the application API"
            )
        }
    }

    infix fun `paths should have the same methods as`(application: ApplicationSpec) {
        // gå igjennom hver path of sammenligne method, returnere metoder som må legges til (som pair?)
        // gå igjennom hver path of sammenligne method, returnere metoder som må fjernes (som pair?)
        // kaste excpetions hvis ikke begge listene er tomme
        pathsInBothSpecAndApplication(application).map {
            it?.let {
            }
        }
    }

    private fun pathsInBothSpecAndApplication(application: ApplicationSpec) =
        this.paths.map { specPath ->
            application.paths.find { it == specPath }?.let { applicationPath ->
                Pair(specPath, applicationPath)
            }
        }

    internal fun updatePaths(application: ApplicationSpec) {
        val pathsToBeAdded = missingPathsInSpec(application)
        val pathsToBeRemoved = pathsNotInApplication(application)
        this.paths = this.paths - pathsToBeRemoved + pathsToBeAdded
    }

    private fun missingPathsInSpec(application: ApplicationSpec): List<Path> =
        application.paths.filterNot { this.paths.contains(it) }

    private fun pathsNotInApplication(application: ApplicationSpec): List<Path> =
        paths.filterNot { application.paths.contains(it) }

    fun toJson(): ByteArray = serDer.generateNewSpecFile(this).toByteArray()

    companion object {
        fun fromJson(openapiFilePath: String): OpenApiSpec {
            return OpenApiSerDer.fromFile(openapiFilePath).let {
                OpenApiSpec(
                    paths = it.paths.map { path ->
                        Path.OpenApiSpecPath(
                            value = path.key,
                            methods = path.value.map { method -> Method(method.key) }
                        )
                    },
                    serDer = it
                )
            }
        }
    }
}

abstract class Path(val value: String, val methods: List<Method>) {
    override fun equals(other: Any?): Boolean {
        require(other is Path)
        return this.value.compareTo(other.value) == 0
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + methods.hashCode()
        return result
    }

    class ApplicationPath(value: String, methods: List<Method>) : Path(value, methods)
    class OpenApiSpecPath(value: String, methods: List<Method>) : Path(value, methods)
}

data class Method(val operation: String)

internal class SpecAssertionRecovery(
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
            File(recoveryFilePath).writeBytes(openApiSpec.toJson())
            throw MissingSpecContentError(recoveryFilePath)
        }
    }
}

internal class PathAssertionError(message: String) : AssertionError(message)
internal class MethodAssertionError(message: String) : AssertionError(message)
internal class MissingSpecContentError(filelocation: String) :
    AssertionError("Updated spec was written to $filelocation, but requires additional information")
