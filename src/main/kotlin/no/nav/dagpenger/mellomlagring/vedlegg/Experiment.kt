package no.nav.dagpenger.mellomlagring.vedlegg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.nio.file.Paths

private val logger = KotlinLogging.logger { }

internal fun CoroutineScope.hubba(mediator: Mediator, fullPath: String, eier: String) {
    this.launch(Dispatchers.IO) {
        kotlin.runCatching {
            val key = Paths.get(fullPath).parent.toString().also { logger.info { "Path: $it" } }
            mediator.liste(key, eier).let {
                logger.info { it }
            }
        }.onFailure {
            logger.error { it }
        }
    }
}
