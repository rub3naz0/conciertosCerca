package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts
import org.rubenazo.conciertosfront.core.domain.repository.SalaConciertoRepository

class FakeSalaConciertoRepository : SalaConciertoRepository {
    var salas: List<SalaConcierto> = emptyList()
    var salasWithConcerts: List<SalaWithConcerts> = emptyList()

    override fun getAllFlow(): Flow<List<SalaConcierto>> = flowOf(salas)
    override suspend fun getAll(): List<SalaConcierto> = salas
    override suspend fun getCount(): Int = salas.size
    override fun getByDateRangeWithConcertsFlow(startDate: String, endDate: String): Flow<List<SalaWithConcerts>> = flowOf(salasWithConcerts)
}
