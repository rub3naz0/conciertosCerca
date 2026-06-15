package org.rubenazo.conciertosfront.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.VenueConcert
import org.rubenazo.conciertosfront.core.domain.repository.SalaConciertoRepository

class SalaConciertoRepositoryImpl(
    private val provider: DatabaseProviderPort,
) : SalaConciertoRepository {

    override fun getAllFlow(): Flow<List<SalaConcierto>> =
        provider.salaConciertoDao().getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<SalaConcierto> =
        provider.salaConciertoDao().getAll().first().map { it.toDomain() }

    override suspend fun getCount(): Int = provider.salaConciertoDao().getCount()

    override fun getByDateRangeWithConcertsFlow(
        startDate: String,
        endDate: String,
    ): Flow<List<SalaWithConcerts>> =
        provider.salaConciertoDao().getByDateRangeWithConcerts(startDate, endDate).map { rows ->
            rows.groupBy { it.salaId }.map { (_, salaRows) ->
                val first = salaRows.first()
                SalaWithConcerts(
                    sala = SalaConcierto(
                        id = first.salaId,
                        name = first.salaName,
                        address = first.salaAddress,
                        city = first.salaCity,
                        province = first.salaProvince,
                        lat = first.salaLat,
                        lng = first.salaLng,
                        imageUrl = first.salaImageUrl,
                        description = first.salaDescription,
                        sourceUrl = first.salaSourceUrl,
                    ),
                    upcomingConcerts = salaRows
                        .filter { it.concertId != null && it.concertDate != null }
                        .groupBy { it.concertId }
                        .map { (_, concertRows) ->
                            val c = concertRows.first()
                            VenueConcert(
                                id = c.concertId!!,
                                date = c.concertDate!!,
                                time = c.concertTime,
                                artistNames = concertRows.mapNotNull { it.artistName },
                            )
                        },
                )
            }
        }
}
