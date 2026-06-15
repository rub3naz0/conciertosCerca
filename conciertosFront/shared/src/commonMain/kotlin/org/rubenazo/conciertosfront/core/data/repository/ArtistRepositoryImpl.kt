package org.rubenazo.conciertosfront.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistWithConcertRow
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.UpcomingConcert
import org.rubenazo.conciertosfront.core.domain.repository.ArtistRepository

class ArtistRepositoryImpl(
    private val provider: DatabaseProviderPort,
) : ArtistRepository {

    override fun getAllFlow(): Flow<List<Artist>> =
        provider.artistDao().getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getAllWithUpcomingConcertsFlow(today: String): Flow<List<ArtistWithConcerts>> =
        provider.artistDao().getAllWithUpcomingConcerts(today).map { it.toArtistsWithConcerts() }

    override fun getByDateRangeWithConcertsFlow(
        startDate: String,
        endDate: String,
    ): Flow<List<ArtistWithConcerts>> =
        provider.artistDao().getByDateRangeWithConcerts(startDate, endDate).map { it.toArtistsWithConcerts() }

    override fun searchByNameWithConcertsFlow(
        query: String,
        today: String,
    ): Flow<List<ArtistWithConcerts>> =
        provider.artistDao().searchByNameWithUpcomingConcerts(query.escapeLikeWildcards(), today)
            .map { it.toArtistsWithConcerts() }

    override suspend fun getAll(): List<Artist> =
        provider.artistDao().getAll().first().map { it.toDomain() }

    override suspend fun getCount(): Int = provider.artistDao().getCount()
}

/**
 * SQLite LIKE treats `%` and `_` as wildcards, so user input containing them would match
 * unintended rows. The DAO query declares `ESCAPE '\'`; this escapes the backslash itself first.
 */
private fun String.escapeLikeWildcards(): String =
    replace("""\""", """\\""")
        .replace("%", """\%""")
        .replace("_", """\_""")

private fun List<ArtistWithConcertRow>.toArtistsWithConcerts(): List<ArtistWithConcerts> =
    groupBy { it.artistId }.map { (_, artistRows) ->
        val first = artistRows.first()
        ArtistWithConcerts(
            artist = Artist(
                id = first.artistId,
                name = first.artistName,
                genre = first.artistGenre,
                imageUrl = first.artistImageUrl,
                website = first.artistWebsite,
                description = first.artistDescription,
                sourceUrl = first.artistSourceUrl,
            ),
            upcomingConcerts = artistRows.mapNotNull { row ->
                if (row.concertId != null && row.concertDate != null &&
                    row.salaName != null && row.salaCity != null
                ) {
                    UpcomingConcert(
                        id = row.concertId,
                        date = row.concertDate,
                        time = row.concertTime,
                        salaName = row.salaName,
                        salaCity = row.salaCity,
                    )
                } else null
            }
        )
    }
