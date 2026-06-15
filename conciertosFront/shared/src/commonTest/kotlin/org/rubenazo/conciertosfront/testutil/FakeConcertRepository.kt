package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository

class FakeConcertRepository : ConcertRepository {
    var concerts: List<Concert> = emptyList()
    var upcomingConcerts: List<Concert>? = null
    var boundingBoxConcerts: List<Concert> = emptyList()

    override fun getAllWithDetailsFlow(): Flow<List<Concert>> = flowOf(concerts)
    override fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<Concert>> = flowOf(concerts)
    override fun getUpcomingFlow(today: String): Flow<List<Concert>> = flowOf(upcomingConcerts ?: concerts)
    override fun getInBoundingBoxFlow(
        latMin: Double,
        latMax: Double,
        lngMin: Double,
        lngMax: Double,
        startDate: String,
        endDate: String,
    ): Flow<List<Concert>> = flowOf(boundingBoxConcerts)
    override suspend fun getAllWithDetails(): List<Concert> = concerts
    override suspend fun getCount(): Int = concerts.size
    override suspend fun getById(id: String): Concert? = concerts.find { it.id == id }
}
