package org.rubenazo.conciertosfront.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository

class ConcertRepositoryImpl(
    private val provider: DatabaseProviderPort,
) : ConcertRepository {

    override fun getAllWithDetailsFlow(): Flow<List<Concert>> =
        provider.concertDao().getAllWithDetails().map { rows -> rows.toConcertList() }

    override fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<Concert>> =
        provider.concertDao().getByDateRangeWithDetails(startDate, endDate).map { rows -> rows.toConcertList() }

    override fun getUpcomingFlow(today: String): Flow<List<Concert>> =
        provider.concertDao().getUpcomingWithDetails(today).map { rows -> rows.toConcertList() }

    override fun getInBoundingBoxFlow(
        latMin: Double,
        latMax: Double,
        lngMin: Double,
        lngMax: Double,
        startDate: String,
        endDate: String,
    ): Flow<List<Concert>> =
        provider.concertDao().getConcertsInBoundingBox(latMin, latMax, lngMin, lngMax, startDate, endDate)
            .map { rows -> rows.toConcertList() }

    override suspend fun getAllWithDetails(): List<Concert> =
        provider.concertDao().getAllWithDetailsOnce().toConcertList()

    override suspend fun getCount(): Int = provider.concertDao().getCount()

    override suspend fun getById(id: String): Concert? =
        provider.concertDao().getByIdWithDetails(id).toConcertList().firstOrNull()
}
