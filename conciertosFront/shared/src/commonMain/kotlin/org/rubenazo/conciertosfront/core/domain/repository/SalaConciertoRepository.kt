package org.rubenazo.conciertosfront.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts

interface SalaConciertoRepository {
    fun getAllFlow(): Flow<List<SalaConcierto>>
    suspend fun getAll(): List<SalaConcierto>
    suspend fun getCount(): Int
    fun getByDateRangeWithConcertsFlow(startDate: String, endDate: String): Flow<List<SalaWithConcerts>>
}
