package org.rubenazo.conciertosfront.core.data.repository

import org.rubenazo.conciertosfront.core.data.local.dao.ConcertWithDetails
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertEntity
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertDto
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SyncedConcert
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto

fun SalaConciertoDto.toEntity(): SalaConciertoEntity = SalaConciertoEntity(
    id = id,
    name = name,
    address = address,
    city = city,
    province = province,
    lat = lat,
    lng = lng,
    imageUrl = imageUrl,
    description = description,
    sourceUrl = sourceUrl
)

fun ArtistDto.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    genre = genre,
    imageUrl = imageUrl,
    website = website,
    description = description,
    sourceUrl = sourceUrl
)

fun ConcertDto.toEntity(): ConcertEntity = ConcertEntity(
    id = id,
    salaConciertoId = salaConciertoId,
    date = date,
    time = time,
    price = price,
    sourceUrl = sourceUrl,
    updatedAt = updatedAt
)

fun SalaConciertoDto.toDomain(): SalaConcierto = SalaConcierto(
    id = id,
    name = name,
    address = address,
    city = city,
    province = province,
    lat = lat,
    lng = lng,
    imageUrl = imageUrl,
    description = description,
    sourceUrl = sourceUrl
)

fun ArtistDto.toDomain(): Artist = Artist(
    id = id,
    name = name,
    genre = genre,
    imageUrl = imageUrl,
    website = website,
    description = description,
    sourceUrl = sourceUrl
)

fun ConcertDto.toSynced(): SyncedConcert = SyncedConcert(
    id = id,
    salaConciertoId = salaConciertoId,
    artistIds = artistIds,
    date = date,
    time = time,
    price = price
)

fun SalaConciertoEntity.toDomain(): SalaConcierto = SalaConcierto(
    id = id,
    name = name,
    address = address,
    city = city,
    province = province,
    lat = lat,
    lng = lng,
    imageUrl = imageUrl,
    description = description,
    sourceUrl = sourceUrl
)

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    genre = genre,
    imageUrl = imageUrl,
    website = website,
    description = description,
    sourceUrl = sourceUrl
)

fun List<ConcertWithDetails>.toConcertList(): List<Concert> {
    return groupBy { it.concertId }.map { (_, rows) ->
        val first = rows.first()
        val sala = SalaConcierto(
            id = first.salaId,
            name = first.salaName,
            address = first.salaAddress,
            city = first.salaCity,
            province = first.salaProvince,
            lat = first.salaLat,
            lng = first.salaLng,
            imageUrl = first.salaImageUrl,
            description = first.salaDescription,
            sourceUrl = first.salaSourceUrl
        )
        val artists = rows
            .filter { it.artistId != null }
            .sortedBy { it.artistPosition }
            .map { row ->
                Artist(
                    id = row.artistId!!,
                    name = row.artistName!!,
                    genre = row.artistGenre,
                    imageUrl = row.artistImageUrl,
                    website = row.artistWebsite,
                    description = row.artistDescription,
                    sourceUrl = row.artistSourceUrl
                )
            }
        Concert(
            id = first.concertId,
            salaConcierto = sala,
            artists = artists,
            date = first.date,
            time = first.time,
            price = first.price,
            sourceUrl = first.sourceUrl,
            updatedAt = first.updatedAt
        )
    }
}
