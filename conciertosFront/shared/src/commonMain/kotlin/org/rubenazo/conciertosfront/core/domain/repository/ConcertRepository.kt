package org.rubenazo.conciertosfront.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.domain.model.Concert

interface ConcertRepository {
    fun getAllWithDetailsFlow(): Flow<List<Concert>>
    fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<Concert>>
    fun getUpcomingFlow(today: String): Flow<List<Concert>>
    fun getInBoundingBoxFlow(
        latMin: Double,
        latMax: Double,
        lngMin: Double,
        lngMax: Double,
        startDate: String,
        endDate: String,
    ): Flow<List<Concert>>
    suspend fun getAllWithDetails(): List<Concert>
    suspend fun getCount(): Int
    suspend fun getById(id: String): Concert?
}
