package org.rubenazo.conciertosfront.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts

/**
 * Read port for venues (salas), backed by the local Room cache. `...Flow` methods are reactive and
 * re-emit on data changes; `suspend` methods are one-shot reads. [getByDateRangeWithConcertsFlow]
 * joins each venue with the concerts it hosts in the selected range.
 */
interface SalaConciertoRepository {
    fun getAllFlow(): Flow<List<SalaConcierto>>
    suspend fun getAll(): List<SalaConcierto>
    suspend fun getCount(): Int
    fun getByDateRangeWithConcertsFlow(startDate: String, endDate: String): Flow<List<SalaWithConcerts>>
}
