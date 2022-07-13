package no.nav.dagpenger.mellomlagring.openapi

import java.io.File

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

internal class PathAssertionError {
    private val missingPaths: MutableList<Path> = mutableListOf()
    private val superfluousPaths: MutableList<Path> = mutableListOf()

    fun evaluate() {
        if (missingPaths.isNotEmpty() || superfluousPaths.isNotEmpty()) {
            throw AssertionError("Incorrect paths in apidoc\n${missingPathToString()}\n${superfluousPathsToString()} ")
        }
    }

    fun addMissingPaths(it: List<Path>) {
        missingPaths.addAll(it)
    }

    fun addsuperfluousPaths(it: List<Path>) {
        superfluousPaths.addAll(it)
    }

    private fun missingPathToString(): String = "${missingPaths.size.are()} missing: ${missingPaths.names()}"
    private fun superfluousPathsToString(): String =
        "${superfluousPaths.size.are()}  superfluous:${superfluousPaths.names()}"

    private fun MutableList<Path>.names() = this.map { it.value }
}

private fun Int.are(): String = this.let {
    if (it <= 1) {
        "$this is"
    } else {
        "$this are"
    }
}

internal class MethodAssertionError(message: String) : AssertionError(message)
internal class MissingSpecContentError(filelocation: String) :
    AssertionError("Updated spec was written to $filelocation, but requires additional information")

internal fun withRecovery(recovery: SpecAssertionRecovery, function: () -> Unit) {
    try {
        function()
    } catch (pathAssertionError: AssertionError) {
        recovery.pathAssertionError()
    } finally {
        // TODO
        recovery.metohdAssertionError()
        recovery.writeToFile()
    }
}
